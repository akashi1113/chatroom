package org.csu.chatroom.util;

public class ChatUtils {
    public static String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9.-]", "_");
    }
}
