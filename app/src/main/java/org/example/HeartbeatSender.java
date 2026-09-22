package org.example;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Regularly sends a heartbeat signal to a receiver **/
public class HeartbeatSender implements Runnable {
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

    /**
     * Starting the thread begins sending heartbeat messages.
     */
    public void run() {
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(
                this::sendMessage,
                0,
                sendingIntervalMs,
                TimeUnit.MILLISECONDS
        );
    }

    private void sendMessage() {
        HeartbeatMessage message = HeartbeatMessage.createOk(this.serviceId);
        byte[] messageBytes = message.toByteArray();

        DatagramPacket packet = new DatagramPacket(messageBytes, messageBytes.length);
        try {
            udpSocket.receive(packet);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        InetAddress address = packet.getAddress();
        int port = packet.getPort();
        packet = new DatagramPacket(messageBytes, messageBytes.length, address, port);

        try {
            udpSocket.send(packet);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
