package org.csu.chatroom.Netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.csu.chatroom.service.UserService;
import org.csu.chatroom.util.OnlineUserManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ChannelHandler.Sharable
public class SimpleServerHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    @Autowired
    private UserService userService;
    public static final String TYPE_PRIVATE_CHAT = "PRIVATE_CHAT";  //
//    private String nickname;
    private static final Map<String, String> USER_CREDENTIALS = new HashMap<>();  // 存储用户的用户名和密码
    private NettyServer nettyServer;  // 手动传入 NettyServer
    private Room currentRoom;
    @Autowired
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
        System.out.println("客户端 " + ctx.channel().remoteAddress() + " 下线");
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
//            nickname = nettyServer.getUsernameByChannel(ctx.channel());
            System.out.println("收到来自 " + ctx.channel().remoteAddress() + " 的心跳包 💓");
            return;
        }

        String currentNickname = nettyServer.getUsernameByChannel(ctx.channel());
        if (TYPE_PRIVATE_CHAT.equals(messageType)) {
            //"目标用户名|||消息内容"
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
                    privateMessage.setPayload(currentNickname + "（私聊）: " + privateContent);

                    // 发给目标用户
                    targetChannel.writeAndFlush(new TextWebSocketFrame(mapper.writeValueAsString(privateMessage)));
                }
            } else {
                System.err.println("私聊消息格式错误: " + payload);
            }
            return;
        }

        //房间逻辑
        if ("JOIN_ROOM".equals(messageType)) {
            nettyServer.joinRoom(ctx.channel(), payload, currentNickname);
            currentRoom = nettyServer.getRoomByChannel(ctx.channel());
            return;
        }

        //发送群聊消息
        currentRoom.broadcastMessage(payload,userService.getUserId(currentNickname));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

}
