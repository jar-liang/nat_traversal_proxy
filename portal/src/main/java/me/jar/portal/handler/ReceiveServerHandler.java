package me.jar.portal.handler;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import me.jar.nat.constants.NatMsgType;
import me.jar.nat.constants.ProxyConstants;
import me.jar.nat.exception.NatProxyException;
import me.jar.nat.message.NatMsg;
import me.jar.nat.utils.AESUtil;
import me.jar.nat.utils.NettyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

/**
 * @Description
 * @Date 2021/4/27-22:17
 */
public class ReceiveServerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiveServerHandler.class);

    private final Channel clientChannel;
    private final String password;

    public ReceiveServerHandler(Channel clientChannel) {
        String password = ProxyConstants.PROPERTY.get(ProxyConstants.PROPERTY_NAME_KEY);
        if (password == null || password.length() == 0) {
            throw new IllegalArgumentException("Illegal key from property");
        }
        this.password = password;
        this.clientChannel = clientChannel;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws NatProxyException {
        if (msg instanceof NatMsg) {
            NatMsg natMsg = (NatMsg) msg;
            switch (natMsg.getType()) {
                case CONNECT:
                    String id = String.valueOf(natMsg.getMetaData().get(ProxyConstants.CHANNEL_ID));
                    if (ctx.channel().id().asLongText().equals(id)) {
                        clientChannel.config().setAutoRead(true);
                    } else {
                        throw new NatProxyException("connect message channel id does not match ReceiveServerHandler channel id");
                    }
                    break;
                case DATA:
                    try {
                        byte[] decryptData = AESUtil.decrypt(natMsg.getDate(), password);
                        clientChannel.writeAndFlush(Unpooled.wrappedBuffer(decryptData));
                    } catch (GeneralSecurityException | UnsupportedEncodingException e) {
                        LOGGER.error("===data from server, Decrypt data failed. detail: {}", e.getMessage());
                        NettyUtil.closeOnFlush(ctx.channel());
                    }
                    break;
                case DISCONNECT:
                    ctx.close();
                    break;
                default:
                    throw new NatProxyException("message type is not one of CONNECT,DATA,DISCONNECT");
            }
        }
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        clientChannel.config().setAutoRead(ctx.channel().isWritable());
        super.channelWritabilityChanged(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        NatMsg natMsg = new NatMsg();
        natMsg.setType(NatMsgType.CONNECT);
        Map<String, Object> metaData = new HashMap<>(6);
        metaData.put(ProxyConstants.CHANNEL_ID, ctx.channel().id().asLongText());
        metaData.put(ProxyConstants.ROLE, ProxyConstants.ROLE_PORTAL);
        String targetIp = ProxyConstants.PROPERTY.get(ProxyConstants.TARGET_IP);
        String targetPort = ProxyConstants.PROPERTY.get(ProxyConstants.TARGET_PORT);
        metaData.put(ProxyConstants.TARGET_IP, targetIp);
        metaData.put(ProxyConstants.TARGET_PORT, targetPort);
        String userName = ProxyConstants.PROPERTY.get(ProxyConstants.USER_NAME);
        String password = ProxyConstants.PROPERTY.get(ProxyConstants.USER_PASSWORD);
        metaData.put("userName", userName);
        metaData.put("password", password);
        natMsg.setMetaData(metaData);
        ctx.writeAndFlush(natMsg).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                ctx.channel().config().setAutoRead(true);
            } else {
                NettyUtil.closeOnFlush(clientChannel);
                ctx.close();
            }
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        NettyUtil.closeOnFlush(clientChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("===ReceiveServerHandler caught exception, cause: {}", cause.getMessage() + ". host: " + ctx.channel().remoteAddress().toString());
        NettyUtil.closeOnFlush(clientChannel);
        ctx.close();
    }

}
