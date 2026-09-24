package org.example;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Random;

/** Entry point for the sensor reader process. */
public final class SensorReader {
    private SensorReader() {}

    public static void main(String[] args) throws SocketException, UnknownHostException {
        System.out.println("Sensor reader started.");
        Sensor sensor1 = new Sensor(1);
        Sensor sensor2 = new Sensor(2);

        InetAddress address = InetAddress.getByName("localhost");
        int port = 4445;

        HeartbeatSender heartbeatSender = HeartbeatSender.create(
                new DatagramSocket(port, address),
                "SensorReader191",
                address,
                port
        );

        Thread t1 = new Thread(sensor1);
        Thread t2 = new Thread(sensor2);
        Thread heartbeat = new Thread(heartbeatSender);

        t1.start();
        t2.start();
        heartbeat.start();
    }

    /**
     * Mocking the reading of inputs from sensors
     *  and detecting objects from the output.
     */
    private static class Sensor implements Runnable {
        private int seed;

        private Sensor(int seed) {
            this.seed = seed;
        }

        @Override
        public void run() {
            Random rand = new Random(seed);
            ObstacleDetector detector = new ObstacleDetector();
            

            while (true) {
                double distanceMeters = rand.nextDouble() * 20; // Simulate sensor data between 0 and 20 meters
                boolean obstacleDetected = detector.detectObstacle(distanceMeters);
                System.out.println("Raw sensor " + seed + " data output: " + distanceMeters + " meters. Obstacle detected: " + obstacleDetected);
                
                try {
                    Thread.sleep(rand.nextInt(100, 500));
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }
}
