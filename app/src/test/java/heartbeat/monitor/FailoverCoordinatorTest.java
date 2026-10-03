package heartbeat.monitor;

import heartbeat.HeartbeatMessage;
import heartbeat.PrimaryAssignment;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class FailoverCoordinatorTest {
    private final AtomicLong clock = new AtomicLong(1000);
    private final Map<String, PrimaryAssignment> assignments = new HashMap<>();
    private final FailoverCoordinator coordinator = new FailoverCoordinator(clock::get,
            assignment -> assignments.put(assignment.sensorId(), assignment));

    private void heartbeat(String sensor, String service) {
        coordinator.heartbeat(HeartbeatMessage.createOk(service, sensor));
    }

    @Test
    public void separateSensorsHaveIndependentPrimariesAndBackups() {
        heartbeat("front", "front-primary");
        heartbeat("front", "front-backup");
        heartbeat("rear", "rear-primary");
        heartbeat("rear", "rear-backup");
        coordinator.check();
        assertEquals("front-primary", assignments.get("front").serviceId());
        assertEquals("rear-primary", assignments.get("rear").serviceId());
        clock.set(1500);
        heartbeat("front", "front-backup");
        heartbeat("rear", "rear-primary");
        heartbeat("rear", "rear-backup");
        coordinator.check();
        assertEquals("front-backup", assignments.get("front").serviceId());
        assertEquals("rear-primary", assignments.get("rear").serviceId());
    }

    @Test
    public void waitsForOldLeaseBeforePromotingLiveBackup() {
        heartbeat("front", "primary");
        heartbeat("front", "backup");
        coordinator.check();
        clock.set(1400);
        coordinator.check(); // Last renewal before the primary's heartbeat expires.
        assertEquals(1900, assignments.get("front").expiresAtMillis());
        clock.set(1500);
        heartbeat("front", "backup");
        coordinator.check();
        assertEquals("primary", assignments.get("front").serviceId());
        clock.set(1900);
        coordinator.check();
        assertEquals("backup", assignments.get("front").serviceId());
    }

    @Test
    public void deadBackupIsNotPromotedAndOtherSensorsAreNotBorrowed() {
        heartbeat("front", "primary");
        heartbeat("front", "dead-backup");
        coordinator.check();
        assignments.clear();
        clock.set(1500);
        heartbeat("rear", "rear-reader");
        coordinator.check();
        assertFalse(assignments.containsKey("front"));
        assertEquals("rear-reader", assignments.get("rear").serviceId());
    }

    @Test
    public void recoveringOldPrimaryDoesNotDisplaceReplacement() {
        heartbeat("front", "old");
        heartbeat("front", "backup");
        coordinator.check();
        clock.set(1500);
        heartbeat("front", "backup");
        coordinator.check();
        heartbeat("front", "old");
        coordinator.check();
        assertEquals("backup", assignments.get("front").serviceId());
    }

    @Test
    public void backupFailureDoesNotDemoteHealthyPrimary() {
        heartbeat("front", "primary");
        heartbeat("front", "backup");
        coordinator.check();
        clock.set(1500);
        heartbeat("front", "primary");
        coordinator.check();
        assertEquals("primary", assignments.get("front").serviceId());
    }

    @Test
    public void noReadersMeansNoAssignments() {
        coordinator.check();
        assertTrue(assignments.isEmpty());
    }
}
