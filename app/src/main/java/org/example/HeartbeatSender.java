package org.example;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;


/** Regularly sends a heartbeat signal to a receiver **/
public class HeartbeatSender  {
    private final DatagramSocket udpSocket;
    private final String serviceId;
    private final InetAddress address;
    private final int port;


    

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
            System.out.println("Sent heartbeat: " + message.toString());
    }

}
