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
            // 移除在线用户
            OnlineUserManager.removeUser(username);
            nettyServer.leaveRoom(ctx.channel());
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
            System.out.println("收到来自 " + ctx.channel().remoteAddress() + " 的心跳包 💓");
            return;
        }

        //获取历史消息
//        if ("GET_HISTORY".equals(messageType)) {
//            Room room = nettyServer.getCurrentRoom(ctx.channel());
//            if (room != null) {
//                room.sendHistory(ctx.channel());
//            }
//            return;
//        }

        //进入私聊房间
        if ("JOIN_PRIVATE".equals(messageType)) {
            String targetUsername = payload;
            String currentUser = nettyServer.getUsernameByChannel(ctx.channel());
            nettyServer.joinPrivateRoom(ctx.channel(), currentUser, targetUsername);
            currentRoom = nettyServer.getCurrentRoom(ctx.channel());
            return;
        }

        String currentNickname = nettyServer.getUsernameByChannel(ctx.channel());
        //群聊房间逻辑
        if ("JOIN_ROOM".equals(messageType)) {
            nettyServer.joinRoom(ctx.channel(), payload, currentNickname);
            currentRoom = nettyServer.getCurrentRoom(ctx.channel());
            return;
        }

        // 消息类型路由
        if (nettyServer.isPrivateConnection(ctx.channel())) {
            if ("PRIVATE_CHAT".equals(messageType)) {
                //"目标用户名|||消息内容"
                String[] parts = payload.split("\\|\\|\\|");
                if (parts.length == 2) {
                    String targetUsername = parts[0];
                    String privateContent = parts[1];
                    String currentUser = nettyServer.getUsernameByChannel(ctx.channel());

                    // 根据用户名找到对应的Channel
                    Channel targetChannel = nettyServer.getChannelByUsername(targetUsername,true);
                    if (targetChannel != null) {
                        if (currentRoom == null || !currentRoom.isPrivate()) {
                            // 如果不在私聊房间，自动加入
                            nettyServer.joinPrivateRoom(ctx.channel(), currentUser, targetUsername);
                            currentRoom = nettyServer.getCurrentRoom(ctx.channel());
                        }
                        if (currentRoom != null) {
                            nettyServer.markPrivateChatActive(currentUser, true);
                            //如果接收方已经在私聊窗口，直接发送消息
                            currentRoom.sendPrivateMessage(privateContent,
                                    userService.getUserId(currentUser),
                                    userService.getUserId(targetUsername),
                                    targetChannel,currentUser);
                        }
                    }
                    else{
                        nettyServer.sendPrivateNotification(currentUser, targetUsername, privateContent);
                    }
                } else {
                    System.err.println("私聊消息格式错误: " + payload);
                }
                return;
            }

            //离开私聊
            if ("LEAVE_PRIVATE".equals(messageType)) {
                String currentUser = nettyServer.getUsernameByChannel(ctx.channel());
                nettyServer.markPrivateChatActive(currentUser, false); // 标记为非活跃私聊
                return;
            }
        }
        else {
            //发送群聊消息
            String roomName = message.getHeader().getRoomName();
            if ("CHAT".equals(messageType)) {
                String currentUser = nettyServer.getUsernameByChannel(ctx.channel());
                // 如果当前在私聊房间，先退出私聊
                if (currentRoom != null && currentRoom.isPrivate()) {
                    nettyServer.leaveRoom(ctx.channel());
                    nettyServer.markPrivateChatActive(currentUser, false);
                    currentRoom = null;
                }

                // 确保加入目标群聊房间
                if (currentRoom == null || currentRoom.isPrivate()) {
                    nettyServer.joinRoom(ctx.channel(), roomName, currentUser);
                    currentRoom = nettyServer.getCurrentRoom(ctx.channel());
                }

                // 发送群聊消息
                currentRoom.broadcastMessage(payload, userService.getUserId(currentUser));        }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    public boolean isUserInPrivateChat(Channel channel,Room room) {
        if (!room.isPrivate()) return false;
        String username = nettyServer.getUsernameByChannel(channel);
        return username != null &&
                room.getPrivateUserIds().contains(userService.getUserId(username)) &&
                nettyServer.isInActivePrivateChat(username);
    }
}
