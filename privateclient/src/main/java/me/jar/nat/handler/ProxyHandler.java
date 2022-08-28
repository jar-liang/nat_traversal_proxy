package me.jar.nat.handler;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import me.jar.nat.channel.PairChannel;
import me.jar.nat.codec.Byte2NatMsgDecoder;
import me.jar.nat.codec.LengthContentDecoder;
import me.jar.nat.codec.NatMsg2ByteEncoder;
import me.jar.nat.constants.NatMsgType;
import me.jar.nat.constants.ProxyConstants;
import me.jar.nat.exception.NatProxyException;
import me.jar.nat.message.NatMsg;
import me.jar.nat.utils.CommonHandler;
import me.jar.nat.utils.NettyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Description
 * @Date 2021/4/27-21:50
 */
public class ProxyHandler extends CommonHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyHandler.class);
    private static final Map<String, PairChannel> PAIR_CHANNEL_MAP = new ConcurrentHashMap<>();
//    private final String password;
    private String proxyType;
    private Channel targetChannel;

    public ProxyHandler() {
//        String password = ProxyConstants.PROPERTY.get(ProxyConstants.PROPERTY_NAME_KEY);
//        if (password == null || password.length() == 0) {
//            throw new IllegalArgumentException("Illegal key from property");
//        }
//        this.password = password;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof NatMsg) {
            NatMsg natMsg = (NatMsg) msg;
            NatMsgType type = natMsg.getType();
            Map<String, Object> metaData = natMsg.getMetaData();
            switch (type) {
                case REGISTER_RESULT:
                    if ("1".equals(metaData.get("result"))) {
                        LOGGER.info("register to transfer proxy server successfully");
                    } else {
                        LOGGER.error("register failed, reason: " + metaData.get("reason"));
                        ctx.close();
                    }
                    break;
                case CONNECT:
                    connectTarget(ctx, metaData);
                    break;
                case KEEPALIVE:
                    break;
                default:
                    throw new NatProxyException("message type is not one of REGISTER_RESULT/CONNECT/KEEPALIVE, unknown type: " + type.getType());
            }
        }
    }

    private void connectTarget(ChannelHandlerContext ctx, Map<String, Object> metaData) {
        System.out.println("建立连接，收到CONNECT消息");
        String channelId = String.valueOf(metaData.get(ProxyConstants.CHANNEL_ID));
        EventLoopGroup workGroup = new NioEventLoopGroup(1);
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workGroup).channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.AUTO_READ, false)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000).handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("clientHandler", new ClientHandler(ctx.channel(), channelId, PAIR_CHANNEL_MAP, true));
            }
        });
