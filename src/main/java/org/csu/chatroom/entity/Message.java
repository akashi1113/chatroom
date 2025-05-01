package org.csu.chatroom.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Message {
    private Integer id;
    private int roomId;
    private int userId;
    private String content;
    private LocalDateTime createTime;
}

