package org.csu.chatroom.service;

import org.csu.chatroom.entity.FileTransfer;
import org.csu.chatroom.persistence.FileTransferMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class FileTransferService {

    @Autowired
    private FileTransferMapper fileTransferMapper;

    @Transactional
    public void saveFileTransfer(FileTransfer fileTransfer) {
        // fileTransfer 应该已经在 Controller 中设置了 sender, fileName, fileSize 等基础信息

        if (fileTransfer.getTransferTime() == null) {
            fileTransfer.setTransferTime(new Date());
        }
        if (fileTransfer.getFileId() == null) {
            fileTransfer.setFileId(UUID.randomUUID().toString()); // 生成唯一的fileId (虽然Controller中已经生成，这里做个兜底)
        }
        // 设置默认的 status
        if (fileTransfer.getStatus() == null || fileTransfer.getStatus().isEmpty()) {
            fileTransfer.setStatus("completed"); // 假设上传成功即为完成
        }

        // 验证roomId必须有值
        if (fileTransfer.getRoomId() == null) {
            System.err.println("Error: FileTransfer object requires roomId to be set.");
            throw new IllegalArgumentException("RoomId cannot be null for file transfer");
        }

        fileTransferMapper.insertFileTransfer(fileTransfer);
    }

    public FileTransfer getFileTransferByFileId(String fileId) {
        return fileTransferMapper.getFileTransferByFileId(fileId);
    }

    // 获取房间文件列表的方法
    public List<FileTransfer> getFilesByRoomId(Integer roomId) {
        return fileTransferMapper.getFilesByRoomId(roomId);
    }
}
