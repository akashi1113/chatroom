package org.csu.chatroom.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.csu.chatroom.entity.User;
import org.springframework.stereotype.Repository;

@Mapper
@Repository
public interface UserMapper{
    public User login(String username, String password);
}