//        String targetIp = ProxyConstants.PROPERTY.get(ProxyConstants.TARGET_IP);
//        String targetPort = ProxyConstants.PROPERTY.get(ProxyConstants.TARGET_PORT);
        String targetIp = String.valueOf(metaData.get(ProxyConstants.TARGET_IP));
        String targetPort = String.valueOf(metaData.get(ProxyConstants.TARGET_PORT));
        System.out.println("连接的目标ip:" + targetIp + ", port: " + targetPort);
        try {
            int targetPortNum = Integer.parseInt(targetPort);
            bootstrap.connect(targetIp, targetPortNum).addListener((ChannelFutureListener) connectTargetFuture -> {
                if (connectTargetFuture.isSuccess()) {
                    System.out.println("建立连接，步骤4，已连接上目标服务器");
                    PairChannel pairChannel = new PairChannel();
                    pairChannel.setPortalChannel(connectTargetFuture.channel());
                    PAIR_CHANNEL_MAP.put(channelId, pairChannel);
                    EventLoopGroup workGroupProxy = new NioEventLoopGroup(1);
                    try {
                        Bootstrap bootstrapProxy = new Bootstrap();
                        bootstrapProxy.group(workGroupProxy).channel(NioSocketChannel.class)
                                .option(ChannelOption.SO_KEEPALIVE, true)
                                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000).handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ChannelPipeline pipeline = ch.pipeline();
                                pipeline.addLast("lengthContent", new LengthContentDecoder());
                                pipeline.addLast("decoder", new Byte2NatMsgDecoder());
                                pipeline.addLast("encoder", new NatMsg2ByteEncoder());
                                pipeline.addLast("clientHandler", new ClientHandler(ctx.channel(), channelId, PAIR_CHANNEL_MAP, false));
                            }
                        });
//                        String serverAgentIp = ProxyConstants.PROPERTY.get(ProxyConstants.FAR_SERVER_IP);
//                        String serverClientPort = ProxyConstants.PROPERTY.get(ProxyConstants.SERVER_CLIENT_PORT);
                        String serverAgentIp = "127.0.0.1";
                        String serverClientPort = "15555";
                        int serverClientPortNum = Integer.parseInt(serverClientPort);
                        bootstrapProxy.connect(serverAgentIp, serverClientPortNum).addListener((ChannelFutureListener) connectProxyFuture -> {
                            if (connectProxyFuture.isSuccess()) {
                                System.out.println("建立连接，步骤5，已连接上中间服务器");
                                pairChannel.setAgentChannel(connectProxyFuture.channel());
                                connectTargetFuture.channel().pipeline().fireUserEventTriggered(connectProxyFuture.channel());
                                connectProxyFuture.channel().pipeline().fireUserEventTriggered(connectTargetFuture.channel());
                                NatMsg natMsg = new NatMsg();
                                natMsg.setType(NatMsgType.CONNECT);
                                Map<String, Object> metaDataSend = new HashMap<>(2);
                                metaDataSend.put(ProxyConstants.CHANNEL_ID, channelId);
                                metaDataSend.put(ProxyConstants.ROLE, ProxyConstants.ROLE_AGENT);
                                natMsg.setMetaData(metaDataSend);
                                System.out.println("建立连接，步骤6，发送CONNECT回去");
                                connectProxyFuture.channel().writeAndFlush(natMsg).addListener((ChannelFutureListener) futureMsgSend -> {
                                    if (futureMsgSend.isSuccess()) {
                                        connectTargetFuture.channel().read();
                                    } else {
                                        futureMsgSend.channel().close();
                                        connectTargetFuture.channel().close();
                                        sendDisconnectMsgAndRemoveChannel(ctx, channelId);
                                    }
                                });
                            }
                        });
                    } catch (Exception e) {
                        LOGGER.error("===after connecting target, now connect to server failed, cause: " + e.getMessage());
                        connectTargetFuture.channel().close();
                        sendDisconnectMsgAndRemoveChannel(ctx, channelId);
                        workGroupProxy.shutdownGracefully();
                    }
                } else {
                    LOGGER.error("===Failed to connect to target server! host: " + targetIp + " , port: " + targetPortNum);
                    sendDisconnectMsgAndRemoveChannel(ctx, channelId);
                }
            });
        } catch (Exception e) {
            LOGGER.error("===Failed to connect to target server! cause: " + e.getMessage());
            sendDisconnectMsgAndRemoveChannel(ctx, channelId);
        }
    }

    private void sendDisconnectMsgAndRemoveChannel(ChannelHandlerContext ctx, String channelId) {
        NatMsg disconnectMsg = new NatMsg();
        disconnectMsg.setType(NatMsgType.DISCONNECT);
        Map<String, Object> failMetaData = new HashMap<>(1);
        failMetaData.put(ProxyConstants.CHANNEL_ID, channelId);
        disconnectMsg.setMetaData(failMetaData);
        ctx.writeAndFlush(disconnectMsg);
        PAIR_CHANNEL_MAP.remove(channelId);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
//        String proxyType = ProxyConstants.PROPERTY.get(ProxyConstants.PROXY_TYPE);
//        if (!ProxyConstants.TYPE_HTTP.equalsIgnoreCase(proxyType) && !ProxyConstants.TYPE_TCP.equalsIgnoreCase(proxyType)) {
//            LOGGER.error("proxy type now can only be HTTP or TCP! please check property.");
//            return;
//        }
//        this.proxyType = proxyType;
//        LOGGER.info("proxy type: " + proxyType + ", start to register to server agent...");
        NatMsg natMsg = new NatMsg();
        natMsg.setType(NatMsgType.REGISTER);
        Map<String, Object> metaData = new HashMap<>(4);
//        String userName = ProxyConstants.PROPERTY.get(ProxyConstants.USER_NAME);
//        metaData.put("userName", userName);
//        String password = ProxyConstants.PROPERTY.get(ProxyConstants.USER_PASSWORD);
//        metaData.put("password", password);
//        String server2ClientPort = ProxyConstants.PROPERTY.get(ProxyConstants.SERVER_CLIENT_PORT);
        String server2ClientPort = "15555";
        metaData.put("port", server2ClientPort);
//        metaData.put("proxyType", proxyType);
        natMsg.setMetaData(metaData);
        ctx.writeAndFlush(natMsg);
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        System.out.println(System.nanoTime() + "断开，5-proxy");
        System.out.println("PAIR_CHANNEL_MAP: 大小：" + PAIR_CHANNEL_MAP.size());
        PAIR_CHANNEL_MAP.values().forEach(e -> {
            NettyUtil.closeOnFlush(e.getPortalChannel());
            NettyUtil.closeOnFlush(e.getAgentChannel());
        });
        PAIR_CHANNEL_MAP.clear();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("===client agent has caught exception, cause: " +  cause.getMessage());
        ctx.close();
    }
}
