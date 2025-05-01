package org.csu.chatroom.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.csu.chatroom.entity.Room;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface RoomMapper{
    public int createRoom(Room room);
    public List<Room> getRooms();
    public Room getRoomById(int id);
    public Room getRoomByName(String name);
}

