package heartbeat.monitor;

import heartbeat.HeartbeatMessage;
import heartbeat.HeartbeatChannel;

import org.jgroups.JChannel;
import org.jgroups.logging.Log;
import org.jgroups.logging.LogFactory;
import org.jgroups.Message;
import org.jgroups.Receiver;
import org.jgroups.ObjectMessage;
import org.jgroups.util.ExtendedUUID;
import java.util.function.LongSupplier;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Entry point for the monitor process. */
public final class Monitor {
    private static final Log LOG = LogFactory.getLog(Monitor.class);

    private long monitorStartNanos;
    private boolean startupFailureReported;
    private final Map<String, ServiceHealth> services = new HashMap<>();

    private static final class ServiceHealth {
        private long lastHeartbeatNanos;
        private boolean failureReported;
    }

    private JChannel channel;
    private final FailoverCoordinator coordinator;

    private Monitor() {
        this(System::currentTimeMillis);
    }

    Monitor(LongSupplier clock) {
        coordinator = new FailoverCoordinator(clock, assignment -> {
            try {
                channel.send(new ObjectMessage(null, assignment));
            } catch (Exception e) {
                LOG.error("monitor: assignment failed", e);
            }
        });
    }

    public static void main(String[] args) {
        Monitor monitor = new Monitor();
        monitor.run();
    }

    public void run() {
        try (JChannel channel = HeartbeatChannel.create("monitor")) {
            configureChannel(channel);
            monitorStartNanos = System.nanoTime();
            channel.connect(HeartbeatChannel.CLUSTER_NAME);
            while (!Thread.currentThread().isInterrupted()) {
                checkHealth();
                TimeUnit.MILLISECONDS.sleep(50);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            throw new IllegalStateException("Monitor communication failed", e);
        }
    }

    void configureChannel(JChannel channel) {
        this.channel = channel;
        channel.addAddressGenerator(() -> ExtendedUUID.randomUUID().put("monitor", new byte[] {1}));
        channel.setReceiver(new Receiver() {
            @Override
            public void receive(Message packet) {
                if (packet instanceof ObjectMessage) return;
                try {
                    byte[] payload = Arrays.copyOfRange(packet.getArray(), packet.getOffset(),
                            packet.getOffset() + packet.getLength());
                    HeartbeatMessage message = HeartbeatMessage.fromByteArray(payload);
                    if (message.getServiceId() != null && !message.getServiceId().isBlank()) {
                        updateTime(message.getServiceId());
                        coordinator.heartbeat(message);
                        LOG.info("%s: received heartbeat", message.getServiceId());
                    }
                } catch (RuntimeException e) {
                    LOG.warn("monitor: invalid heartbeat", e);
                }
            }
        });
    }

    void checkHealth() {
        reportFailureIfNeeded();
        coordinator.check();
    }

    private synchronized void reportFailureIfNeeded() {
        long now = System.nanoTime();
        if (services.isEmpty() && !startupFailureReported
                && now - monitorStartNanos >= TimeUnit.SECONDS.toNanos(10)) {
            LOG.warn("monitor: startup timeout");
            startupFailureReported = true;
        }
        services.forEach((serviceId, health) -> {
            if (!health.failureReported
                    && now - health.lastHeartbeatNanos >= TimeUnit.MILLISECONDS.toNanos(500)) {
                LOG.warn("%s: heartbeat timeout", serviceId);
                health.failureReported = true;
            }
        });
    }

    private synchronized void updateTime(String serviceId) {
        ServiceHealth health = services.computeIfAbsent(serviceId, id -> new ServiceHealth());
        health.lastHeartbeatNanos = System.nanoTime();
        health.failureReported = false;
    }
}
