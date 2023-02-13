package me.jar.portal.start;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import me.jar.nat.constants.ProxyConstants;
import me.jar.nat.utils.NettyUtil;
import me.jar.nat.utils.PlatformUtil;
import me.jar.portal.handler.ConnectServerHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.Map;


/**
 * @Description
 * @Date 2021/4/27-21:31
 */
public class PortalStarter {
    static {
        File file = new File(".");
        System.setProperty("WORKDIR", file.getAbsolutePath());
    }
    private static final Logger LOGGER = LoggerFactory.getLogger(PortalStarter.class);

    private final int port;

    public PortalStarter(int port) {
        this.port = port;
    }

    public void run() {
        ChannelInitializer<SocketChannel> channelInitializer = new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ch.pipeline().addLast("connectFar", new ConnectServerHandler());
            }
        };
        NettyUtil.starServer(port, channelInitializer, false);
    }

    public static void main(String[] args) {
        URL location = PortalStarter.class.getProtectionDomain().getCodeSource().getLocation();
        Map<String, String> propertyMap = PlatformUtil.parseProperty2Map(location);
        if (!propertyMap.isEmpty()) {
            ProxyConstants.PROPERTY.clear();
            ProxyConstants.PROPERTY.putAll(propertyMap);

            if (ProxyConstants.PROPERTY.containsKey(ProxyConstants.KEY_NAME_PORT)) {
                String port = ProxyConstants.PROPERTY.get(ProxyConstants.KEY_NAME_PORT);
                try {
                    int portNum = Integer.parseInt(port.trim());
                    new PortalStarter(portNum).run();
                } catch (NumberFormatException e) {
                    LOGGER.error("===Failed to parse number, property setting may be wrong.", e);
                }
            } else {
                LOGGER.error("===Failed to get port from property, starting server failed.");
            }
        }
    }
}
