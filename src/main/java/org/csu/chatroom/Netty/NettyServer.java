package org.csu.chatroom.Netty;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import jakarta.annotation.PostConstruct;
import org.csu.chatroom.service.RoomService;
import org.csu.chatroom.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Component
// 仅在 "dev" 环境下启动
public class NettyServer {
    private final int port = 8081;
    private final Map<Channel, Boolean> privateConnections = new ConcurrentHashMap<>();
    private final List<Channel> channels = new ArrayList<>();  // 保存所有客户端的连接
    private final Map<String, Room> rooms = new HashMap<>();  // 存储聊天室
    private final Map<Channel, Room> userRooms = new HashMap<>();  // 存储用户所在的聊天室
    private final Map<Channel, Long> lastHeartbeat = new HashMap<>(); // 存储每个客户端的心跳时间戳
    private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>(); // 消息队列
    @Autowired
    private ApplicationContext context;
    @Autowired
    RoomService roomService;
    @Autowired
    UserService userService;
    private final Map<String, Room> privateRoomMapping = new ConcurrentHashMap<>(); // 私聊关系映射表
    private final Set<String> activePrivateChatUsers = new HashSet<>();
    NettyServer self = this;
    private final Map<Channel, Room> groupRooms = new ConcurrentHashMap<>();
    private final Map<Channel, Room> privateRooms = new ConcurrentHashMap<>();
    private final Map<String, Channel> privateChannels = new ConcurrentHashMap<>();
    private final Map<String, Channel> groupChannels = new ConcurrentHashMap<>();

    public Room getCurrentRoom(Channel channel) {
        return isPrivateConnection(channel) ?
                privateRooms.get(channel) : groupRooms.get(channel);
    }

    public void markPrivateChatActive(String username, boolean active) {
        if (active) {
            activePrivateChatUsers.add(username);
        } else {
            activePrivateChatUsers.remove(username);
        }
    }

    // 判断连接类型
    public boolean isPrivateConnection(Channel channel) {
        return Boolean.TRUE.equals(privateConnections.get(channel));
    }

    // 设置连接类型
    public void setConnectionType(Channel channel, boolean isPrivate) {
        privateConnections.put(channel, isPrivate);
    }

    public boolean isInActivePrivateChat(String username) {
        return activePrivateChatUsers.contains(username);
    }

