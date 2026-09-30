package heartbeat.reader;

public class SensorState {
    private long readingsProcessed;
    private double lastDistance;
    private boolean lastObstacleDetected;
    
    public SensorState() {
        this.readingsProcessed = 0;
    }

    public void update(double distance, boolean obstacleDetected) {
        readingsProcessed++;
        lastDistance = distance;
        lastObstacleDetected = obstacleDetected;
    }

    public void restore(long readingsProcessed, double lastDistance, boolean lastObstacleDetected) {
        this.readingsProcessed = readingsProcessed;
        this.lastDistance = lastDistance;
        this.lastObstacleDetected = lastObstacleDetected;
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
