package org.csu.chatroom.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class Message {
    private Integer id;
    private int roomId;
    private int sender;
    private int receiver;
    private String content;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    public boolean isPrivateMessage() {
        return receiver != 0; // 如果receiver有值则是私聊，否则是群聊
    }
}

