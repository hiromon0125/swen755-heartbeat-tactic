package heartbeat.monitor;

import java.util.function.LongSupplier;
import org.jgroups.JChannel;

/** Test-only bridge for integration tests spanning monitor and reader packages. */
public final class MonitorHarness {
    private final Monitor monitor;

    public MonitorHarness(LongSupplier clock) {
        monitor = new Monitor(clock);
    }

    public void configureChannel(JChannel channel) {
        monitor.configureChannel(channel);
    }

    public void checkHealth() {
        monitor.checkHealth();
    }
}
