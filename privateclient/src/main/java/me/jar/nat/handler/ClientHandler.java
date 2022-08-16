package me.jar.nat.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import me.jar.nat.constants.NatMsgType;
import me.jar.nat.constants.ProxyConstants;
import me.jar.nat.message.NatMsg;
import me.jar.nat.utils.AESUtil;
import me.jar.nat.utils.BuildDataUtil;
import me.jar.nat.utils.NettyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

/**
 * @Description
 * @Date 2021/4/27-21:50
 */
public class ClientHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientHandler.class);
    private final Channel proxyChannel;
    private final String channelId;
    private final String password;
    private final Map<String, Channel> channelMap;
    private int idleTime;
    private int lastLength = 0;
    private final boolean isNeedWaiting = ProxyConstants.TYPE_HTTP.equalsIgnoreCase(ProxyConstants.PROPERTY.get(ProxyConstants.PROXY_TYPE));

    public ClientHandler(Channel proxyChannel, String channelId, Map<String, Channel> channelMap, int idleTime) {
        this.proxyChannel = proxyChannel;
        this.channelId = channelId;
        String password = ProxyConstants.PROPERTY.get(ProxyConstants.PROPERTY_NAME_KEY);
        if (password == null || password.length() == 0) {
            throw new IllegalArgumentException("Illegal key from property");
        }
        this.password = password;
        this.channelMap = channelMap;
        this.idleTime = idleTime;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof byte[]) {
            byte[] bytes = (byte[]) msg;
            try {
                byte[] encrypt = AESUtil.encrypt(bytes, password);
                byte[] data = BuildDataUtil.buildLengthAndMarkWithData(encrypt);
                if (isNeedWaiting) {
                    if (lastLength > 10240) {
                        Thread.sleep(100L);
                        if (!channelMap.containsKey(channelId)) {
                            LOGGER.warn("channelMap has no channel, its id: " + channelId + ", stop sending data!");
                            ctx.close();
                            return;
                        }
                    }
                    lastLength = data.length;
                }
                Map<String, Object> metaData = new HashMap<>(1);
                metaData.put(ProxyConstants.CHANNEL_ID, channelId);
                NatMsg natMsg = new NatMsg();
                natMsg.setType(NatMsgType.DATA);
                natMsg.setMetaData(metaData);
                natMsg.setDate(data);
                proxyChannel.writeAndFlush(natMsg);
            } catch (GeneralSecurityException | UnsupportedEncodingException | InterruptedException e) {
                LOGGER.error("===Encrypt data failed. detail: {}", e.getMessage());
                ctx.close();
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        channelMap.remove(channelId);
        Map<String, Object> metaData = new HashMap<>(1);
        metaData.put(ProxyConstants.CHANNEL_ID, channelId);
        NatMsg transferMsg = new NatMsg();
        transferMsg.setType(NatMsgType.DISCONNECT);
        transferMsg.setMetaData(metaData);
        proxyChannel.writeAndFlush(transferMsg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("===target channel has caught exception, cause: {}", cause.getMessage());
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.ALL_IDLE) {
                LOGGER.warn("no data read and write more than " + idleTime + "s, close connection");
                channelMap.remove(channelId);
                // 发送DISCONNECT消息 close会调用channelInactive
                NettyUtil.closeOnFlush(ctx.channel());
            }
        }
    }
}
