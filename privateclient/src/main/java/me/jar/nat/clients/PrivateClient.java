package me.jar.nat.clients;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import me.jar.nat.codec.Byte2NatMsgDecoder;
import me.jar.nat.codec.LengthContentDecoder;
import me.jar.nat.codec.NatMsg2ByteEncoder;
import me.jar.nat.constants.ProxyConstants;
import me.jar.nat.handler.ProxyHandler;
import me.jar.nat.utils.PlatformUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Map;


/**
 * @Description
 * @Date 2021/4/27-21:31
 */
public class PrivateClient {
    static {
        String path = PrivateClient.class.getProtectionDomain().getCodeSource().getLocation().getPath();
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

    private static final Logger LOGGER = LoggerFactory.getLogger(PrivateClient.class);

    public static void connectProxyServer() throws InterruptedException {
        EventLoopGroup workGroup = new NioEventLoopGroup(1);
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(workGroup).channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000).handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ChannelPipeline pipeline = ch.pipeline();
                    pipeline.addLast("lengthContent", new LengthContentDecoder());
                    pipeline.addLast("decoder", new Byte2NatMsgDecoder());
                    pipeline.addLast("encoder", new NatMsg2ByteEncoder());
                    pipeline.addLast("idleEvt", new IdleStateHandler(60, 30, 0));
                    pipeline.addLast("proxyHandler", new ProxyHandler());
                }
            });
            String serverAgentIp = ProxyConstants.PROPERTY.get(ProxyConstants.FAR_SERVER_IP);
            String serverAgentPort = ProxyConstants.PROPERTY.get(ProxyConstants.FAR_SERVER_PORT);
            int serverAgentPortNum = Integer.parseInt(serverAgentPort);
            Channel channel = bootstrap.connect(serverAgentIp, serverAgentPortNum).channel();
            channel.closeFuture().addListener(future -> {
                LOGGER.info("last client agent close, shutdown workGroup and retry in 10 seconds...");
                workGroup.shutdownGracefully();
                new Thread(() -> {
                    while (true) {
                        try {
                            Thread.sleep(10000L);
                        } catch (InterruptedException interruptedException) {
                            LOGGER.error("sleep 10s was interrupted!");
                        }
                        try {
                            connectProxyServer();
                            break;
                        } catch (InterruptedException e) {
                            LOGGER.error("channel close retry connection failed. detail: " + e.getMessage());
                        }
                    }
                }).start();
            });
        } catch (Exception e) {
            LOGGER.error("===client agent start failed, cause: " + e.getMessage());
            workGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        URL location = PrivateClient.class.getProtectionDomain().getCodeSource().getLocation();
        Map<String, String> propertyMap = PlatformUtil.parseProperty2Map(location);
        if (!propertyMap.isEmpty()) {
            ProxyConstants.PROPERTY.clear();
            ProxyConstants.PROPERTY.putAll(propertyMap);
            connectProxyServer();
        }
    }

}
