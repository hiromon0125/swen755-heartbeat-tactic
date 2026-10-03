package heartbeat.reader;

import org.junit.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class SensorReaderLauncherTest {
    private static final class Child extends Process {
        boolean alive = true;
        @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
        @Override public InputStream getInputStream() { return InputStream.nullInputStream(); }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public int waitFor() { alive = false; return 0; }
        @Override public boolean waitFor(long timeout, TimeUnit unit) { return !alive; }
        @Override public int exitValue() {
            if (alive) throw new IllegalThreadStateException();
            return 0;
        }
        @Override public void destroy() { alive = false; }
        @Override public boolean isAlive() { return alive; }
    }

    private static final class Starter implements SensorReaderLauncher.ProcessStarter {
        final List<Child> children = new ArrayList<>();
        final List<List<String>> commands = new ArrayList<>();
        int failAt = -1;
        @Override public Process start(List<String> command) throws IOException {
            if (children.size() == failAt) throw new IOException("Simulated launch failure");
            commands.add(command);
            Child child = new Child();
            children.add(child);
            return child;
        }
    }

    @Test
    public void optionalNameAndEmptyBudgetProduceUniqueNamesAndUnlimitedBackups() {
        var first = SensorReaderLauncher.Options.parse(new String[0]);
        var second = SensorReaderLauncher.Options.parse(new String[] {"", ""});
        assertNotEquals(first.sensorName(), second.sensorName());
        assertEquals(-1, first.backups());
        assertEquals(-1, second.backups());
        assertEquals(3, SensorReaderLauncher.Options.parse(new String[] {"", "3"}).backups());
        assertEquals("front", SensorReaderLauncher.Options.parse(new String[] {"front"}).sensorName());
    }

    @Test
    public void invalidBudgetsAndExtraArgumentsAreRejected() {
        for (String invalid : List.of("0", "-1", "abc", "1.5", "999999999999999999999")) {
            assertThrows(IllegalArgumentException.class,
                    () -> SensorReaderLauncher.Options.parse(new String[] {"front", invalid}));
        }
        assertThrows(IllegalArgumentException.class,
                () -> SensorReaderLauncher.Options.parse(new String[] {"front", "1", "extra"}));
    }

    @Test
    public void startsTwoDistinctReadersAndPreservesNamesWithSpacesAsOneArgument() throws Exception {
        Starter starter = new Starter();
        try (var launcher = new SensorReaderLauncher(
                SensorReaderLauncher.Options.parse(new String[] {"front distance", "1"}), starter)) {
            launcher.start();
            launcher.start();
            assertEquals(2, starter.children.size());
            assertEquals("heartbeat.reader.SensorReader", starter.commands.get(0).get(3));
            assertEquals("front distance", starter.commands.get(0).get(5));
            assertEquals("front distance", starter.commands.get(1).get(5));
            assertNotEquals(starter.commands.get(0).get(4), starter.commands.get(1).get(4));
        }
        assertTrue(starter.children.stream().noneMatch(Process::isAlive));
    }

    @Test
    public void finiteBudgetIncludesInitialBackupAndNeverExceedsTwoLiveReaders() throws Exception {
        Starter starter = new Starter();
        try (var launcher = new SensorReaderLauncher(
                SensorReaderLauncher.Options.parse(new String[] {"front", "2"}), starter)) {
            launcher.start();
            starter.children.get(0).destroy();
            assertTrue(launcher.maintain());
            assertEquals(3, starter.children.size());
            assertEquals(2, starter.children.stream().filter(Process::isAlive).count());
            starter.children.get(1).destroy();
            assertTrue(launcher.maintain());
            assertEquals(3, starter.children.size());
            starter.children.get(2).destroy();
            assertFalse(launcher.maintain());
        }
    }

    @Test
    public void unlimitedBudgetReplacesEitherOrBothFailedReaders() throws Exception {
        Starter starter = new Starter();
        try (var launcher = new SensorReaderLauncher(
                SensorReaderLauncher.Options.parse(new String[] {"front"}), starter)) {
            launcher.start();
            for (int i = 0; i < 5; i++) {
                starter.children.forEach(Process::destroy);
                assertTrue(launcher.maintain());
                assertEquals(2, starter.children.stream().filter(Process::isAlive).count());
            }
            assertEquals(12, starter.children.size());
            launcher.close();
            assertFalse(launcher.maintain());
            assertEquals(12, starter.children.size());
        }
    }

    @Test
    public void launchFailureCleansUpPreviouslyStartedChild() {
        Starter starter = new Starter();
        starter.failAt = 1;
        var launcher = new SensorReaderLauncher(
                SensorReaderLauncher.Options.parse(new String[] {"front", "1"}), starter);
        assertThrows(IOException.class, launcher::run);
        assertFalse(starter.children.get(0).isAlive());
    }
}
