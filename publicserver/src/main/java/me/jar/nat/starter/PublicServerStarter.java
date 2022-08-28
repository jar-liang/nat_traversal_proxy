package me.jar.nat.starter;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import me.jar.nat.channel.PairChannel;
import me.jar.nat.codec.Byte2NatMsgDecoder;
import me.jar.nat.codec.LengthContentDecoder;
import me.jar.nat.codec.NatMsg2ByteEncoder;
import me.jar.nat.handler.ServerProxyHandler;
import me.jar.nat.utils.NettyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Description
 * @Date 2021/4/23-23:45
 */
public class PublicServerStarter {
    static {
        String path = PublicServerStarter.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        if (path.contains(".jar")) {
            String osName = System.getProperty("os.name");
            String tempPath;
            if (osName.contains("Windows")) {
                tempPath = path.substring(path.indexOf("/") + 1, path.indexOf(".jar"));
            } else {
                tempPath = path.substring(path.indexOf("/"), path.indexOf(".jar"));
            }
            String targetDirPath = tempPath.substring(0, tempPath.lastIndexOf("/"));
            System.out.println("target path: " + targetDirPath);
            System.setProperty("WORKDIR", targetDirPath);
        } else {
            System.out.println("current path not contain .jar file");
            System.exit(1);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicServerStarter.class);
    private static final URL URL = PublicServerStarter.class.getProtectionDomain().getCodeSource().getLocation();
    private static final Map<String, Map<String, PairChannel>> GLOBAL_CHANNEL_MAP = new ConcurrentHashMap<>();

    public static void main(String[] args) {
//        Map<String, String> propertyMap = PlatformUtil.parseProperty2Map(URL);
//        if (!propertyMap.isEmpty()) {
//            ProxyConstants.PROPERTY.clear();
//            ProxyConstants.PROPERTY.putAll(propertyMap);
//        }
//        if (ProxyConstants.PROPERTY.containsKey(ProxyConstants.SERVER_LISTEN_PORT)) {
//            String port = ProxyConstants.PROPERTY.get(ProxyConstants.SERVER_LISTEN_PORT);
            try {
//                int portNum = Integer.parseInt(port.trim()); todo
                int portNum = 23333;
                new PublicServerStarter().runForProxy(portNum);
            } catch (NumberFormatException e) {
                LOGGER.error("===Failed to parse number, property setting may be wrong.", e);
            }
//        } else {
//            LOGGER.error("===Failed to get port from property, starting server failed.");
//        }

    }

    public void runForProxy(int port) {
        ChannelInitializer<SocketChannel> channelInitializer = new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ChannelPipeline pipeline = ch.pipeline();
                // 添加与客户端交互的handler
                pipeline.addLast("lengthContent", new LengthContentDecoder());
                pipeline.addLast("decoder", new Byte2NatMsgDecoder());
                pipeline.addLast("encoder", new NatMsg2ByteEncoder());
                pipeline.addLast("idleEvt", new IdleStateHandler(60, 30, 0));
                pipeline.addLast("proxyControlHandler", new ServerProxyHandler(GLOBAL_CHANNEL_MAP));
            }
        };
        NettyUtil.starServer(port, channelInitializer);
    }

    public static URL getUrl() {
        return URL;
    }
}
