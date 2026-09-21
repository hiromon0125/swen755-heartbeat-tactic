# Heartbeat tactic

Requires Java 21. The Gradle wrapper downloads Gradle and dependencies on the first run.

Start the monitor:

```sh
./start-monitor.sh
```

Start the sensor reader in a separate terminal:

```sh
./start-sensor-reader.sh
```

These launch independent Java entry points: `org.example.Monitor` and
`org.example.SensorReader`. Both currently print a startup message and exit;
add the monitoring and sensor-reading loops in their respective `main` methods.

The scripts work from any working directory and forward Gradle options. To pass
application arguments, use `./start-monitor.sh --args="..."` or
`./start-sensor-reader.sh --args="..."`.

You can also run the tasks directly (including on Windows with `gradlew.bat`):

```sh
./gradlew :app:runMonitor
./gradlew :app:runSensorReader
./gradlew :app:build
```

The original sample `:app:run` task remains available.
