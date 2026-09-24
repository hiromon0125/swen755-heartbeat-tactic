# Heartbeat tactic

Uses Java 21, Gradle, and Apache Commons Lang for heartbeat serialization.
Guava and JUnit are also declared in the build configuration.

Start the monitor:

```sh
./start-monitor.sh
```

Start the sensor reader in a separate terminal:

```sh
./start-sensor-reader.sh
```

These launch independent Java entry points: `org.example.Monitor` and
`org.example.SensorReader`, communicating over UDP on Localhost:4445.

The sensor reader simulates two distance sensors and detects obstacles
closer than 5 meters. It sends a heartbeat after successful processing,
targeting a 100 ms cycle. Each reading has a 1% chance of containing
malformed data, causing an intentional unhandled NumberFormatException
that terminates the sensor reader.

The monitor reports one warning after 500 ms without a matching heartbeat,
or after 10 seconds if no initial heartbeat arrives. It keeps listening
and resumes monitoring when matching heartbeats arrive again.

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
