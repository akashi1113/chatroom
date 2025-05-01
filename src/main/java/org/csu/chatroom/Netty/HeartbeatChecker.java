package org.csu.chatroom.Netty;

public class HeartbeatChecker extends Thread {
    private final NettyServer nettyServer;

    public HeartbeatChecker(NettyServer nettyServer) {
        this.nettyServer = nettyServer;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(30000);  // 每 30 秒检查一次
                nettyServer.checkHeartbeat();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
