package org.csu.chatroom.Netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.csu.chatroom.service.RoomService;
import org.csu.chatroom.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

@Component
@Scope("prototype")
public class Room {
    @Autowired
    private RoomService roomService;

    @Autowired
    private UserService userService;

    private String name;
    private int roomId;
    private List<Channel> users = new ArrayList<>();  // 存储聊天室内的用户
    private LinkedList<org.csu.chatroom.entity.Message> messages = new LinkedList<>();  // 存储消息记录（历史消息）
    private static final int MAX_HISTORY_SIZE = 100;  // 最大历史记录条数

    public void init(String name) {
        this.name = name;
        this.roomId=roomService.getRoomByName(name).getId();
        loadHistoryFromDatabase();
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

    public void broadcastMessage(String content, int sender) {
        if (messages.size() >= MAX_HISTORY_SIZE) {
            messages.poll();  // 删除最旧的消息，确保历史记录不会超出最大限制
        }

        if(sender>=0){
            //保存到数据库
            org.csu.chatroom.entity.Message message = new org.csu.chatroom.entity.Message();
            message.setRoomId(roomId);
            message.setSender(sender);
            message.setContent(content);
            message.setCreateTime(new Date());
            roomService.saveMessage(message);
            content=userService.getUserName(sender) + ": " + content + " 💬";
            System.out.println(content);
            messages.add(message);//保存新消息到历史记录
        }

        Message.MessageHeader header = new Message.MessageHeader(
                "CHAT",  // 消息类型
                String.valueOf(System.currentTimeMillis()),  // 消息 ID
                content.length(),  // 消息长度
                String.valueOf(content.hashCode())  // 校验和
        );

        Message msg = new Message(header, content);
        String jsonMessage = convertToJson(msg);

        if (jsonMessage != null) {

            for (Channel user : users) {
                System.out.println("count-----------------");

                user.writeAndFlush(new TextWebSocketFrame(jsonMessage));
            }
        }
    }

    private void loadHistoryFromDatabase() {
        List<org.csu.chatroom.entity.Message> history = roomService.getRecentMessages(roomId, MAX_HISTORY_SIZE);
        messages.clear();
        messages.addAll(history);
    }

    private void sendHistory(Channel user) {
        for (org.csu.chatroom.entity.Message message : messages) {
            Message.MessageHeader header = new Message.MessageHeader(
                    "HISTORY",  // 消息类型
                    String.valueOf(System.currentTimeMillis()),
                    message.getContent().length(),
                    String.valueOf(message.getContent().hashCode())
            );

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
