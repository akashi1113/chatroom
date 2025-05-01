package org.csu.chatroom.Netty;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.util.CharsetUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NettyClient {

    private final String host;
    private final int port;
    private final Map<Channel, Long> lastHeartbeat = new HashMap<>(); // 存储每个客户端的心跳时间戳


    public NettyClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public  void connect() throws InterruptedException
    {
        Scanner scanner = new Scanner(System.in);
        System.out.print("请输入你的昵称：");
        String nickname = scanner.nextLine();  // 用户输入昵称
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            // 解码器：根据包头中的长度字段来处理粘包和半包
                            // 解码器：根据包头中的消息长度字段来处理粘包和半包
                            pipeline.addLast(new LengthFieldBasedFrameDecoder(
                                    8192,  // 最大帧大小
                                    0,     // 长度字段的偏移量，从包头的第 0 字节开始
                                    4,     // 长度字段的长度（消息体长度为 4 字节）
                                    0,     // 长度字段的增量，通常为 0
                                    4));   // 长度字段的数据起始位置，从包头的第 4 字节开始

                            pipeline.addLast(new LengthFieldPrepender(4));  // 负责将消息长度写入包头
                            pipeline.addLast(new SimpleClientHandler(nickname));
                        }
                    });

            ChannelFuture f = b.connect(host, port).sync();
            System.out.println("客户端连接成功 ✅");
            Channel channel = f.channel();
            // 创建消息包头
            Message.MessageHeader header = new Message.MessageHeader(
                    "LOGIN",  // 消息类型：登录
                    "001",    // 消息 ID
                    nickname.length(),  // 消息长度（昵称长度）
                    String.valueOf(nickname.hashCode()) // 校验和
            );

            // 创建消息载荷
            String payload = nickname;

            // 创建完整消息
            Message message = new Message(header, payload);
            // 将消息转为 JSON 字符串
            String jsonMessage = convertToJson(message);
            // 发送消息
            channel.writeAndFlush(Unpooled.copiedBuffer(jsonMessage, CharsetUtil.UTF_8));
            // 模拟发送消息（你也可以替换成用户输入）
            // 创建聊天室或加入聊天室
            System.out.println("请输入聊天室名称（或输入 'exit' 退出）：");
            String roomName = scanner.nextLine();
            // 创建房间消息
            header = new Message.MessageHeader(
                    "JOIN_ROOM",  // 消息类型：加入房间
                    "002",        // 消息 ID
                    roomName.length(),  // 消息长度
                    String.valueOf(roomName.hashCode()) // 校验和
            );

            payload = roomName;
            message = new Message(header, payload);
            jsonMessage = convertToJson(message);

            channel.writeAndFlush(Unpooled.copiedBuffer(jsonMessage, CharsetUtil.UTF_8));



            Scanner scanner1 = new Scanner(System.in);

            // 心跳机制
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(() -> {
                // 创建心跳包消息
                Message.MessageHeader header1 = new Message.MessageHeader(
                        "HEARTBEAT",  // 消息类型：心跳包
                        "003",        // 消息 ID
                        "heartbeat".length(),  // 消息长度
                        String.valueOf("heartbeat".hashCode()) // 校验和
                );

                String payload1 = "heartbeat";
                Message message1 = new Message(header1, payload1);
                String jsonMessage1 = convertToJson(message1);
                channel.writeAndFlush(Unpooled.copiedBuffer(jsonMessage1, CharsetUtil.UTF_8));
            }, 0, 30, TimeUnit.SECONDS);
            while (scanner1.hasNext()) {
                String msg = scanner1.nextLine();
                header = new Message.MessageHeader(
                        "CHAT",  // 消息类型：聊天
                        "004",   // 消息 ID
                        msg.length(),  // 消息长度
                        String.valueOf(msg.hashCode()) // 校验和
                );

                message = new Message(header, msg);
                jsonMessage = convertToJson(message);
                channel.writeAndFlush(Unpooled.copiedBuffer(jsonMessage, CharsetUtil.UTF_8));
            }

            f.channel().closeFuture().sync();
        }catch (Exception e) {
            System.out.println("连接断开，正在重连...");
            Thread.sleep(5000);  // 等待 5 秒再尝试重连
            connect();  // 重新连接
        }  finally {
            group.shutdownGracefully();
        }
    }

    public void start() throws InterruptedException {
        connect();
    }


    private String convertToJson(Message message) {
        // 使用 Jackson 库将 Message 转为 JSON 字符串
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }

}
