package org.csu.chatroom.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.csu.chatroom.entity.FileTransfer;

import java.util.List;

@Mapper
public interface FileTransferMapper {

    @Insert("INSERT INTO file_transfer (file_id, file_name, file_size, sender, room_id, transfer_time, status) " +
            "VALUES (#{fileId}, #{fileName}, #{fileSize}, #{sender}, #{roomId}, #{transferTime}, #{status})")
    void insertFileTransfer(FileTransfer fileTransfer);

    @Select("SELECT id, file_id, file_name, file_size, sender, room_id, transfer_time, status " +
            "FROM file_transfer WHERE file_id = #{fileId}")
    FileTransfer getFileTransferByFileId(@Param("fileId") String fileId);

    @Select("SELECT id, file_id, file_name, file_size, sender, room_id, transfer_time, status " +
            "FROM file_transfer WHERE room_id = #{roomId}")
    List<FileTransfer> getFilesByRoomId(@Param("roomId") Integer roomId);
}
