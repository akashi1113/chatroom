package org.csu.chatroom.Netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.csu.chatroom.service.RoomService;
import org.csu.chatroom.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Scope("prototype")
public class Room {
    @Autowired
    private RoomService roomService;

    @Autowired
    private UserService userService;
    private boolean isPrivate = false;
    private Set<Integer> privateUserIds = new HashSet<>();
    private String name;
    private int roomId;
    private List<Channel> users = new ArrayList<>();  // 存储聊天室内的用户
    private LinkedList<org.csu.chatroom.entity.Message> messages = new LinkedList<>();  // 存储消息记录（历史消息）
    private static final int MAX_HISTORY_SIZE = 100;  // 最大历史记录条数

    public boolean isPrivate() {
        return isPrivate;
    }

    public Set<Integer> getPrivateUserIds() {
        return privateUserIds;
    }

    public void init(String name) {
        this.name = name;
        this.roomId=roomService.getRoomByName(name).getId();
        loadHistoryFromDatabase();
    }

    public void createPrivateRoom(String user1, String user2) {
        this.isPrivate = true;
        this.name = "私聊_" + user1 + "_" + user2;
        this.privateUserIds.add(userService.getUserId(user1));
        this.privateUserIds.add(userService.getUserId(user2));
        loadHistoryFromDatabase();
    }

    public String getName() {
        return name;
    }

    public void addUser(Channel user) {
        users.add(user);
    }

    public void removeUser(Channel user) {
        users.remove(user);
    }

    //群聊消息专用
    public void broadcastMessage(String content, int senderId) {
        if (isPrivate) {
            System.err.println("错误：不应在私聊房间使用 broadcastGroupMessage");
            return;
        }

        org.csu.chatroom.entity.Message message = new org.csu.chatroom.entity.Message();
        message.setSender(senderId);
        message.setRoomId(roomId);
        message.setContent(content);
        message.setCreateTime(new Date());

        if(senderId>0) roomService.saveMessage(message);
        if (messages.size() >= MAX_HISTORY_SIZE) messages.poll();
        messages.add(message);

        String displayContent=content;
        if(senderId>0) displayContent = userService.getUserName(senderId) + ": " + content + " 💬";
        Message.MessageHeader header = new Message.MessageHeader(
                "CHAT",
                String.valueOf(System.currentTimeMillis()),
                displayContent.length(),
                String.valueOf(displayContent.hashCode())
        );
        header.setSender(userService.getUserName(senderId));
        Message msg = new Message(header, displayContent);
        String json = convertToJson(msg);

        if (json != null) {
            for (Channel user : users) {
                user.writeAndFlush(new TextWebSocketFrame(json));
            }
        }
    }

    //私聊消息专用
    public void sendPrivateMessage(String content, int senderId, int receiverId, Channel receiverChannel, String senderName) {
        if (!isPrivate) {
            System.err.println("错误：不应在非私聊房间使用 sendPrivateMessage");
            return;
        }

        org.csu.chatroom.entity.Message message = new org.csu.chatroom.entity.Message();
        message.setSender(senderId);
        message.setReceiver(receiverId);
        message.setContent(content);
        message.setCreateTime(new Date());

        roomService.saveMessage(message);
        messages.add(message);

        Message.MessageHeader header = new Message.MessageHeader();
        header.setMessageType("PRIVATE_CHAT");
        header.setMessageId(System.currentTimeMillis() + "");
        header.setMessageLength(content.length());
        header.setChecksum(String.valueOf(content.hashCode()));
        header.setSender(senderName);

        Message msg = new Message(header, content);
        String json = convertToJson(msg);
        if (json != null) {
            receiverChannel.writeAndFlush(new TextWebSocketFrame(json));
        }
    }

    private void loadHistoryFromDatabase() {
        if (isPrivate) {
            // 私聊房间加载双方消息
            List<Integer> userIds = new ArrayList<>(privateUserIds);
            if (userIds.size() == 2) {
                List<org.csu.chatroom.entity.Message> history =
                        roomService.getPrivateMessages(userIds.get(0), userIds.get(1), MAX_HISTORY_SIZE);
                messages.clear();
                messages.addAll(history);
            }
        } else {
            // 群聊房间加载普通消息
            List<org.csu.chatroom.entity.Message> history =
                    roomService.getRecentMessages(roomId, MAX_HISTORY_SIZE);
            messages.clear();
            messages.addAll(history);
        }
    }

    public void sendHistory(Channel user) {
        loadHistoryFromDatabase();
        for (org.csu.chatroom.entity.Message message : messages) {
            Message.MessageHeader header = new Message.MessageHeader(
                    "HISTORY",  // 消息类型
                    String.valueOf(System.currentTimeMillis()),
                    message.getContent().length(),
                    String.valueOf(message.getContent().hashCode())
            );
            header.setSender(userService.getUserName(message.getSender()));

            Message msg = new Message(header, message.getContent());
            String jsonMessage = convertToJson(msg);

            if (jsonMessage != null) {
                System.out.println("发送历史消息：" + jsonMessage);
                user.writeAndFlush(new TextWebSocketFrame(jsonMessage));
            } else {
                System.err.println("错误：历史消息转换为 JSON 时失败！");
            }
        }
    }

    //发送指定时间范围内的历史消息
    public void sendHistoryByTimeRange(Channel user, Date startTime, Date endTime) {
        List<org.csu.chatroom.entity.Message> history = roomService.getMessagesByTimeRange(roomId, startTime, endTime);
        for (org.csu.chatroom.entity.Message message : history) {
            Message.MessageHeader header = new Message.MessageHeader(
                    "HISTORY_RANGE",  // 新增消息类型，表示时间段历史记录
                    String.valueOf(message.getCreateTime().getTime()),  // 使用消息实际时间作为ID
                    message.getContent().length(),
                    String.valueOf(message.getContent().hashCode())
            );

            Message msg = new Message(header, message.getContent());
            String jsonMessage = convertToJson(msg);

            if (jsonMessage != null) {
                System.out.println("发送指定时间的历史消息：" + jsonMessage);
                user.writeAndFlush(new TextWebSocketFrame(jsonMessage));
            } else {
                System.err.println("错误：历史消息转换为 JSON 时失败！");
            }
        }
    }

    // 将消息转换为 JSON 字符串
    private String convertToJson(Message message) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(message);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
