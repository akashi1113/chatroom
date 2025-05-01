package org.csu.chatroom.service;

import org.csu.chatroom.entity.User;
import org.csu.chatroom.persistence.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    @Autowired
    UserMapper userMapper;

    public User login(String username,String password) {
        return userMapper.login(username,password);
    }

    public int getUserId(String username){
        return userMapper.getUserId(username);
    }

    public String getUserName(int userId){
        return userMapper.getUserName(userId);
    }
}
