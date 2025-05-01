package org.csu.chatroom.Netty;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.util.CharsetUtil;
import org.springframework.stereotype.Component;

@Component
public class MessageSender {

    public void sendMessage(Channel channel, String messageType, String messageId, String payload) {
        // 计算消息长度
        int messageLength = payload.length();

        // 生成校验和（示例使用简单的字符串长度）
        String checksum = String.valueOf(payload.hashCode());

        // 创建包头
        Message.MessageHeader header = new Message.MessageHeader(
                messageType,
                messageId,
                messageLength,
                checksum
        );

        // 创建完整的消息
        Message message = new Message(messageType, messageId, messageLength, checksum, payload);

        // 将消息转换为字节流发送
        String jsonMessage = convertToJson(message);
        channel.writeAndFlush(Unpooled.copiedBuffer(jsonMessage, CharsetUtil.UTF_8));
    }

    public String convertToJson(Message message) {
        // 使用 JSON 序列化工具（如 Jackson 或 Gson）将 Message 转为 JSON 字符串
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }
}
