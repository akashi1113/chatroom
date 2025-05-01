package org.csu.chatroom.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.csu.chatroom.entity.Message;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Mapper
@Repository
public interface MessageMapper {
    public int insertMessage(Message message);
    public List<Message> selectRecentMessages(@Param("roomId") int roomId, @Param("limit") int limit);
    List<Message> selectByTimeRange(@Param("roomId") int roomId,
                                    @Param("startTime") Date startTime,
                                    @Param("endTime") Date endTime);
}
