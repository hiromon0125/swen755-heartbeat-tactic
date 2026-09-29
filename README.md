# Heartbeat tactic

Uses Java 21, Gradle, JGroups 5.4.12.Final, and Apache Commons Lang for heartbeat serialization.
Guava and JUnit are also declared in the build configuration.

Start the monitor:

```sh
./start-monitor.sh
```

Start the sensor reader in a separate terminal:

```sh
./start-sensor-reader.sh
```

Pass the channel name followed by a sensor ID (defaults: `sensor-reader`, `sensor-1`).
Readers with the same sensor ID form a primary/backup group. Launch each command
in a separate terminal after starting the monitor:

```sh
./start-sensor-reader.sh --args="front-reader front"
./start-sensor-reader.sh --args="front-backup front"
./start-sensor-reader.sh --args="rear-reader rear"
./start-sensor-reader.sh --args="rear-backup rear"
```

These commands create two independent sensor groups with two readers each. The
first healthy reader discovered by the monitor becomes primary for that sensor;
the other remains backup. Processes must be launched explicitly: the application
does not create backup processes. More than one backup per sensor is supported.
Each process generates a service ID from its channel name plus a random UUID.
The ID is logged at startup and reused for that run.

These launch independent Java entry points: `heartbeat.monitor.Monitor` and
`heartbeat.reader.SensorReader`. Heartbeat messages are broadcast through JGroups in
cluster `heartbeat-tactic`, using its bundled `udp.xml` protocol stack (UDP
transport and multicast discovery). Both processes must use the same network
interface and have UDP multicast available. The former raw UDP port 4445 is no
longer used. Sensor readings remain local to the sensor reader.

JGroups manages discovery, membership, and reliable delivery. The application
still uses its own heartbeat messages and timeout checks, independently of
JGroups membership notifications.

Each sensor reader simulates one distance sensor and detects obstacles
closer than 5 meters. HeartbeatSender independently schedules heartbeats
every 100 ms. Each sensor reading has a 1% chance of containing malformed
data, causing an unhandled NumberFormatException. As processing exits,
try-with-resources stops the heartbeat scheduler and closes the JGroups channel,
allowing the sensor-reader process to terminate.

The monitor discovers service IDs from incoming heartbeats and tracks each one
independently. It reports one warning per service after 500 ms without a heartbeat,
or a startup warning after 10 seconds if no service has sent a heartbeat. It keeps
listening and resumes monitoring a service when its heartbeats arrive again.

The scripts work from any working directory and forward Gradle options. To pass
application arguments, use `./start-monitor.sh --args="..."` or
`./start-sensor-reader.sh --args="..."`.

You can also run the tasks directly (including on Windows with `gradlew.bat`):

```sh
./gradlew :app:runMonitor
./gradlew :app:runSensorReader
./gradlew :app:build
```

The `:app:run` task also starts the sensor reader.

Sensor readers implement JGroups `Receiver`. Membership views identify the
monitor; the monitor issues primary assignments through the same JGroups UDP
stack. Both primary and backup readers send heartbeats every 100 ms. Only the
reader with a valid assignment reads its sensor.

The monitor checks heartbeat health every 50 ms. After 500 ms without a primary's
heartbeat, it selects a healthy backup for the same sensor. It waits for the
previous 500 ms assignment to expire before authorizing the replacement, so
promotion can take up to roughly one second after the last heartbeat. A recovered
old primary stays backup while the replacement is healthy. If no healthy backup
exists, that sensor has no active primary until a healthy reader is available.

Assignments expire after 500 ms unless renewed. Readers stop starting new reads
when their assignment expires or the monitor leaves; an in-progress read may
finish. This assumes one monitor and synchronized wall clocks across processes.
It is a classroom failover mechanism, not a distributed fencing or consensus
protocol. Monitor redundancy and arbitrary clock skew/partitions are not covered.
Sensor exceptions still close the heartbeat sender and channel.

Run the test package in `app/src/test/java/heartbeat`:

```sh
./gradlew :app:test
```

The suite covers serialization, obstacle thresholds, generated IDs, argument
validation, primary-only readings, assignment expiry, independent sensor groups,
healthy-backup selection, recovery, and per-service timeout reporting. Deterministic
tests use an injected clock and fixed sensor readings. UDP integration tests verify
heartbeats from primary and backup readers, sender shutdown, and monitor-triggered
promotion with four readers while all JGroups members remain connected. Local UDP
multicast is required. Full process crashes and network partitions are not simulated.

The HTML test report is generated at `app/build/reports/tests/test/index.html`.

Source packages under `app/src/main/java/heartbeat`:

- `monitor/`: monitor and failover coordination.
- `reader/`: sensor reader, simulated sensor, obstacle detection, and heartbeat sender.
- `heartbeat` root: shared messages and JGroups channel configuration.

Tests mirror these packages under `app/src/test/java/heartbeat`.
