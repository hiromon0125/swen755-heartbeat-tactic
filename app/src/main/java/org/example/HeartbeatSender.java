package org.example;

import java.net.DatagramSocket;

/** Regularly sends a heartbeat signal to a receiver **/
public class HeartbeatSender {
    private int sendingIntervalMs;
    private DatagramSocket udpSocket;
    private String serviceId;

    public static final int DEFAULT_INTERVAL_MS = 100;

    protected HeartbeatSender(
            int sendingIntervalMs,
            DatagramSocket udpSocket,
            String serviceId
    ) {
        this.sendingIntervalMs = sendingIntervalMs;
        this.udpSocket = udpSocket;
        this.serviceId = serviceId;
    }

    public static HeartbeatSender create(
        int sendingIntervalMs,
        DatagramSocket udpSocket,
        String serviceId
    ) {
        return new HeartbeatSender(
                sendingIntervalMs,
                udpSocket,
                serviceId
        );
    }

    public static HeartbeatSender create(
            DatagramSocket udpSocket,
            String serviceId
    ) {
        return HeartbeatSender.create(
                DEFAULT_INTERVAL_MS,
                udpSocket,
                serviceId
        );
    }


}
