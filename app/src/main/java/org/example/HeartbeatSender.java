package org.example;

import java.net.DatagramSocket;

/** Regularly sends a heartbeat signal to a receiver **/
public class HeartbeatSender {
    private int sendingIntervalMs;
    private DatagramSocket udpSocket;

    protected HeartbeatSender(
            int sendingIntervalMs,
            DatagramSocket udpSocket
    ) {
        this.sendingIntervalMs = sendingIntervalMs;
        this.udpSocket = udpSocket;
    }

    public static HeartbeatSender create(
        int sendingIntervalMs,
        DatagramSocket udpSocket
    ) {
        return new HeartbeatSender(
                sendingIntervalMs,
                udpSocket
        );
    }


}
