package org.csu.chatroom.Netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;

public class WebSocketHandshakeInterceptor extends ChannelInboundHandlerAdapter {
    private final NettyServer nettyServer;

    public WebSocketHandshakeInterceptor(NettyServer nettyServer) {
        this.nettyServer = nettyServer;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            FullHttpRequest request = (FullHttpRequest) msg;
            QueryStringDecoder queryDecoder = new QueryStringDecoder(request.uri());

            // 从URL参数获取连接类型
            boolean isPrivate = queryDecoder.parameters().containsKey("type") &&
                    "private".equals(queryDecoder.parameters().get("type").get(0));

            nettyServer.setConnectionType(ctx.channel(), isPrivate);
        }
        super.channelRead(ctx, msg);
    }
}
