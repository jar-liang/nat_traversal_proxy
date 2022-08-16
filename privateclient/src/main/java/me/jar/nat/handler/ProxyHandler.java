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
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import me.jar.nat.constants.NatMsgType;
import me.jar.nat.constants.ProxyConstants;
import me.jar.nat.exception.NatProxyException;
import me.jar.nat.message.NatMsg;
import me.jar.nat.utils.AESUtil;
import me.jar.nat.utils.CommonHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Description
 * @Date 2021/4/27-21:50
 */
public class ProxyHandler extends CommonHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyHandler.class);
    private static final Map<String, Channel> CHANNEL_MAP = new ConcurrentHashMap<>();
    private final String password;
    private String proxyType;

    public ProxyHandler() {
        String password = ProxyConstants.PROPERTY.get(ProxyConstants.PROPERTY_NAME_KEY);
        if (password == null || password.length() == 0) {
            throw new IllegalArgumentException("Illegal key from property");
        }
        this.password = password;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof NatMsg) {
            NatMsg natMsg = (NatMsg) msg;
            NatMsgType type = natMsg.getType();
            Map<String, Object> metaData = natMsg.getMetaData();
            String channelId = String.valueOf(metaData.get(ProxyConstants.CHANNEL_ID));
            switch (type) {
                case REGISTER_RESULT:
                    if ("1".equals(metaData.get("result"))) {
                        LOGGER.info("register to transfer proxy server successfully");
                    } else {
                        LOGGER.error("register failed, reason: " + metaData.get("reason"));
                        ctx.close();
                    }
                    break;
//                case CONNECT: // 不需要了，直接根据是否有连接判断进行处理，在下面的DATA的case中处理连接
//                    connectTarget(ctx, msg, metaData);
//                    break;
                case DISCONNECT:
                    CHANNEL_MAP.remove(channelId);
                    break;
                case DATA:
                    Channel channelData = CHANNEL_MAP.get(channelId);
                    byte[] transferMsgDate = natMsg.getDate();
                    if (transferMsgDate.length < ProxyConstants.MARK_BYTE.length) {
                        LOGGER.error("Get data length error! data length: " + transferMsgDate.length + ", less than mark bytes length: " + ProxyConstants.MARK_BYTE.length);
                        ctx.close();
                        break;
                    }
                    for (int i = 0; i < ProxyConstants.MARK_BYTE.length; i++) {
                        if (transferMsgDate[transferMsgDate.length - ProxyConstants.MARK_BYTE.length + i] != ProxyConstants.MARK_BYTE[i]) {
                            LOGGER.info("===Illegal data from ip: {}", ctx.channel().remoteAddress());
                            ctx.close();
                            return;
                        }
                    }
                    byte[] encryptSource = new byte[transferMsgDate.length - ProxyConstants.MARK_BYTE.length];
                    System.arraycopy(transferMsgDate, 0, encryptSource, 0, encryptSource.length);
                    try {
                        byte[] decryptBytes = AESUtil.decrypt(encryptSource, password);
                        if (channelData == null || !channelData.isActive()) {
                            connectTarget(ctx, decryptBytes, metaData);
                        } else {
                            channelData.writeAndFlush(decryptBytes);
                        }
                    } catch (GeneralSecurityException | UnsupportedEncodingException e) {
                        LOGGER.error("===Decrypt data failed. detail: {}", e.getMessage());
                        ctx.close();
                    }
                    break;
                case KEEPALIVE:
                    break;
                default:
                    throw new NatProxyException("unknown type: " + type.getType());
            }
        }
    }

    private void connectTarget(ChannelHandlerContext ctx, byte[] data, Map<String, Object> metaData) {
        String channelId = String.valueOf(metaData.get(ProxyConstants.CHANNEL_ID));
        EventLoopGroup workGroup = new NioEventLoopGroup(1);
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workGroup).channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000).handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast("byteArrayDecoder", new ByteArrayDecoder());
                pipeline.addLast("byteArrayEncoder", new ByteArrayEncoder());
                int idleTime = !ProxyConstants.TYPE_TCP.equalsIgnoreCase(proxyType) ? 15 : 10;
                pipeline.addLast("idleEvt", new IdleStateHandler(0, 0, idleTime));
                pipeline.addLast("clientHandler", new ClientHandler(ctx.channel(), channelId, CHANNEL_MAP, idleTime));
                CHANNEL_MAP.put(channelId, ch);
            }
        });
        String targetIp = ProxyConstants.PROPERTY.get(ProxyConstants.TARGET_IP);
        String targetPort = ProxyConstants.PROPERTY.get(ProxyConstants.TARGET_PORT);
        try {
            int targetPortNum = Integer.parseInt(targetPort);
            bootstrap.connect(targetIp, targetPortNum).addListener((ChannelFutureListener) connectFuture -> {
                if (connectFuture.isSuccess()) {
                    connectFuture.channel().writeAndFlush(data);
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
        CHANNEL_MAP.remove(channelId);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        String proxyType = ProxyConstants.PROPERTY.get(ProxyConstants.PROXY_TYPE);
        if (!ProxyConstants.TYPE_HTTP.equalsIgnoreCase(proxyType) && !ProxyConstants.TYPE_TCP.equalsIgnoreCase(proxyType)) {
            LOGGER.error("proxy type now can only be HTTP or TCP! please check property.");
            return;
        }
        this.proxyType = proxyType;
        LOGGER.info("proxy type: " + proxyType + ", start to register to server agent...");
        NatMsg natMsg = new NatMsg();
        natMsg.setType(NatMsgType.REGISTER);
        Map<String, Object> metaData = new HashMap<>(4);
        String userName = ProxyConstants.PROPERTY.get(ProxyConstants.USER_NAME);
        metaData.put("userName", userName);
        String password = ProxyConstants.PROPERTY.get(ProxyConstants.USER_PASSWORD);
        metaData.put("password", password);
        String server2ClientPort = ProxyConstants.PROPERTY.get(ProxyConstants.SERVER_CLIENT_PORT);
        metaData.put("port", server2ClientPort);
        metaData.put("proxyType", proxyType);
        natMsg.setMetaData(metaData);
        ctx.writeAndFlush(natMsg);
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        LOGGER.info("===client agent to server agent connection inactive, channel: " + ctx.channel().toString());
        CHANNEL_MAP.values().forEach(e -> {
            if (e != null && e.isActive()) {
                e.close();
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("===client agent has caught exception, cause: " +  cause.getMessage());
        ctx.close();
    }
}
