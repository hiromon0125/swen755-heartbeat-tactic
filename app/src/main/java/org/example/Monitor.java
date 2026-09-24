package org.example;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Arrays;

/** Entry point for the monitor process. */
public final class Monitor {
    private Monitor() {}

    public static void main(String[] args) {
        try(DatagramSocket receiver = new DatagramSocket(4445)){
            while(true) {
                byte[] buffer = new byte[4096];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                
                // Wait for a UDP packet
                receiver.receive(packet);
                // Deserialize the packet into a HeartbeatMessage
                HeartbeatMessage message = HeartbeatMessage.fromByteArray(Arrays.copyOf(packet.getData(), packet.getLength()));
                
                // Print the heartbeat message
                System.out.println("Received heartbeat : message = " + message.toString());
            }
            
            
            
        } catch (IOException e) {
            
            e.printStackTrace();
        }
        
    }
}
