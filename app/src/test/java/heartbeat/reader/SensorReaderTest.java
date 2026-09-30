package heartbeat.reader;

import java.util.UUID;

import org.jgroups.JChannel;
import org.jgroups.protocols.SHARED_LOOPBACK;
import org.jgroups.protocols.SHARED_LOOPBACK_PING;
import org.jgroups.protocols.UNICAST3;
import org.jgroups.protocols.pbcast.GMS;
import org.jgroups.protocols.pbcast.NAKACK2;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import heartbeat.PrimaryAssignment;

public class SensorReaderTest {
    static final class CountingSensor extends Sensor {
        int reads;
        String value = "4.0";

        @Override
        String readRawDistance() {
            reads++;
            return value;
        }
    }

    // Real JGroups membership with an in-memory transport keeps election tests deterministic.
    private JChannel channel(String name) throws Exception {
        return new JChannel(new SHARED_LOOPBACK(), new SHARED_LOOPBACK_PING(),
                new NAKACK2(), new UNICAST3(), new GMS()).setName(name);
    }

    @Test
    public void backupDoesNotReadEvenMalformedSensor() {
        CountingSensor sensor = new CountingSensor();
        sensor.value = "7.x";
        new SensorReader(sensor, new ObstacleDetector()).readCycle();
        assertEquals(0, sensor.reads);
    }

    @Test
    public void onlyAssignedSensorReadsAndExpiredLeaseStopsReading() throws Exception {
        java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong(1000);
        CountingSensor sensor = new CountingSensor();
        SensorReader reader = new SensorReader(sensor, new ObstacleDetector(), "distance", clock::get);
        try (JChannel channel = channel("custom")) {
            reader.configureChannel(channel);
            reader.acceptAssignment(new PrimaryAssignment("other-sensor", reader.serviceId(), 1500));
            reader.readCycle();
            assertEquals(0, sensor.reads);
            reader.acceptAssignment(new PrimaryAssignment("distance", reader.serviceId(), 1500));
            reader.readCycle();
            assertEquals(1, sensor.reads);
            clock.set(1500);
            reader.readCycle();
            assertEquals(1, sensor.reads);
            reader.acceptAssignment(new PrimaryAssignment("distance", reader.serviceId(), 2000));
            reader.readCycle();
            assertEquals(2, sensor.reads);
            reader.acceptAssignment(new PrimaryAssignment("distance", "replacement", 2100));
            reader.readCycle();
            assertEquals(2, sensor.reads);
            reader.acceptAssignment(new PrimaryAssignment("distance", reader.serviceId(), 1900));
            reader.readCycle();
            assertEquals("Older assignments must not restore leadership", 2, sensor.reads);
        }
    }

    @Test
    public void onlyMonitorCanAssignAndLosingMonitorStopsReading() throws Exception {
        java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong(1000);
        CountingSensor sensor = new CountingSensor();
        SensorReader reader = new SensorReader(sensor, new ObstacleDetector(), "front", clock::get);
        try (JChannel channel = channel("reader")) {
            reader.configureChannel(channel);
            channel.connect("authority-" + UUID.randomUUID());
            org.jgroups.Address monitor = org.jgroups.util.ExtendedUUID.randomUUID()
                    .put("monitor", new byte[] {1});
            reader.viewAccepted(org.jgroups.View.create(monitor, 1, monitor, channel.getAddress()));
            PrimaryAssignment assignment = new PrimaryAssignment("front", reader.serviceId(), 1500);
            reader.receive(new org.jgroups.ObjectMessage(null, assignment).setSrc(channel.getAddress()));
            reader.readCycle();
            assertEquals(0, sensor.reads);
            reader.receive(new org.jgroups.ObjectMessage(null, assignment).setSrc(monitor));
            reader.readCycle();
            assertEquals(1, sensor.reads);
            reader.viewAccepted(org.jgroups.View.create(channel.getAddress(), 2, channel.getAddress()));
            reader.readCycle();
            assertEquals(1, sensor.reads);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBlankSensorId() throws Exception {
        SensorReader.main(new String[] {"reader", " "});
    }

    @Test(expected = NumberFormatException.class)
    public void primaryPropagatesCorruptReading() throws Exception {
        CountingSensor sensor = new CountingSensor();
        sensor.value = "7.x";
        SensorReader reader = new SensorReader(sensor, new ObstacleDetector());
        try (JChannel channel = channel("reader")) {
            reader.configureChannel(channel);
            channel.connect("corruption-" + UUID.randomUUID());
            reader.acceptAssignment(new PrimaryAssignment("sensor-1", reader.serviceId(),
                    System.currentTimeMillis() + 500));
            reader.readCycle();
        }
    }

    @Test
    public void generatedIdsIncludeChannelNameAndUniqueUuid() {
        SensorReader reader = new SensorReader(new CountingSensor(), new ObstacleDetector());
        String first = reader.getServiceId("custom");
        String second = reader.getServiceId("custom");
        assertTrue(first.startsWith("custom-"));
        UUID.fromString(first.substring("custom-".length()));
        assertNotEquals(first, second);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBlankChannelName() throws Exception {
        SensorReader.main(new String[] {" "});
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsExtraArguments() throws Exception {
        SensorReader.main(new String[] {"one", "two", "three"});
    }

    static void awaitMembers(JChannel channel, int count) throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (channel.getView().size() != count && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals("Expected cluster membership", count, channel.getView().size());
    }

    @Test
    public void backupRestoresCheckpointWhenPromoted() throws Exception {
        java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong(1000);
        CountingSensor sensor = new CountingSensor();
        SensorReader reader = new SensorReader(sensor, new ObstacleDetector(), "front", clock::get);

        try (JChannel channel = channel("reader")) {
            reader.configureChannel(channel);
            channel.connect("checkpoint-" + UUID.randomUUID());

            // Establish a monitor.
            org.jgroups.Address monitor = org.jgroups.util.ExtendedUUID.randomUUID().put("monitor", new byte[] {1});

        reader.viewAccepted(org.jgroups.View.create(monitor, 1, monitor, channel.getAddress()));

        // Simulate a checkpoint sent by the previous primary.
        Checkpoint checkpoint = new Checkpoint("front", "old-primary", 5, 2.5, true);

        reader.receive(new org.jgroups.ObjectMessage(null, checkpoint).setSrc(org.jgroups.util.ExtendedUUID.randomUUID()));

        // Monitor promotes this backup to primary.
        PrimaryAssignment assignment = new PrimaryAssignment("front", reader.serviceId(), 1500);

        reader.receive(new org.jgroups.ObjectMessage(null, assignment).setSrc(monitor));

        // Verify that the checkpoint state was restored.
        assertEquals(5, reader.state().getReadingsProcessed());
        assertEquals(2.5, reader.state().getLastDistance(), 0.001);
        assertTrue(reader.state().isLastObstacleDetected());
    }
    }   
}