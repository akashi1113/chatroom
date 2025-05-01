package org.csu.chatroom.service;

import org.csu.chatroom.entity.Message;
import org.csu.chatroom.entity.Room;
import org.csu.chatroom.persistence.MessageMapper;
import org.csu.chatroom.persistence.RoomMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class RoomService {
    @Autowired
    RoomMapper roomMapper;

    @Autowired
    MessageMapper messageMapper;

    public int createRoom(Room room) {
        return roomMapper.createRoom(room);
    }

    public List<Room> getRooms() {
        return roomMapper.getRooms();
    }

    public Room getRoomById(int id) {
        return roomMapper.getRoomById(id);
    }

    public Room getRoomByName(String name) {
        return roomMapper.getRoomByName(name);
    }

    @Transactional
    public void saveMessage(Message message) {
        if (message.getCreateTime() == null) {
            message.setCreateTime(new Date());
        }
        messageMapper.insertMessage(message);
    }

    public List<Message> getRecentMessages(int roomId, int limit) {
        return messageMapper.selectRecentMessages(roomId, limit);
    }

    //按时间范围查询消息
    public List<Message> getMessagesByTimeRange(int roomId, Date startTime, Date endTime) {
        return messageMapper.selectByTimeRange(roomId, startTime, endTime);
    }
}
