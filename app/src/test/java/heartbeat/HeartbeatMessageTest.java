package heartbeat;

import org.junit.Test;
import static org.junit.Assert.*;

public class HeartbeatMessageTest {
    @Test
    public void serializationPreservesOkAndErrorMessages() {
        for (HeartbeatMessage original : new HeartbeatMessage[] {
                HeartbeatMessage.createOk("reader-1", "front"), HeartbeatMessage.createError("reader-2")}) {
            HeartbeatMessage decoded = HeartbeatMessage.fromByteArray(original.toByteArray());
            assertEquals(original.getServiceId(), decoded.getServiceId());
            assertEquals(original.getSensorId(), decoded.getSensorId());
            assertEquals(original.getStatus(), decoded.getStatus());
            assertEquals(original.getTimestamp(), decoded.getTimestamp());
        }
    }

    @Test(expected = RuntimeException.class)
    public void malformedPayloadIsRejected() {
        HeartbeatMessage.fromByteArray(new byte[] {1, 2, 3});
    }
}
