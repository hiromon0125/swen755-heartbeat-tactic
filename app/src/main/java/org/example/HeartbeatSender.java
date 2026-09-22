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
    private final long sendingIntervalMs;
    private final DatagramSocket udpSocket;
    private final String serviceId;
    private final InetAddress address;
    private final int port;


    public static final long DEFAULT_INTERVAL_MS = 100L;

    protected HeartbeatSender(
            long sendingIntervalMs,
            DatagramSocket udpSocket,
            String serviceId,
            InetAddress address,
            int port
    ) {
        this.sendingIntervalMs = sendingIntervalMs;
        this.udpSocket = udpSocket;
        this.serviceId = serviceId;
        this.address = address;
        this.port = port;
    }

    public static HeartbeatSender create(
        long sendingIntervalMs,
        DatagramSocket udpSocket,
        String serviceId,
        InetAddress address,
        int port
    ) {
        return new HeartbeatSender(
                sendingIntervalMs,
                udpSocket,
                serviceId,
                address,
                port
        );
    }

    public static HeartbeatSender create(
            DatagramSocket udpSocket,
            String serviceId,
            InetAddress address,
            int port
    ) {
        return HeartbeatSender.create(
                DEFAULT_INTERVAL_MS,
                udpSocket,
                serviceId,
                address,
                port
        );
    }

    /**
     * Starting the thread begins sending heartbeat messages.
     */
    @Override
    public void run() {
        System.out.println("Heartbeat for service " + serviceId + " started.");

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
        System.out.println("Sent message: " + message);
        byte[] messageBytes = message.toByteArray();

        DatagramPacket packet = new DatagramPacket(
                messageBytes,
                messageBytes.length,
                this.address,
                this.port
        );

        try {
            udpSocket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
