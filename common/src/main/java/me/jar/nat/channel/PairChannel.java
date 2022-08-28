package me.jar.nat.channel;

import io.netty.channel.Channel;

import java.util.Objects;

public class PairChannel {
    private Channel agentChannel;
    private Channel portalChannel;

    public Channel getAgentChannel() {
        return agentChannel;
    }

    public void setAgentChannel(Channel agentChannel) {
        this.agentChannel = agentChannel;
    }

    public Channel getPortalChannel() {
        return portalChannel;
    }

    public void setPortalChannel(Channel portalChannel) {
        this.portalChannel = portalChannel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PairChannel that = (PairChannel) o;
        return Objects.equals(agentChannel, that.agentChannel) && Objects.equals(portalChannel, that.portalChannel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(agentChannel, portalChannel);
    }

    @Override
    public String toString() {
        return "PairChannel{" +
                "agentChannel=" + agentChannel +
                ", portalChannel=" + portalChannel +
                '}';
    }
}
