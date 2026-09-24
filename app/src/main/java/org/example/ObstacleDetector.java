package org.example;

public class ObstacleDetector {

    private final static double DISTANCE_THRESHOLD = 5.0; // In meters
    
    
    
    /**
     * Detects if there is an obstacle based on the distance measured by the sensor.
     * @param distance
     * @return true if an obstacle is detected, false otherwise
     */
    public boolean detectObstacle(double distance) {
        return distance < DISTANCE_THRESHOLD;
    }
    
    
    
    
    
    
}
