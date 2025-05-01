package org.csu.chatroom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ChatRoomApplicationTests {

    @Autowired
    private ScMapper scMapper;


    @Test
    void contextLoads() {

       Sc sc =  scMapper.selectById("4545645");
        System.out.println(sc);
    }

}
