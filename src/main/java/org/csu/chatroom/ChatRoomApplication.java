package org.csu.chatroom;

import org.csu.chatroom.Netty.NettyServer;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("org.csu.chatroom.persistence")
@SpringBootApplication
public class ChatRoomApplication {

    @Autowired
    private NettyServer nettyServer;

    public static void main(String[] args) {
        SpringApplication.run(ChatRoomApplication.class, args);
    }

//    @Override
//    public void run(String... args) {
//        // 启动 Netty 服务器，但放到新的线程里执行
//        new Thread(() -> {
//            try {
//                nettyServer.start();
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }).start();
//    }
}
