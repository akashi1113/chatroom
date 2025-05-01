package org.csu.chatroom.Netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.CharsetUtil;

import org.csu.chatroom.util.OnlineUserManager;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimpleServerHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    public static final String TYPE_PRIVATE_CHAT = "PRIVATE_CHAT";  //



    private String nickname;
    private static final Map<String, String> USER_CREDENTIALS = new HashMap<>();  // 存储用户的用户名和密码
    private NettyServer nettyServer;  // 手动传入 NettyServer
    private Room currentRoom;
    public SimpleServerHandler(NettyServer nettyServer) {
        this.nettyServer = nettyServer;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // 当客户端连接时，添加到在线用户列表
        nettyServer.addChannel(ctx.channel());
        System.out.println("客户端 " + ctx.channel().remoteAddress() + " 上线");

    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {


        String username = nettyServer.getUsernameByChannel(ctx.channel());  // 获取该用户的用户名
        if (username != null) {
            OnlineUserManager.removeUser(username);  // 移除在线用户
            System.out.println("客户端 " + ctx.channel().remoteAddress() + " 下线，移除在线用户：" + username);
        }
        // 当客户端断开时，从在线用户列表中移除
        nettyServer.removeChannel(ctx.channel());
        System.out.println("客户端 " + ctx.channel().remoteAddress() + " 下线");
        // 当客户端断开时，从在线用户列表中移除


//        currentRoom.removeUser(ctx.channel());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
        String jsonMessage = frame.text();  // 直接拿到WebSocket传来的文本内容
        System.out.println("收到客户端消息: " + jsonMessage);

        ObjectMapper mapper = new ObjectMapper();
        Message message = mapper.readValue(jsonMessage, Message.class);

        String messageType = message.getHeader().getMessageType();
        String payload = message.getPayload();

        // 处理心跳
        if ("HEARTBEAT".equals(messageType)) {
            nettyServer.updateHeartbeat(ctx.channel());
            System.out.println("收到来自 " + ctx.channel().remoteAddress() + " 的心跳包 💓");
            return;
        }

        if ("NICKNAME".equals(messageType)) {
            nettyServer.bindUserToChannel(payload,ctx.channel());
            nickname = nettyServer.getUsernameByChannel(ctx.channel());
            System.out.println("收到来自 " + ctx.channel().remoteAddress() + " 的心跳包 💓");
            return;
        }


        // 【新增处理：私聊消息】
        if (TYPE_PRIVATE_CHAT.equals(messageType)) {
            // payload里面需要包含【目标用户名】和【私聊内容】
            // 我们简单一点，payload规定格式: "目标用户名|||消息内容"
            String[] parts = payload.split("\\|\\|\\|");
            if (parts.length == 2) {
                String targetUsername = parts[0];
                String privateContent = parts[1];

                // 根据用户名找到对应的Channel
                Channel targetChannel = nettyServer.getChannelByUsername(targetUsername);
                if (targetChannel != null) {
                    // 组装私聊的消息
                    Message privateMessage = new Message();
                    Message.MessageHeader header = new Message.MessageHeader();
                    header.setMessageType(TYPE_PRIVATE_CHAT);
                    header.setMessageId(System.currentTimeMillis() + "");
                    header.setMessageLength(privateContent.length());
                    header.setChecksum(String.valueOf(privateContent.hashCode()));
                    privateMessage.setHeader(header);
                    privateMessage.setPayload(nickname + "（私聊）: " + privateContent);

                    // 发给目标用户
                    targetChannel.writeAndFlush(new TextWebSocketFrame(new ObjectMapper().writeValueAsString(privateMessage)));
                }
            } else {
                System.err.println("私聊消息格式错误: " + payload);
            }
            return;  // 处理完了直接return
        }
        // 处理昵称与房间逻辑
       if (currentRoom == null) {
            Room room = nettyServer.getRoom(payload);
            if (room == null) {
                room = nettyServer.createRoom(payload);
            }
            currentRoom = room;
            room.addUser(ctx.channel());
            room.broadcastMessage(nickname + " 已加入 " + payload + " 📢");
        } else {
            System.out.println(currentRoom);
            currentRoom.broadcastMessage(nickname + ": " + payload + " 💬");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

//    @Override
//    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
//        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
//            WebSocketServerProtocolHandler.HandshakeComplete handshake = (WebSocketServerProtocolHandler.HandshakeComplete) evt;
//
//            String requestUri = handshake.requestUri();  // 获取客户端连接时的URI
//            System.out.println("握手完成，连接的URI：" + requestUri);
//
//            QueryStringDecoder decoder = new QueryStringDecoder(requestUri);
//            Map<String, List<String>> parameters = decoder.parameters();
//
//            if (parameters.containsKey("username")) {
//                String username = parameters.get("username").get(0);
//                System.out.println("绑定用户：" + username);
//
//                nettyServer.bindUserToChannel(username, ctx.channel());  // 将用户名和Channel绑定
//            } else {
//                System.err.println("连接未提供用户名，拒绝连接！");
//                ctx.close();  // 如果没有用户名，关闭连接
//                return;  // 如果没有用户名，跳过后续逻辑
//            }
//        } else {
//            super.userEventTriggered(ctx, evt);  // 传递其他事件
//        }
//    }
}
