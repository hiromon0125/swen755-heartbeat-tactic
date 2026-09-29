package heartbeat.monitor;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/** Access private timing state to exercise timeouts without slow wall-clock sleeps. */
public class MonitorTest {
    private Monitor monitor() throws Exception {
        Constructor<Monitor> constructor = Monitor.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void heartbeat(Monitor monitor, String id) throws Exception {
        Method method = Monitor.class.getDeclaredMethod("updateTime", String.class);
        method.setAccessible(true);
        method.invoke(monitor, id);
    }

    private void check(Monitor monitor) throws Exception {
        Method method = Monitor.class.getDeclaredMethod("reportFailureIfNeeded");
        method.setAccessible(true);
        method.invoke(monitor);
    }

    @Test
    public void startupTimeoutOnlyAfterGracePeriod() throws Exception {
        Monitor monitor = monitor();
        set(monitor, "monitorStartNanos", System.nanoTime());
        check(monitor);
        assertEquals(false, field(monitor, "startupFailureReported"));
        set(monitor, "monitorStartNanos", System.nanoTime() - TimeUnit.SECONDS.toNanos(11));
        check(monitor);
        assertEquals(true, field(monitor, "startupFailureReported"));
    }

    @Test
    public void tracksTimeoutAndRecoveryIndependentlyPerService() throws Exception {
        Monitor monitor = monitor();
        heartbeat(monitor, "reader-a");
        heartbeat(monitor, "reader-b");
        Map<?, ?> services = (Map<?, ?>) field(monitor, "services");
        Object first = services.get("reader-a");
        Object second = services.get("reader-b");
        set(first, "lastHeartbeatNanos", System.nanoTime() - TimeUnit.SECONDS.toNanos(1));
        check(monitor);
        assertEquals(true, field(first, "failureReported"));
        assertEquals(false, field(second, "failureReported"));
        heartbeat(monitor, "reader-a");
        check(monitor);
        assertEquals(false, field(first, "failureReported"));
        assertEquals(2, services.size());
    }
}
