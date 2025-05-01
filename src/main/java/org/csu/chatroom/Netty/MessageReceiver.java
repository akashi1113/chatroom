package org.csu.chatroom.Netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.CharsetUtil;

import java.io.IOException;

public class MessageReceiver {

    public void receiveMessage(ChannelHandlerContext ctx, Object msg) {
        ByteBuf byteBuf = (ByteBuf) msg;
        String jsonMessage = byteBuf.toString(CharsetUtil.UTF_8);

        // 解析 JSON
        ObjectMapper mapper = new ObjectMapper();
        try {
            Message message = mapper.readValue(jsonMessage, Message.class);
            handleReceivedMessage(ctx, message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleReceivedMessage(ChannelHandlerContext ctx, Message message) {
        String messageType = message.getHeader().getMessageType();
        String payload = message.getPayload();

        if ("CHAT".equals(messageType)) {
            // 处理聊天消息
            System.out.println("收到聊天消息：" + payload);
        } else if ("HEARTBEAT".equals(messageType)) {
            // 处理心跳消息
            System.out.println("收到心跳包");
        } else {
            System.out.println("收到其他类型的消息");
        }
    }
}

