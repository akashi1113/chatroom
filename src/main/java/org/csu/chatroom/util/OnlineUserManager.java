package org.csu.chatroom.util;


import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class OnlineUserManager {
    private static final Set<String> onlineUsers = ConcurrentHashMap.newKeySet();

    public static void addUser(String username) {
        onlineUsers.add(username);
    }

    public static void removeUser(String username) {
        onlineUsers.remove(username);
    }

    public static Set<String> getOnlineUsers() {
        return onlineUsers;
    }
}
