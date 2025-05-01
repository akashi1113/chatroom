package org.csu.chatroom.Netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.CharsetUtil;

public class SimpleClientHandler extends ChannelInboundHandlerAdapter {

    private final String nickname;

    public SimpleClientHandler(String nickname) {
        this.nickname = nickname;
    }


    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        System.out.println("已连接到服务器，昵称: " + nickname);
    }



    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg)  throws Exception {


        ByteBuf byteBuf  = (ByteBuf) msg;
        String jsonMessage  = byteBuf.toString(CharsetUtil.UTF_8);
        // 使用 Jackson 序列化工具将消息解析为 Message 对象
        ObjectMapper mapper = new ObjectMapper();
        Message message = mapper.readValue(jsonMessage, Message.class);
        // 根据消息类型进行处理
        String messageType = message.getHeader().getMessageType();
        String payload = message.getPayload();

        switch (messageType) {
            case "LOGIN":
                // 登录成功消息
                System.out.println("登录成功，昵称：" + payload);
                break;
            case "JOIN_ROOM":
                // 加入房间消息
                System.out.println("加入房间：" + payload);
                break;
            case "CHAT":
                // 聊天消息
                System.out.println("收到消息：" + payload);
                break;
            case "HEARTBEAT":
                // 收到心跳包
                System.out.println("收到心跳包");
                break;
            case "HISTORY":
                System.out.println(payload);
                break;
            default:
                System.out.println("未知消息类型");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}