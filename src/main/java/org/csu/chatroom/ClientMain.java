package org.csu.chatroom;

import org.csu.chatroom.Netty.NettyClient;

public class ClientMain {
    public static void main(String[] args) throws InterruptedException {
        new NettyClient("localhost", 8081).start();
    }
}

