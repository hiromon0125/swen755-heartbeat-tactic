package org.example;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/** Sends periodic heartbeats after start() is called**/
public class HeartbeatSender implements AutoCloseable {
    private final DatagramSocket udpSocket;
    private final String serviceId;
    private final InetAddress address;
    private final int port;

    private final ScheduledExecutorService scheduler = 
            Executors.newSingleThreadScheduledExecutor();

    private static final long SENDING_INTERVAL_MS = 100;

    protected HeartbeatSender(
            DatagramSocket udpSocket,
            String serviceId,
            InetAddress address,
            int port
            ) {

        this.udpSocket = udpSocket;
        this.serviceId = serviceId;
        this.address = address;
        this.port = port;
    }

    public static HeartbeatSender create(
            DatagramSocket udpSocket,
            String serviceId,
            InetAddress address,
            int port
            ) {
        return new HeartbeatSender(
                udpSocket,
                serviceId,
                address,
                port
                );
    }

    /**
     * Starts sending heartbeat messages at a fixed interval defined by SENDING_INTERVAL_MS.
     * 
     * 
     */
    public void start() {
        scheduler.scheduleAtFixedRate(
                () ->  {
                    try {
                        sendMessage();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                },
                0,
                SENDING_INTERVAL_MS,
                TimeUnit.MILLISECONDS
                );

    }


    public void sendMessage() throws IOException {
        HeartbeatMessage message = HeartbeatMessage.createOk(this.serviceId);
        byte[] messageBytes = message.toByteArray();
        // Build the UDP packet with the serialized message and send it to the monitor
        DatagramPacket packet = new DatagramPacket(
                messageBytes,
                messageBytes.length,
                this.address,
                this.port
                );
        udpSocket.send(packet);
        System.out.println("Sent heartbeat: " + message);
    }

    @Override
    public void close() {
        scheduler.close();

    }

}
