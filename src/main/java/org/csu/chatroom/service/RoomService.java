package org.csu.chatroom.service;

import org.csu.chatroom.entity.Room;
import org.csu.chatroom.persistence.RoomMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RoomService {
    @Autowired
    RoomMapper roomMapper;

    public int createRoom(Room room) {
        return roomMapper.createRoom(room);
    }

    public List<Room> getRooms() {
        return roomMapper.getRooms();
    }

    public Room getRoom(int id) {
        return roomMapper.getRoom(id);
    }
}
