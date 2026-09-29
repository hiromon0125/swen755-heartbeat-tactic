package heartbeat.reader;

import heartbeat.HeartbeatMessage;

import org.jgroups.BytesMessage;
import org.jgroups.JChannel;
import org.jgroups.logging.Log;
import org.jgroups.logging.LogFactory;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/** Sends periodic heartbeats after start() is called**/
public class HeartbeatSender implements AutoCloseable {
    private static final Log LOG = LogFactory.getLog(HeartbeatSender.class);
    private final JChannel channel;
    private final String serviceId;
    private final String sensorId;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();

    private static final long SENDING_INTERVAL_MS = 100;

    private HeartbeatSender(JChannel channel, String serviceId, String sensorId) {
        this.channel = channel;
        this.serviceId = serviceId;
        this.sensorId = sensorId;
    }

    public static HeartbeatSender create(JChannel channel, String serviceId) {
        return create(channel, serviceId, "sensor-1");
    }

    public static HeartbeatSender create(JChannel channel, String serviceId, String sensorId) {
        return new HeartbeatSender(channel, serviceId, sensorId);
    }

    /**
     * Starts sending heartbeat messages at a fixed interval defined by SENDING_INTERVAL_MS.
     *
     *
     */
    public void start() {
        scheduler.scheduleAtFixedRate(
                () ->  {
                    try {
                        sendMessage();
                    } catch (Exception e) {
                        LOG.error(serviceId + ": heartbeat send failed", e);
                    }
                },
                0,
                SENDING_INTERVAL_MS,
                TimeUnit.MILLISECONDS
                );

    }


    public void sendMessage() throws Exception {
        HeartbeatMessage message = HeartbeatMessage.createOk(this.serviceId, this.sensorId);
        byte[] messageBytes = message.toByteArray();
        // A null destination broadcasts to the heartbeat cluster over JGroups UDP.
        channel.send(new BytesMessage(null, messageBytes));
        LOG.info("%s: sent heartbeat", serviceId);
    }

    @Override
    public void close() {
        scheduler.close();

    }

}
