package heartbeat;

import org.jgroups.JChannel;

/** Shared JGroups configuration for heartbeat traffic. */
public final class HeartbeatChannel {
    public static final String CLUSTER_NAME = "heartbeat-tactic";

    private HeartbeatChannel() {}

    public static JChannel create(String name) throws Exception {
        // Explicitly select the bundled UDP stack, including multicast discovery.
        JChannel channel = new JChannel("udp.xml");
        channel.setName(name);
        return channel;
    }
}
