package me.jar.nat.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import me.jar.nat.channel.PairChannel;
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
 * @Date 2021/4/27-21:50
 */
public class ClientHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientHandler.class);
    private final String channelId;
    private final String password;
    private final Map<String, PairChannel> pairChannelMap;
    private final boolean isTargetChannel;
    private Channel theOtherChannel;

    public ClientHandler(String channelId, Map<String, PairChannel> pairChannelMap, boolean isTargetChannel) {
        String password = ProxyConstants.PROPERTY.get(ProxyConstants.PROPERTY_NAME_KEY);
        if (password == null || password.length() == 0) {
            throw new IllegalArgumentException("Illegal key from property");
        }
        this.password = password;
        this.channelId = channelId;
        this.pairChannelMap = pairChannelMap;
        this.isTargetChannel = isTargetChannel;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws NatProxyException {
        if (isTargetChannel) {
            if (msg instanceof ByteBuf) {
                ByteBuf data = (ByteBuf) msg;
                NatMsg natMsg = new NatMsg();
                natMsg.setType(NatMsgType.DATA);
                Map<String, Object> metaData = new HashMap<>(2);
                metaData.put(ProxyConstants.CHANNEL_ID, channelId);
                metaData.put(ProxyConstants.ROLE, ProxyConstants.ROLE_AGENT);
                natMsg.setMetaData(metaData);
                try {
                    byte[] encryptData = AESUtil.encrypt(ByteBufUtil.getBytes(data), password);
                    natMsg.setDate(encryptData);
                    theOtherChannel.writeAndFlush(natMsg).addListener((ChannelFutureListener) future -> {
                        if (!future.isSuccess()) {
                            NettyUtil.closeOnFlush(ctx.channel());
                        }
                    });
                } catch (GeneralSecurityException | UnsupportedEncodingException e) {
                    LOGGER.error("===data from target, Encrypt data failed, close connection. detail: {}", e.getMessage());
                    NettyUtil.closeOnFlush(ctx.channel());
                } finally {
                    ReferenceCountUtil.release(data);
                }
            } else {
                throw new NatProxyException("target message received is not ByteBuf");
            }
        } else {
            if (msg instanceof NatMsg) {
                NatMsg natMsg = (NatMsg) msg;
                switch (natMsg.getType()) {
                    case DATA:
                        try {
                            byte[] decryptData = AESUtil.decrypt(natMsg.getDate(), password);
                            theOtherChannel.writeAndFlush(Unpooled.wrappedBuffer(decryptData));
                        } catch (GeneralSecurityException | UnsupportedEncodingException e) {
                            LOGGER.error("===data from server, Decrypt data failed. detail: {}", e.getMessage());
                            NettyUtil.closeOnFlush(ctx.channel());
                        }
                        break;
                    case DISCONNECT:
                        NettyUtil.closeOnFlush(ctx.channel());
                        break;
                    default:
                        throw new NatProxyException("message type is not one of DATA/DISCONNECT");
                }
            } else {
                throw new NatProxyException("agent message received is not NatMsg");
            }
        }
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        theOtherChannel.config().setAutoRead(ctx.channel().isWritable());
        super.channelWritabilityChanged(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (isTargetChannel) {
            if (theOtherChannel != null && theOtherChannel.isActive()) {
                NatMsg natMsg = new NatMsg();
                natMsg.setType(NatMsgType.DISCONNECT);
                Map<String, Object> metaData = new HashMap<>(2);
                metaData.put(ProxyConstants.CHANNEL_ID, channelId);
                metaData.put(ProxyConstants.ROLE, ProxyConstants.ROLE_AGENT);
                natMsg.setMetaData(metaData);
                theOtherChannel.writeAndFlush(natMsg).addListener(ChannelFutureListener.CLOSE);
            }
        } else {
            NettyUtil.closeOnFlush(theOtherChannel);
        }
        pairChannelMap.remove(channelId);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("===target channel has caught exception, cause: {}", cause.getMessage());
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof Channel) {
            this.theOtherChannel = (Channel) evt;
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }
}
