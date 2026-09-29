package heartbeat;

import java.io.Serializable;

/** A short-lived permission to read one sensor, issued by the monitor. */
public record PrimaryAssignment(String sensorId, String serviceId, long expiresAtMillis) implements Serializable {}
