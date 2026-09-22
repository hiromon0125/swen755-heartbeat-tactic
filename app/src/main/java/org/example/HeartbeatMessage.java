package org.example;

import org.apache.commons.lang3.SerializationUtils;

import java.io.Serializable;
import java.time.Instant;

/** Message sent by {@link org.example.HeartbeatSender} **/
public class HeartbeatMessage implements Serializable {
    private String serviceId;
    private Instant timestamp;
    private Status status;

    public String getServiceId() {
        return serviceId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Status getStatus() {
        return status;
    }

    public enum Status { OK, ERROR }

    protected HeartbeatMessage(
            String serviceId,
            Instant timestamp,
            Status status
    ) {
        this.serviceId = serviceId;
        this.timestamp = timestamp;
        this.status = status;
    }

    public static HeartbeatMessage createOk(String serviceId) {
        return new HeartbeatMessage(serviceId, Instant.now(), Status.OK);
    }

    public static HeartbeatMessage createError(String serviceId) {
        return new HeartbeatMessage(serviceId, Instant.now(), Status.ERROR);
    }

    public static HeartbeatMessage fromByteArray(byte[] bytes) {
        return SerializationUtils.deserialize(bytes);
    }

    public byte[] toByteArray() {
        return SerializationUtils.serialize(this);
    }
}
