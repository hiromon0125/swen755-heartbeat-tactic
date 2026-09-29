package heartbeat.reader;

import heartbeat.HeartbeatMessage;
import heartbeat.HeartbeatChannel;
import heartbeat.PrimaryAssignment;

import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.Receiver;
import org.jgroups.protocols.UDP;
import org.junit.Test;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class HeartbeatCommunicationTest {
    @Test
    public void backupSendsHeartbeatsWithoutReadingSensors() throws Exception {
        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<>();
        String cluster = "backup-heartbeat-" + UUID.randomUUID();
        SensorReaderTest.CountingSensor primarySensor = new SensorReaderTest.CountingSensor();
        SensorReaderTest.CountingSensor backupSensor = new SensorReaderTest.CountingSensor();
        SensorReader primary = new SensorReader(primarySensor, new ObstacleDetector());
        SensorReader backup = new SensorReader(backupSensor, new ObstacleDetector());
        try (JChannel monitor = HeartbeatChannel.create("monitor");
                JChannel first = HeartbeatChannel.create("primary");
                JChannel second = HeartbeatChannel.create("backup")) {
            primary.configureChannel(first);
            backup.configureChannel(second);
            monitor.setReceiver(new Receiver() {
                @Override
                public void receive(Message message) {
                    received.add(HeartbeatMessage.fromByteArray(Arrays.copyOfRange(
                            message.getArray(), message.getOffset(),
                            message.getOffset() + message.getLength())).getServiceId());
                }
            });
            monitor.connect(cluster);
            first.connect(cluster);
            second.connect(cluster);
            SensorReaderTest.awaitMembers(second, 3);
            try (HeartbeatSender primarySender = HeartbeatSender.create(first, "primary-service");
                    HeartbeatSender backupSender = HeartbeatSender.create(second, "backup-service")) {
                primarySender.start();
                backupSender.start();
                java.util.Set<String> ids = new java.util.HashSet<>();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (ids.size() < 2 && System.nanoTime() < deadline) {
                    String id = received.poll(100, TimeUnit.MILLISECONDS);
                    if (id != null) ids.add(id);
                }
                assertEquals(java.util.Set.of("primary-service", "backup-service"), ids);
                primary.acceptAssignment(new PrimaryAssignment("sensor-1", primary.serviceId(),
                        System.currentTimeMillis() + 500));
                primary.readCycle();
                backup.readCycle();
                assertEquals(1, primarySensor.reads);
                assertEquals(0, backupSensor.reads);
            }
        }
    }

    @Test
    public void scheduledHeartbeatsArriveOverUdpAndStopAfterClose() throws Exception {
        LinkedBlockingQueue<HeartbeatMessage> received = new LinkedBlockingQueue<>();
        String cluster = "udp-test-" + UUID.randomUUID();
        try (JChannel monitor = HeartbeatChannel.create("test-monitor");
                JChannel sensor = HeartbeatChannel.create("test-sensor")) {
            assertTrue(sensor.getProtocolStack().getTransport() instanceof UDP);
            assertEquals("test-sensor", sensor.getName());
            monitor.setReceiver(new Receiver() {
                @Override
                public void receive(Message message) {
                    received.add(HeartbeatMessage.fromByteArray(Arrays.copyOfRange(
                            message.getArray(), message.getOffset(),
                            message.getOffset() + message.getLength())));
                }
            });
            monitor.connect(cluster);
            sensor.connect(cluster);
            SensorReaderTest.awaitMembers(sensor, 2);
            try (HeartbeatSender sender = HeartbeatSender.create(sensor, "generated-service-id")) {
                sender.start();
                for (int i = 0; i < 3; i++) {
                    HeartbeatMessage heartbeat = received.poll(5, TimeUnit.SECONDS);
                    assertNotNull("Scheduled heartbeat must arrive", heartbeat);
                    assertEquals("generated-service-id", heartbeat.getServiceId());
                    assertEquals(HeartbeatMessage.Status.OK, heartbeat.getStatus());
                }
            }
            // Drain packets already in flight, then require a quiet period longer than a cycle.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (received.poll(350, TimeUnit.MILLISECONDS) != null) {
                assertTrue("Sender should stop after close", System.nanoTime() < deadline);
            }
            assertTrue("Closing sender does not own/close channel", sensor.isConnected());
        }
    }
}
