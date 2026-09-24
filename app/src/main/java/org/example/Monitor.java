package org.example;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/** Entry point for the monitor process. */
public final class Monitor {
    private Monitor() {}

    public static void main(String[] args) {
        try(DatagramSocket receiver = new DatagramSocket(4445)){
            receiver.setSoTimeout(50); // Wait 50 ms for a packet before timing out, prevents blocking indefinitely
            long monitorStartNanos = System.nanoTime();
            long lastHeartbeatNanos = 0;
            boolean heartbeatReceived = false;
            boolean failureReported = false;

            while(true) {
                // Create a buffer to hold incoming UDP packets
                byte[] buffer = new byte[4096];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    // Wait for a UDP packet
                    receiver.receive(packet);
                    // Deserialize the packet into a HeartbeatMessage
                    HeartbeatMessage message = HeartbeatMessage.fromByteArray(Arrays.copyOf(packet.getData(), packet.getLength()));
                    // Update flags for successful heartbeat reception
                    if ("SensorReader191".equals(message.getServiceId())) {
                        lastHeartbeatNanos = System.nanoTime();
                        heartbeatReceived = true;
                        failureReported = false;
                        // Print the heartbeat message
                        System.out.println("Received heartbeat :" + message);
                    }
                } catch (SocketTimeoutException e) {
                    // No packet arrived during this wait. Continue to the health check below
                }
                // Check if the heartbeat has been received within the expected time frame
                long now = System.nanoTime();
                // Allow 10 seconds for the first heartbeat.
                // After that, report a timeout after 500 ms without a matching heartbeat.
                if (!failureReported) {
                    if (!heartbeatReceived
                            && now - monitorStartNanos >= TimeUnit.SECONDS.toNanos(10)) {
                        System.out.println("Startup timeout: no heartbeat received from SensorReader191.");
                        failureReported = true;

                    } else if (heartbeatReceived
                            && now - lastHeartbeatNanos >= TimeUnit.MILLISECONDS.toNanos(500)) {
                        System.out.println("Heartbeat timeout: SensorReader191 may have failed.");
                        failureReported = true;
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
