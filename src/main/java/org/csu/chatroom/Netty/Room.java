package org.csu.chatroom.Netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class Room {
    private String name;
    private List<Channel> users = new ArrayList<>();  // 存储聊天室内的用户
    private LinkedList<String> messages = new LinkedList<>();  // 存储消息记录（历史消息）
    private static final int MAX_HISTORY_SIZE = 100;  // 最大历史记录条数

    public Room(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void addUser(Channel user) {
        users.add(user);
        sendHistory(user);  // 用户加入时，发送历史消息
    }

    public void removeUser(Channel user) {
        users.remove(user);
    }

    public void broadcastMessage(String message) {
        if (messages.size() >= MAX_HISTORY_SIZE) {
            messages.poll();  // 删除最旧的消息，确保历史记录不会超出最大限制
        }
        messages.add(message);  // 保存新消息到历史记录

        Message.MessageHeader header = new Message.MessageHeader(
                "CHAT",  // 消息类型
                String.valueOf(System.currentTimeMillis()),  // 消息 ID
                message.length(),  // 消息长度
                String.valueOf(message.hashCode())  // 校验和
        );

        Message msg = new Message(header, message);
        String jsonMessage = convertToJson(msg);

        if (jsonMessage != null) {

            for (Channel user : users) {
                System.out.println("count-----------------");

                user.writeAndFlush(new TextWebSocketFrame(jsonMessage));
            }
        }
    }

    private void sendHistory(Channel user) {
        for (String message : messages) {
            Message.MessageHeader header = new Message.MessageHeader(
                    "HISTORY",  // 消息类型
                    String.valueOf(System.currentTimeMillis()),
                    message.length(),
                    String.valueOf(message.hashCode())
            );

            Message msg = new Message(header, message);
            String jsonMessage = convertToJson(msg);

            if (jsonMessage != null) {
                System.out.println("发送历史消息：" + jsonMessage);
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
