package org.example;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/** Entry point for the monitor process. */
public final class Monitor {
    
    private long monitorStartNanos;
    private long lastHeartbeatNanos = 0;
    private boolean heartbeatReceived = false;
    private boolean failureReported = false;
    
    
    private Monitor() {}

    public static void main(String[] args) {
        Monitor monitor = new Monitor();
        monitor.run();
    }
    
    public void run() {
        try(DatagramSocket receiver = new DatagramSocket(4445)){
            receiver.setSoTimeout(50); // Wait 50 ms for a packet before timing out, prevents blocking indefinitely
            monitorStartNanos = System.nanoTime();

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
                        updateTime();
                        System.out.println("Received heartbeat :" + message);
                    }
                } catch (SocketTimeoutException e) {
                    // No packet arrived during this wait. Continue to the health check below
                }
                
                if (!failureReported && !checkAlive()) {
                    if (heartbeatReceived) {
                        System.out.println("Heartbeat timeout: SensorReader191 may have failed.");
                    } else {
                        System.out.println("Startup timeout: no heartbeat received from SensorReader191.");
                    }
                    failureReported = true;
                }
                
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    
    /**
     * Updates the last heartbeat time to the current time, sets heartbeatReceived to true, and resets failureReported to false.
     */
    private void updateTime() {
        lastHeartbeatNanos = System.nanoTime();
        heartbeatReceived = true;
        failureReported = false;
    }
    
    private boolean checkAlive() {
        long now = System.nanoTime();

        if (!heartbeatReceived) {
            return now - monitorStartNanos < TimeUnit.SECONDS.toNanos(10);
        }

        return now - lastHeartbeatNanos < TimeUnit.MILLISECONDS.toNanos(500);
    }
    
    
    
}
