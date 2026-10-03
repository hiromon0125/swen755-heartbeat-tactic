package heartbeat.reader;

import java.util.Random;

/** Simulates a distance sensor that occasionally returns malformed readings. */
public class Sensor {
    private static final double CORRUPTION_PROBABILITY = 0.01;
    private final Random rand = new Random();

    /** Returns a distance in meters, or a malformed value to simulate corruption. */
    String readRawDistance() {
        if (rand.nextDouble() < CORRUPTION_PROBABILITY) {
            return "7.x";
        }
        return Double.toString(rand.nextDouble() * 20);
    }
}
