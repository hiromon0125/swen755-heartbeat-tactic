package heartbeat.monitor;

import heartbeat.HeartbeatMessage;
import heartbeat.PrimaryAssignment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Single-monitor primary selection, independently for each sensor. */
final class FailoverCoordinator {
    static final long HEARTBEAT_TIMEOUT_MS = 500;
    static final long LEASE_MS = 500;
    private final LongSupplier clock;
    private final Consumer<PrimaryAssignment> assignments;
    private final Map<String, Map<String, Health>> sensors = new LinkedHashMap<>();
    private final Map<String, PrimaryAssignment> primaries = new LinkedHashMap<>();

    private static final class Health {
        long lastHeartbeat;
        boolean ok;
    }

    FailoverCoordinator(LongSupplier clock, Consumer<PrimaryAssignment> assignments) {
        this.clock = clock;
        this.assignments = assignments;
    }

    synchronized void heartbeat(HeartbeatMessage message) {
        if (message.getSensorId() == null || message.getSensorId().isBlank()
                || message.getServiceId() == null || message.getServiceId().isBlank()) {
            return;
        }
        Health health = sensors.computeIfAbsent(message.getSensorId(), id -> new LinkedHashMap<>())
                .computeIfAbsent(message.getServiceId(), id -> new Health());
        health.lastHeartbeat = clock.getAsLong();
        health.ok = message.getStatus() == HeartbeatMessage.Status.OK;
    }

    synchronized void check() {
        long now = clock.getAsLong();
        sensors.forEach((sensorId, services) -> {
            PrimaryAssignment current = primaries.get(sensorId);
            String primary = current == null ? null : current.serviceId();
            if (primary == null || !alive(services.get(primary), now)) {
                // Do not authorize a replacement while the previous lease can still be valid.
                if (current != null && now < current.expiresAtMillis()) return;
                primary = services.entrySet().stream()
                        .filter(entry -> alive(entry.getValue(), now))
                        .map(entry -> entry.getKey()).findFirst().orElse(null);
            }
            if (primary == null) {
                primaries.remove(sensorId);
                return;
            }
            PrimaryAssignment next = new PrimaryAssignment(sensorId, primary, now + LEASE_MS);
            primaries.put(sensorId, next);
            assignments.accept(next);
        });
    }

    private boolean alive(Health health, long now) {
        return health != null && health.ok && now - health.lastHeartbeat < HEARTBEAT_TIMEOUT_MS;
    }
}
