package heartbeat.reader;

import java.io.Serializable;

/* Creates a snapshot of the state to be sent from primary reader to the backup
 */
public class Checkpoint implements Serializable{
    
    private final String sensorId;
    private final String serviceId;
    private final long readingsProcessed;
    private final double lastDistance;
    private final boolean lastObstacleDetected;

    public Checkpoint (String sensorId, String serviceId, long readingsProcessed, double lastDistance, boolean lastObstacleDetected) {
            this.sensorId = sensorId;
            this.serviceId = serviceId;
            this.readingsProcessed = readingsProcessed;
            this.lastDistance = lastDistance;
            this.lastObstacleDetected = lastObstacleDetected;
        }

    public String getSensorId() {
        return sensorId;
    }

    public String getServiceId() {
        return serviceId;
    }

    public long getReadingsProcessed() {
        return readingsProcessed;
    }

    public double getLastDistance() {
        return lastDistance;
    }

    public boolean isLastObstacleDetected() {
        return lastObstacleDetected;
    }
}
