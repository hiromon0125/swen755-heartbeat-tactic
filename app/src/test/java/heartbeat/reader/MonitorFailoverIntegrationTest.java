package heartbeat.reader;

import heartbeat.monitor.MonitorHarness;

import heartbeat.HeartbeatMessage;
import heartbeat.HeartbeatChannel;

import org.jgroups.JChannel;
import org.junit.Test;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class MonitorFailoverIntegrationTest {
    @Test
    public void monitorPromotesOnlyMatchingBackupWhenPrimaryHeartbeatsStopOverUdp() throws Exception {
        AtomicLong clock = new AtomicLong(1000);
        MonitorHarness monitor = new MonitorHarness(clock::get);
        SensorReaderTest.CountingSensor frontSensor = new SensorReaderTest.CountingSensor();
        SensorReaderTest.CountingSensor frontBackupSensor = new SensorReaderTest.CountingSensor();
        SensorReaderTest.CountingSensor rearSensor = new SensorReaderTest.CountingSensor();
        SensorReaderTest.CountingSensor rearBackupSensor = new SensorReaderTest.CountingSensor();
        SensorReader front = new SensorReader(frontSensor, new ObstacleDetector(), "front", clock::get);
        SensorReader frontBackup = new SensorReader(frontBackupSensor, new ObstacleDetector(), "front", clock::get);
        SensorReader rear = new SensorReader(rearSensor, new ObstacleDetector(), "rear", clock::get);
        SensorReader rearBackup = new SensorReader(rearBackupSensor, new ObstacleDetector(), "rear", clock::get);
        String cluster = "monitor-failover-" + UUID.randomUUID();
        try (JChannel monitorChannel = HeartbeatChannel.create("monitor");
                JChannel frontChannel = HeartbeatChannel.create("front-reader");
                JChannel frontBackupChannel = HeartbeatChannel.create("front-backup");
                JChannel rearChannel = HeartbeatChannel.create("rear-reader");
                JChannel rearBackupChannel = HeartbeatChannel.create("rear-backup")) {
            monitor.configureChannel(monitorChannel);
            java.util.concurrent.LinkedBlockingQueue<String> observed = new java.util.concurrent.LinkedBlockingQueue<>();
            org.jgroups.Receiver receiver = monitorChannel.getReceiver();
            monitorChannel.setReceiver(new org.jgroups.Receiver() {
                @Override
                public void receive(org.jgroups.Message message) {
                    receiver.receive(message);
                    if (!(message instanceof org.jgroups.ObjectMessage)) {
                        HeartbeatMessage heartbeat = HeartbeatMessage.fromByteArray(java.util.Arrays.copyOfRange(
                                message.getArray(), message.getOffset(), message.getOffset() + message.getLength()));
                        observed.add(heartbeat.getServiceId());
                    }
                }
            });
            front.configureChannel(frontChannel);
            frontBackup.configureChannel(frontBackupChannel);
            rear.configureChannel(rearChannel);
            rearBackup.configureChannel(rearBackupChannel);
            monitorChannel.connect(cluster);
            frontChannel.connect(cluster);
            frontBackupChannel.connect(cluster);
            rearChannel.connect(cluster);
            rearBackupChannel.connect(cluster);
            SensorReaderTest.awaitMembers(rearBackupChannel, 5);
            try (HeartbeatSender frontSender = HeartbeatSender.create(frontChannel, front.serviceId(), "front");
                    HeartbeatSender frontBackupSender = HeartbeatSender.create(frontBackupChannel,
                            frontBackup.serviceId(), "front");
                    HeartbeatSender rearSender = HeartbeatSender.create(rearChannel, rear.serviceId(), "rear");
                    HeartbeatSender rearBackupSender = HeartbeatSender.create(rearBackupChannel,
                            rearBackup.serviceId(), "rear")) {
                awaitReading(monitor, frontSender, front, frontSensor);
                awaitReading(monitor, rearSender, rear, rearSensor);
                observed.clear();
                frontBackupSender.sendMessage();
                awaitHeartbeat(observed, frontBackup.serviceId());
                rearBackupSender.sendMessage();
                awaitHeartbeat(observed, rearBackup.serviceId());
                frontBackup.readCycle();
                rearBackup.readCycle();
                assertEquals(0, frontBackupSensor.reads);
                assertEquals(0, rearBackupSensor.reads);

                // Leave all five JGroups members connected. Only the primary's heartbeats stop.
                clock.set(1400);
                observed.clear();
                rearSender.sendMessage();
                awaitHeartbeat(observed, rear.serviceId());
                rearBackupSender.sendMessage();
                awaitHeartbeat(observed, rearBackup.serviceId());
                clock.set(1500);
                awaitReading(monitor, frontBackupSender, frontBackup, frontBackupSensor);
                int oldReads = frontSensor.reads;
                front.readCycle();
                assertEquals("Expired primary must stop reading", oldReads, frontSensor.reads);
                awaitReading(monitor, rearSender, rear, rearSensor);
                rearBackup.readCycle();
                assertEquals("Other sensor's backup must remain backup", 0, rearBackupSensor.reads);
                assertEquals("Promotion must not depend on a membership change", 5,
                        monitorChannel.getView().size());
            }
        }
    }

    private void awaitHeartbeat(java.util.concurrent.LinkedBlockingQueue<String> observed, String serviceId)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (serviceId.equals(observed.poll(100, TimeUnit.MILLISECONDS))) return;
        }
        fail("MonitorHarness must receive heartbeat from " + serviceId);
    }

    private void awaitReading(MonitorHarness monitor, HeartbeatSender sender, SensorReader reader,
            SensorReaderTest.CountingSensor sensor) throws Exception {
        int previous = sensor.reads;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (sensor.reads == previous && System.nanoTime() < deadline) {
            sender.sendMessage();
            monitor.checkHealth();
            reader.readCycle();
            Thread.sleep(20);
        }
        assertTrue("MonitorHarness assignment must enable sensor reading", sensor.reads > previous);
    }
}
