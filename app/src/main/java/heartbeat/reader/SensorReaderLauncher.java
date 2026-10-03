package heartbeat.reader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.jgroups.logging.Log;
import org.jgroups.logging.LogFactory;

/** Keeps a primary/backup pair of reader processes available for one sensor. */
public final class SensorReaderLauncher implements AutoCloseable {
    private static final Log LOG = LogFactory.getLog(SensorReaderLauncher.class);
    private final String sensorName;
    private final ProcessStarter starter;
    private final List<Process> children = new ArrayList<>();
    private long backupsRemaining;
    private long nextReader = 1;
    private boolean started;
    private boolean closed;

    record Options(String sensorName, long backups) {
        static Options parse(String[] args) {
            if (args.length > 2) throw usage();
            String name = args.length == 0 || args[0].isBlank()
                    ? "sensor-" + UUID.randomUUID() : args[0];
            long backups = -1;
            if (args.length == 2 && !args[1].isBlank()) {
                try {
                    backups = Long.parseLong(args[1]);
                } catch (NumberFormatException e) {
                    throw usage();
                }
                if (backups < 1) throw usage();
            }
            return new Options(name, backups);
        }

        private static IllegalArgumentException usage() {
            return new IllegalArgumentException(
                    "Usage: SensorReaderLauncher [sensor-name] [backups >= 1; empty = unlimited]");
        }
    }

    @FunctionalInterface
    interface ProcessStarter {
        Process start(List<String> command) throws IOException;
    }

    SensorReaderLauncher(Options options, ProcessStarter starter) {
        sensorName = options.sensorName();
        backupsRemaining = options.backups();
        this.starter = starter;
    }

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        try (SensorReaderLauncher launcher = new SensorReaderLauncher(options,
                command -> new ProcessBuilder(command).inheritIO().start())) {
            Thread shutdown = new Thread(launcher::close, "stop-sensor-readers");
            Runtime.getRuntime().addShutdownHook(shutdown);
            try {
                launcher.run();
            } finally {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdown);
                } catch (IllegalStateException ignored) {
                    // JVM shutdown is already running the cleanup hook.
                }
            }
        }
    }

    void run() throws IOException, InterruptedException {
        try {
            start();
            while (maintain()) {
                TimeUnit.SECONDS.sleep(1);
            }
        } finally {
            close();
        }
    }

    synchronized void start() throws IOException {
        if (started || closed) return;
        started = true;
        launchReader();
        replenish();
    }

    synchronized boolean maintain() throws IOException {
        if (closed) return false;
        children.removeIf(process -> {
            if (process.isAlive()) return false;
            LOG.info("%s: reader exited (%s)", sensorName, process.exitValue());
            return true;
        });
        replenish();
        return !children.isEmpty();
    }

    private void replenish() throws IOException {
        while (children.size() < 2 && backupsRemaining != 0) {
            launchReader();
            if (backupsRemaining > 0) backupsRemaining--;
        }
    }

    private void launchReader() throws IOException {
        String channelName = sensorName + "-reader-" + nextReader++;
        children.add(starter.start(command(channelName)));
        LOG.info("%s: launched %s", sensorName, channelName);
    }

    List<String> command(String channelName) {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        String java = Path.of(System.getProperty("java.home"), "bin", executable).toString();
        return List.of(java, "-cp", System.getProperty("java.class.path"),
                SensorReader.class.getName(), channelName, sensorName);
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        children.forEach(Process::destroy);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        try {
            for (Process child : children) {
                long remaining = Math.max(0, deadline - System.nanoTime());
                if (!child.waitFor(remaining, TimeUnit.NANOSECONDS)) child.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            children.stream().filter(Process::isAlive).forEach(Process::destroyForcibly);
            children.clear();
        }
    }
}
