package org.example;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/** Entry point for the sensor reader process. */
public final class SensorReader {
    private static final long CYCLE_INTERVAL_MS = 100; 

    private SensorReader() {}

    public static void main(String[] args) throws IOException, InterruptedException {

        System.out.println("Sensor reader started.");
        // Create two sensor instances and an obstacle detector
        Sensor sensor1 = new Sensor();
        Sensor sensor2 = new Sensor();
        ObstacleDetector detector = new ObstacleDetector();
        // Network configuration for sending heartbeat messages
        InetAddress address = InetAddress.getByName("localhost");
        int port = 4445;
        // Convert cycle interval from milliseconds to nanoseconds
        long cycleIntervalNanos = TimeUnit.MILLISECONDS.toNanos(CYCLE_INTERVAL_MS);
        // Create a DatagramSocket for sending heartbeat messages
        try (DatagramSocket socket = new DatagramSocket()){
            // Create a HeartbeatSender instance to send heartbeat messages
            HeartbeatSender heartbeatSender = HeartbeatSender.create(
                    socket,
                    "SensorReader191",
                    address,
                    port
                    );
            while (true) {
                // Record the start time of the cycle
                long cycleStartNanos = System.nanoTime();
                // Read distances from two sensors and detect obstacles
                double distance1 = Double.parseDouble(sensor1.readRawDistance());
                boolean obstacle1 = detector.detectObstacle(distance1);

                double distance2 = Double.parseDouble(sensor2.readRawDistance());
                boolean obstacle2 = detector.detectObstacle(distance2);
                // Print the readings and detection results to the console
                System.out.println("Sensor 1: distance =" + distance1
                        + " meters, obstacleDetected =" + obstacle1);

                System.out.println("Sensor 2: distance=" + distance2
                        + " meters, obstacleDetected=" + obstacle2);
                // Send a heartbeat message to indicate the service is alive
                heartbeatSender.sendMessage();
                // Calculate elapsed time and remaining time to maintain a consistent cycle interval
                long elapsedNanos = System.nanoTime() - cycleStartNanos;
                long remainingNanos = cycleIntervalNanos - elapsedNanos;
                // Sleep for the remaining time if the cycle completed faster than the interval
                if (remainingNanos > 0) {
                    TimeUnit.NANOSECONDS.sleep(remainingNanos);
                } 
            }
        } 
    }

    /**
     * Mocking the reading of inputs from sensors
     *  and detecting objects from the output.
     *  Generates one simultaed random distance reading per call to readDistanceMeters() method.
     */
    private static class Sensor {
        private static final double CORRUPTION_PROBABILITY = 0.1; // 1% chance of corruption
        private final Random rand = new Random();
        
        /**
         * Simulates reading a raw distance measurement from a sensor, which may be corrupted.
         * 7.x is a smulated corrupted reading that is not a valid numeric value.
         * @return A string representing the distance measurement, which may be a valid numeric value or a corrupted value.
         */
        private String readRawDistance() {
            if (rand.nextDouble() <= CORRUPTION_PROBABILITY) {
                return "7.x"; // Simulated corrupted numeric reading
            }
            return Double.toString(rand.nextDouble() * 20);
        }
        


    }
}
