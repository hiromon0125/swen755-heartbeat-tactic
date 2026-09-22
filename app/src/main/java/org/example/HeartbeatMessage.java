package org.example;

import java.time.Instant;

public class HeartbeatMessage {
    private String serviceId;
    private Instant timestamp;
    private Status status;

    public enum Status { OK, ERROR }

    protected HeartbeatMessage(
            String serviceId,
            Status status
    ) {
        this.serviceId = serviceId;
        this.status = status;
    }

    public static HeartbeatMessage createOk(String serviceId) {
        return new HeartbeatMessage(serviceId, Status.OK);
    }

    public static HeartbeatMessage createError(String serviceId) {
        return new HeartbeatMessage(serviceId, Status.ERROR);
    }
}
