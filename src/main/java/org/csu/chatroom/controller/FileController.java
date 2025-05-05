package org.csu.chatroom.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.csu.chatroom.Netty.Message;
import org.csu.chatroom.Netty.NettyServer;
import org.csu.chatroom.entity.FileTransfer;
import org.csu.chatroom.entity.User;
import org.csu.chatroom.service.FileTransferService;
import org.csu.chatroom.service.RoomService;
import org.csu.chatroom.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/files")
public class FileController {

    @Value("${file.upload-dir:/tmp/uploads}")
    private String uploadDir;

    @Autowired
    private FileTransferService fileTransferService;

    @Autowired
    private UserService userService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private NettyServer nettyServer;

    @PostMapping("/upload")
    @ResponseBody
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file,
                                        @RequestParam("sender") String senderUsername,
                                        @RequestParam("roomId") Integer roomId,
                                        HttpSession session) {

        // 用户认证验证
        User currentUser = (User) session.getAttribute("user");
        if (currentUser == null || !currentUser.getUsername().equals(senderUsername)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        // 检查文件类型和大小
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }
        // 100MB限制
        if (file.getSize() > 100 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("error", "File size exceeds limit (Max 100MB)"));
        }

        // 检查是否提供了roomId
        if (roomId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "RoomId must be provided"));
        }

        try {
            // 验证房间存在
            if (nettyServer.getGroupRoomById(roomId) == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Group room with ID '" + roomId + "' not found."));
            }

            // 文件存储逻辑
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            String originalFilename = file.getOriginalFilename();
            String cleanFilename = originalFilename != null ?
                    originalFilename.replaceAll("[^a-zA-Z0-9.\\-_]", "_") : "unknown_file";
            String fileId = UUID.randomUUID().toString() + "_" + cleanFilename;
            Path filePath = uploadPath.resolve(fileId);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // 数据库记录
            FileTransfer fileTransfer = new FileTransfer();
            fileTransfer.setFileId(fileId);
            fileTransfer.setFileName(originalFilename);
            fileTransfer.setFileSize(file.getSize());
            fileTransfer.setSender(currentUser.getUsername());
            fileTransfer.setTransferTime(new Date());
            fileTransfer.setStatus("completed");
            fileTransfer.setRoomId(roomId);

            fileTransferService.saveFileTransfer(fileTransfer);
            System.out.println("File transfer record saved to DB for fileId: " + fileId);

            // WebSocket通知 - 构建 Message 对象并委托给 NettyServer
            Message fileMessage = buildFileMessage(currentUser, fileTransfer, roomId);
            sendFileNotification(fileMessage, roomId, currentUser.getUsername());

            // 返回前端需要的完整信息
            return ResponseEntity.ok().body(Map.of(
                    "message", "File uploaded successfully",
                    "fileId", fileId,
                    "fileName", originalFilename,
                    "size", file.getSize(),
                    "url", "/files/download/" + fileId,
                    "downloadUrl", "/files/download/" + fileId
            ));

        } catch (IOException e) {
            System.err.println("File upload failed for sender: " + senderUsername + ", error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "File upload failed: " + e.getMessage()));
        } catch (Exception e) {
            System.err.println("Server error during file upload for sender: " + senderUsername + ", error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Server error during file upload."));
        }
    }

    private Message buildFileMessage(User sender, FileTransfer fileTransfer, Integer roomId) {
        Message.MessageHeader header = new Message.MessageHeader();
        header.setMessageType("FILE"); // 消息类型为FILE
        header.setSender(sender.getUsername());
        header.setCreateTime(new Date());
        header.setMessageId(fileTransfer.getFileId());

        // 构建文件信息JSON，确保与前端解析格式一致
        String payload = String.format(
                "{\"fileId\":\"%s\",\"fileName\":\"%s\",\"size\":%d,\"url\":\"/files/download/%s\"}",
                fileTransfer.getFileId(),
                fileTransfer.getFileName(),
                fileTransfer.getFileSize(),
                fileTransfer.getFileId()
        );

        // 获取房间名称
        String roomName = "未知房间";
        org.csu.chatroom.entity.Room dbRoom = roomService.getRoomById(roomId);
        if (dbRoom != null) {
            roomName = dbRoom.getName();
        }
        header.setRoomName(roomName);

        header.setMessageLength(payload.length());
        header.setChecksum(String.valueOf(payload.hashCode()));

        Message message = new Message();
        message.setHeader(header);
        message.setPayload(payload);
        return message;
    }

    private void sendFileNotification(Message message, Integer roomId, String senderUsername) {
        try {
            // NettyServer will handle broadcasting for group file messages
            nettyServer.sendGroupFileMessage(roomId, message);
        } catch (Exception e) {
            System.err.println("Failed to delegate file notification sending to NettyServer: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String fileId,
            HttpSession session,
            HttpServletRequest request) {

        // 权限验证
        User currentUser = (User) session.getAttribute("user");
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }

        FileTransfer fileTransfer = fileTransferService.getFileTransferByFileId(fileId);
        System.out.println("Download requested for fileId: " + fileId);
        if (fileTransfer == null) {
            System.out.println("Error: FileTransfer record not found for fileId: " + fileId);
            return ResponseEntity.notFound().build();
        }

        System.out.println("Retrieved FileTransfer for fileId " + fileId + ":");
        System.out.println("  fileName: " + fileTransfer.getFileName());
        System.out.println("  fileSize (from DB): " + fileTransfer.getFileSize());
        System.out.println("  sender: " + fileTransfer.getSender());
        System.out.println("  roomId: " + fileTransfer.getRoomId());

        // 简化访问控制 - 任何已登录用户可以访问群聊文件
        // 这里不再调用isUserInRoom，直接验证用户已登录即可
        // 如果有需要，可以添加额外的访问控制检查

        try {
            Path filePath = Paths.get(uploadDir).resolve(fileId);
            System.out.println("Checking file existence at path: " + filePath);
            boolean fileExists = Files.exists(filePath);
            System.out.println("File exists on disk: " + fileExists);
            if (!Files.exists(filePath)) {
                System.out.println("Error: File not found on disk at path: " + filePath);
                return ResponseEntity.notFound().build();
            }

            long actualFileSizeOnDisk = Files.size(filePath);
            System.out.println("Actual file size on disk: " + actualFileSizeOnDisk + " bytes");

            // 获取文件类型并处理 null
            String contentType = Files.probeContentType(filePath);
            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM; // 默认二进制流
            if (contentType != null) {
                mediaType = MediaType.parseMediaType(contentType);
            }
            System.out.println("Determined content type: " + mediaType);

            InputStreamResource resource = new InputStreamResource(new FileInputStream(filePath.toFile()));
            System.out.println("Created InputStreamResource for file: " + fileId);

            // 测试流可读性
            try (InputStream testStream = new FileInputStream(filePath.toFile())) {
                byte[] buffer = new byte[1024];
                int bytesRead = testStream.read(buffer);
                System.out.println("Stream test read bytes: " + bytesRead);
            }

            // 文件名编码处理
            String originalFileName = fileTransfer.getFileName();
            String displayFileName = (originalFileName != null && !originalFileName.trim().isEmpty()) ?
                    originalFileName : "downloaded_file";
            String encodedFileName = URLEncoder.encode(displayFileName, StandardCharsets.UTF_8.toString())
                    .replace("+", "%20");

            // 检测浏览器类型并适配Content-Disposition头
            String userAgent = request.getHeader("User-Agent");
            String contentDisposition;

            if (userAgent != null && userAgent.contains("MSIE")) {
                // IE浏览器
                contentDisposition = "attachment; filename=" + encodedFileName;
            } else if (userAgent != null && userAgent.contains("Firefox")) {
                // Firefox浏览器
                contentDisposition = "attachment; filename*=UTF-8''" + encodedFileName;
            } else {
                // Chrome, Safari, Edge等
                contentDisposition = "attachment; filename=\"" + encodedFileName + "\"";
            }

            // 确定Content-Length
            Long fileSize = fileTransfer.getFileSize();
            long contentLength = (fileSize != null && fileSize > 0) ? fileSize : actualFileSizeOnDisk;

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .contentType(mediaType)
                    .contentLength(contentLength)
                    .body(resource);

        } catch (IOException e) {
            System.err.println("File download failed for fileId: " + fileId + ", user: " + currentUser.getUsername() + ", error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            System.err.println("Server error during file download for fileId: " + fileId + ", user: " + currentUser.getUsername() + ", error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}
