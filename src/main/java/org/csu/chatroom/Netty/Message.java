package org.csu.chatroom.Netty;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Message {
    private MessageHeader header;
    private String payload;  // 载荷通常是JSON字符串

    public Message() {
    }

    public Message(MessageHeader header, String payload) {
        this.header = header;
        this.payload = payload;
    }

    public Message(String messageType, String messageId, int messageLength, String checksum, String payload) {
        this.header = new MessageHeader(messageType, messageId, messageLength, checksum);
        this.payload = payload;
    }

    public MessageHeader getHeader() {
        return header;
    }

    public void setHeader(MessageHeader header) {
        this.header = header;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessageHeader {
        private String messageType;
        private String messageId;
        private int messageLength;  // 可以忽略，不用前端传
        private String checksum;    // 可以忽略，不用前端传

        public MessageHeader() {
        }

        public MessageHeader(String messageType, String messageId, int messageLength, String checksum) {
            this.messageType = messageType;
            this.messageId = messageId;
            this.messageLength = messageLength;
            this.checksum = checksum;
        }

        public String getMessageType() {
            return messageType;
        }

        public void setMessageType(String messageType) {
            this.messageType = messageType;
        }

        public String getMessageId() {
            return messageId;
        }

        public void setMessageId(String messageId) {
            this.messageId = messageId;
        }

        public int getMessageLength() {
            return messageLength;
        }

        public void setMessageLength(int messageLength) {
            this.messageLength = messageLength;
        }

        public String getChecksum() {
            return checksum;
        }

        public void setChecksum(String checksum) {
            this.checksum = checksum;
        }
    }
}