    @PostConstruct
    public void init() {
        new Thread(() -> {
            try {
                start();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    // 绑定用户与 Channel
    public void bindUserToChannel(String username, Channel channel) {
        // 移除旧连接
        Channel oldChannel = isPrivateConnection(channel) ?
                privateChannels.get(username) : groupChannels.get(username);
        if (oldChannel != null && oldChannel != channel) {
            oldChannel.close(); // 强制关闭旧连接
        }

        // 更新映射
        if (isPrivateConnection(channel)) {
            privateChannels.put(username, channel);
        } else {
            groupChannels.put(username, channel);
        }
    }

    // 获取 Channel 对应的用户名
    public String getUsernameByChannel(Channel channel) {
        if(isPrivateConnection(channel)){
            for (Map.Entry<String, Channel> entry : privateChannels.entrySet()) {
                if (entry.getValue() == channel) {
                    return entry.getKey();
                }
            }
        }
        else{
            for (Map.Entry<String, Channel> entry : groupChannels.entrySet()) {
                if (entry.getValue() == channel) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    public Channel getChannelByUsername(String username,boolean isPrivate) {
        return isPrivate ? privateChannels.get(username) : groupChannels.get(username);
    }

    public void start() throws InterruptedException {
        // 启动消息处理线程
        new Thread(this::processMessages).start();
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();

                            // 这里改成支持HTTP和WebSocket
                            pipeline.addLast(new HttpServerCodec());
                            pipeline.addLast(new HttpObjectAggregator(65536));
//                            pipeline.addLast(new ChunkedWriteHandler());
                            // 处理WebSocket升级握手，指定访问路径是 "/chat"
                            pipeline.addLast(new WebSocketHandshakeInterceptor(self)); // 添加拦截器
//                            pipeline.addLast(new WebSocketServerProtocolHandler("/chat",  null,true)); // 确保WebSocket协议处理
                            // 修改initChannel方法中的协议处理器配置
                            pipeline.addLast(new WebSocketServerProtocolHandler(
                                    "/chat",  // 路径基础
                                    null,     // 子协议
                                    true,     // 允许扩展
                                    65536,    // 最大帧大小
                                    false,    // 不检查起始路径
                                    true      // 允许查询参数
                            ));
                            pipeline.addLast(context.getBean(SimpleServerHandler.class));  // 这里传递
                        }
                    });

            ChannelFuture f = b.bind(port).sync();
            System.out.println("Netty 服务启动在端口 " + port);

            // 启动心跳检查线程
            new HeartbeatChecker(this).start();

            f.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    // 检查心跳
    public void checkHeartbeat() {
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<Channel, Long>> iterator = lastHeartbeat.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Channel, Long> entry = iterator.next();
            long lastTime = entry.getValue();

            // 超过 60 秒未收到心跳包，认为连接断开
            if (currentTime - lastTime > 60000) {
                Channel channel = entry.getKey();
                System.out.println("客户端 " + channel.remoteAddress() + " 超过 60 秒未收到心跳包，断开连接");
                channel.close();
                iterator.remove();
            }
        }
    }

    // 更新心跳时间戳
    public void updateHeartbeat(Channel channel) {
        lastHeartbeat.put(channel, System.currentTimeMillis());
    }


    public void addChannel(Channel channel) {
        channels.add(channel);
    }

    public void removeChannel(Channel channel) {
        channels.remove(channel);
    }

    // 处理消息的线程
    private void processMessages() {
        while (true) {
            try {
                // 从队列中取出消息并处理
                String message = messageQueue.take();
                // 处理消息逻辑
                System.out.println("处理消息: " + message);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // 添加消息到队列
    public void addMessageToQueue(String message) {
        messageQueue.add(message);
    }

    // 管理群聊房间
    public synchronized Room getOrCreateRoom(String roomName) {
        return rooms.computeIfAbsent(roomName, name -> {
            Room room = context.getBean(Room.class);
            room.init(name);
            return room;
        });
    }

    public void joinRoom(Channel channel, String roomName, String username) {
        Room room = getOrCreateRoom(roomName);
        groupRooms.put(channel, room);
        room.addUser(channel);
        bindUserToChannel(username, channel);
        System.out.println(username + " 加入了房间: " + roomName);
        room.sendHistory(channel);
        room.broadcastMessage(username + " 已加入 " + roomName + " 📢",-1);
    }

    public Room getRoomByChannel(Channel channel) {
        return userRooms.get(channel);
    }

    //    public void leaveRoom(Channel channel) {
//        Room room;
//        if (isPrivateConnection(channel)) {
//            room=privateRooms.remove(channel);
//        } else {
//            room=groupRooms.remove(channel);
//        }
//        if (room != null) {
//            room.removeUser(channel);
//            String username = getUsernameByChannel(channel);
//            room.broadcastMessage(username + " 已离开 " + " 📢",-1);
//        }
//    }
    public void leaveRoom(Channel channel) {
        Room room;
        String username = getUsernameByChannel(channel);

        if (isPrivateConnection(channel)) {
            // 私聊房间处理
            room = privateRooms.remove(channel);
            if (room != null) {
                room.removeUser(channel);
                // 不要广播消息，因为这是私聊
                System.out.println(username + " 离开了私聊");

                // 从私聊连接映射中移除
                if (username != null) {
                    privateChannels.remove(username);
                }
            }
        } else {
            // 群聊房间处理
            room = groupRooms.remove(channel);
            if (room != null) {
                room.removeUser(channel);
                // 只在群聊中广播离开消息
                room.broadcastMessage(username + " 已离开 " + room.getName() + " 📢", -1);

                // 从群聊连接映射中移除
                if (username != null) {
                    groupChannels.remove(username);
                }
            }
        }

        // 检查用户是否还有其他连接
        if (username != null) {
            boolean hasOtherConnections =
                    (privateChannels.containsKey(username) || groupChannels.containsKey(username));

            if (!hasOtherConnections) {
                System.out.println("用户 " + username + " 的所有连接已断开，从在线列表中移除");
                // 这里应该有通知其他用户更新在线列表的逻辑
            } else {
                System.out.println("用户 " + username + " 仍有其他连接活跃，保持在线状态");
            }
        }
    }


    // 获取或创建私聊房间
    public synchronized Room getOrCreatePrivateRoom(String currentUser, String targetUser) {
        String roomKey = generateRelationKey(currentUser, targetUser);
        return privateRoomMapping.computeIfAbsent(roomKey, k -> {
            Room room = context.getBean(Room.class);
            room.createPrivateRoom(currentUser, targetUser);
            return room;
        });
    }

    // 生成用户关系键（确保顺序无关）
    private String generateRelationKey(String user1, String user2) {
        return user1.compareTo(user2) < 0 ?
                user1 + "|" + user2 :
                user2 + "|" + user1;
    }

    public void joinPrivateRoom(Channel channel, String currentUser, String targetUser) {
        leaveRoom(channel);
        Room privateRoom = getOrCreatePrivateRoom(currentUser, targetUser);
        privateRooms.put(channel, privateRoom);
        bindUserToChannel(currentUser, channel);
        privateRoom.addUser(channel);
        markPrivateChatActive(currentUser, true);
        privateRoom.sendHistory(channel);
        System.out.println(currentUser + " 加入了与 " + targetUser + " 的私聊房间");
    }

    public void sendPrivateNotification(String sender, String receiver, String content) {
        Channel receiverChannel = getChannelByUsername(receiver,false);
        org.csu.chatroom.entity.Message message = new org.csu.chatroom.entity.Message();
        message.setSender(userService.getUserId(sender));
        message.setReceiver(userService.getUserId(receiver));
        message.setContent(content);
        message.setCreateTime(new Date());

        roomService.saveMessage(message);
        if (receiverChannel != null) {
            Message notification = new Message();
            Message.MessageHeader header = new Message.MessageHeader();
            header.setCreateTime(new Date());
            header.setMessageType("PRIVATE_NOTIFICATION");
            header.setMessageId(System.currentTimeMillis() + "");
            notification.setHeader(header);
            notification.setPayload(sender + "给你发了一条私信");

            try {
                receiverChannel.writeAndFlush(new TextWebSocketFrame(new ObjectMapper().writeValueAsString(notification)));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }
    /**
     * 发送群聊文件消息
     * @param roomId 房间ID
     * @param message 文件消息
     */
    /**
     * 发送群聊文件消息
     */
    public void sendGroupFileMessage(Integer roomId, Message message) {
        if (roomId == null) {
            System.err.println("发送群组文件消息失败：roomId为空");
            return;
        }

        try {
            // 获取房间对象
            org.csu.chatroom.entity.Room dbRoom = roomService.getRoomById(roomId);
            if (dbRoom == null) {
                System.err.println("发送群组文件消息失败：找不到房间ID " + roomId);
                return;
            }

            String roomName = dbRoom.getName();
            Room room = getOrCreateRoom(roomName);

            // 记录调试信息
            System.out.println("准备发送文件消息到房间：" + roomName +
                    "，发送者：" + message.getHeader().getSender() +
                    "，文件ID：" + message.getHeader().getMessageId());

            // 使用Room对象的广播文件方法
            room.broadcastFileMessage(message);

            System.out.println("文件消息发送完成：" + message.getHeader().getMessageId());
        } catch (Exception e) {
            System.err.println("发送群组文件消息失败：" + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * 获取组房间
     * @param roomId 房间ID
     * @return 房间对象
     */
    public Room getGroupRoomById(Integer roomId) {
        if (roomId == null) return null;

        org.csu.chatroom.entity.Room dbRoom = roomService.getRoomById(roomId);
        if (dbRoom == null) return null;

        return getOrCreateRoom(dbRoom.getName());
    }

}

