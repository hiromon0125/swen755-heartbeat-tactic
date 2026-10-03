package heartbeat.reader;

import org.junit.Test;
import static org.junit.Assert.*;

public class ObstacleDetectorTest {
    @Test
    public void detectsOnlyDistancesBelowFiveMeters() {
        ObstacleDetector detector = new ObstacleDetector();
        assertTrue(detector.detectObstacle(0));
        assertTrue(detector.detectObstacle(4.99));
        assertFalse(detector.detectObstacle(5));
        assertFalse(detector.detectObstacle(20));
    }
}
