package org.csu.chatroom.entity;

import lombok.Data;

import java.util.Date;

@Data
public class FileTransfer {
    private Integer roomId;
    private Long id;
    private String fileId;
    private String fileName;
    private Long fileSize;
    private String sender;
    private Date transferTime;
    private String status;
}


