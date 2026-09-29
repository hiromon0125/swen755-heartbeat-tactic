package heartbeat.reader;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ObjectMessage;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.jgroups.logging.Log;
import org.jgroups.logging.LogFactory;
import org.jgroups.util.ExtendedUUID;

import heartbeat.HeartbeatChannel;
import heartbeat.PrimaryAssignment;

/** Entry point for the sensor reader process. */
public final class SensorReader implements Receiver {
    private static final Log LOG = LogFactory.getLog(SensorReader.class);
    private static final long CYCLE_INTERVAL_MS = 100;
    private static final String SENSOR_MEMBER_KEY = "sensor-reader";
    private volatile Role role = Role.BACKUP;
    private volatile PrimaryAssignment assignment;
    private volatile Address monitorAddress;
    private final String sensorId;
    private final LongSupplier clock;
    private String serviceId;
    private final Sensor sensor;
    private final ObstacleDetector detector;

    public SensorReader(Sensor sensor, ObstacleDetector detector) {
        this(sensor, detector, "sensor-1", System::currentTimeMillis);
    }

    SensorReader(Sensor sensor, ObstacleDetector detector, String sensorId, LongSupplier clock) {
        this.sensor = sensor;
        this.detector = detector;
        this.sensorId = sensorId;
        this.clock = clock;
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 2 || java.util.Arrays.stream(args).anyMatch(arg -> arg.isBlank())) {
            throw new IllegalArgumentException("Usage: SensorReader [channel-name] [sensor-id]");
        }
        String channelName = args.length == 0 ? "sensor-reader" : args[0];
        String sensorId = args.length < 2 ? "sensor-1" : args[1];
        SensorReader reader = new SensorReader(new Sensor(), new ObstacleDetector(), sensorId,
                System::currentTimeMillis);
        reader.run(channelName);
    }

    private void run(String channelName) throws Exception {
        serviceId = getServiceId(channelName);
        LOG.info("%s: started", serviceId);
        // Close the heartbeat sender before the channel if sensor processing fails.
        try (JChannel channel = HeartbeatChannel.create(channelName);
                HeartbeatSender heartbeatSender = HeartbeatSender.create(channel, serviceId, sensorId)) {
            configureChannel(channel);
            channel.connect(HeartbeatChannel.CLUSTER_NAME);
            heartbeatSender.start();
            readContinuously();
        } finally {
            role = Role.BACKUP;
        }
    }

    void configureChannel(JChannel channel) {
        if (serviceId == null)
            serviceId = getServiceId(channel.getName());
        channel.addAddressGenerator(() -> ExtendedUUID.randomUUID()
                .put(SENSOR_MEMBER_KEY, new byte[] { 1 }));
        channel.setReceiver(this);
    }

    @Override
    public void viewAccepted(View view) {
        LOG.info("%s: membership changed", serviceId);
        Address monitor = view.getMembers().stream()
                .filter(member -> member instanceof ExtendedUUID id && id.get("monitor") != null)
                .findFirst().orElse(null);
        if (!java.util.Objects.equals(monitorAddress, monitor)) {
            assignment = null;
            role = Role.BACKUP;
        }
        monitorAddress = monitor;
    }

    @Override
    public void receive(Message message) {
        if (monitorAddress != null && monitorAddress.equals(message.getSrc())
                && message instanceof ObjectMessage && message.getObject() instanceof PrimaryAssignment next) {
            acceptAssignment(next);
        }
    }

    void acceptAssignment(PrimaryAssignment next) {
        if (!sensorId.equals(next.sensorId()) || next.expiresAtMillis() <= clock.getAsLong())
            return;
        PrimaryAssignment previous = assignment;
        if (previous != null && next.expiresAtMillis() < previous.expiresAtMillis())
            return;
        assignment = next;
        if (serviceId.equals(next.serviceId()))
            becomePrimary();
        else
            role = Role.BACKUP;
    }

    String serviceId() {
        return serviceId;
    }

    private void becomePrimary() {
        if (role != Role.PRIMARY) {
            // Enable reading on the main thread without blocking the JGroups callback.
            role = Role.PRIMARY;
            LOG.info("%s: became primary", serviceId);
        }
    }

    String getServiceId(String channelName) {
        return channelName + "-" + UUID.randomUUID();
    }

    private void readContinuously() throws InterruptedException {
        long cycleIntervalNanos = TimeUnit.MILLISECONDS.toNanos(CYCLE_INTERVAL_MS);
        while (true) {
            long cycleStartNanos = System.nanoTime();
            readCycle();

            long elapsedNanos = System.nanoTime() - cycleStartNanos;
            long remainingNanos = cycleIntervalNanos - elapsedNanos;
            if (remainingNanos > 0) {
                TimeUnit.NANOSECONDS.sleep(remainingNanos);
            }
        }
    }

    void readCycle() {
        PrimaryAssignment current = assignment;
        if (role == Role.PRIMARY && current != null && serviceId.equals(current.serviceId())
                && clock.getAsLong() < current.expiresAtMillis()) {
            readAndReportDistance();
        } else {
            role = Role.BACKUP;
        }
    }

    private void readAndReportDistance() {
        double distance = Double.parseDouble(sensor.readRawDistance());
        boolean obstacleDetected = detector.detectObstacle(distance);
        LOG.info("%s: read sensor distance=%.2f m, obstacle=%s", serviceId, distance, obstacleDetected);
    }

    private enum Role {
        PRIMARY,
        BACKUP;
    }
}
