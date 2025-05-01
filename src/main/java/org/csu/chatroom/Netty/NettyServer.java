package org.csu.chatroom.Netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import jakarta.annotation.PostConstruct;
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
    private final List<Channel> channels = new ArrayList<>();  // 保存所有客户端的连接
    private final Map<String, Room> rooms = new HashMap<>();  // 存储聊天室
    private final Map<Channel, Room> userRooms = new HashMap<>();  // 存储用户所在的聊天室
    private final Map<Channel, Long> lastHeartbeat = new HashMap<>(); // 存储每个客户端的心跳时间戳
    private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>(); // 消息队列
    private final ConcurrentHashMap<Channel, String> channelUserMap = new ConcurrentHashMap<>();  // 保存 Channel 与 User 绑定关系
    @Autowired
    private ApplicationContext context;

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
        channelUserMap.put(channel, username);  // 将 username 和 channel 绑定
    }

    // 获取 Channel 对应的用户名
    public String getUsernameByChannel(Channel channel) {
        return channelUserMap.get(channel);  // 根据 channel 获取用户名
    }

    public Channel getChannelByUsername(String username) {
        for (Map.Entry<Channel, String> entry : channelUserMap.entrySet()) {
            if (entry.getValue().equals(username)) {
                return entry.getKey();
            }
        }
        return null;
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
                            pipeline.addLast(new ChunkedWriteHandler());
                            // 处理WebSocket升级握手，指定访问路径是 "/chat"
                            pipeline.addLast(new WebSocketServerProtocolHandler("/chat", null, true)); // 确保WebSocket协议处理
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

    // 添加房间管理方法
    public synchronized Room getOrCreateRoom(String roomName) {
        return rooms.computeIfAbsent(roomName, name -> {
            Room room = context.getBean(Room.class);
            room.init(name);
            return room;
        });
    }

    public void joinRoom(Channel channel, String roomName, String username) {
        Room room = getOrCreateRoom(roomName);
        room.addUser(channel);
        userRooms.put(channel, room);
        bindUserToChannel(username, channel);
        System.out.println(username + " 加入了房间: " + roomName);
        room.broadcastMessage(username + " 已加入 " + roomName + " 📢",-1);
    }

    public Room getRoomByChannel(Channel channel) {
        return userRooms.get(channel);
    }

    public void leaveRoom(Channel channel) {
        Room room = userRooms.remove(channel);
        if (room != null) {
            room.removeUser(channel);
            String username = getUsernameByChannel(channel);
            System.out.println(username + " 离开了房间: " + room.getName());
            room.broadcastMessage(username + " 已离开 " + " 📢",-1);
        }
    }
}

