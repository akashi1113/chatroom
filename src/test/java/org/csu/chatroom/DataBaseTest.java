package org.csu.chatroom;

import org.csu.chatroom.Netty.NettyServer;
import org.csu.chatroom.entity.Message;
import org.csu.chatroom.entity.Room;
import org.csu.chatroom.entity.User;
import org.csu.chatroom.persistence.MessageMapper;
import org.csu.chatroom.persistence.RoomMapper;
import org.csu.chatroom.persistence.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

@SpringBootTest
 class DatabaseTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoomMapper roomMapper;

    @Autowired
    private MessageMapper messageMapper;

    @MockBean
    private NettyServer nettyServer;  // 模拟 NettyServer
    @Test
    public void testCRUD() {
        // 创建用户
        User user = new User();

        user.setUsername("admin");
        user.setPassword("admin123");

        userMapper.insert(user);

        // 创建聊天室
        Room room = new Room();
        room.setName("Java交流群");
        roomMapper.insert(room);


        // 创建消息
        Message message = new Message();
        message.setRoomId(room.getId());
        message.setUserId(user.getId());
        message.setContent("欢迎加入 Java 交流群！");
        messageMapper.insert(message);

        // 查询消息
        List<Message> messages = messageMapper.selectList(null);
        messages.forEach(System.out::println);
    }
}

