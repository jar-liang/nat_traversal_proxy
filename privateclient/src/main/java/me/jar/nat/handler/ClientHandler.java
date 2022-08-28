package me.jar.nat.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import me.jar.nat.channel.PairChannel;
import me.jar.nat.constants.NatMsgType;
import me.jar.nat.constants.ProxyConstants;
import me.jar.nat.exception.NatProxyException;
import me.jar.nat.message.NatMsg;
import me.jar.nat.utils.NettyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
//    private final String password;
    private final Map<String, PairChannel> pairChannelMap;
    private final boolean isTargetChannel;
    private Channel theOtherChannel;

    public ClientHandler(Channel proxyChannel, String channelId, Map<String, PairChannel> pairChannelMap, boolean isTargetChannel) {
        this.proxyChannel = proxyChannel;
        this.channelId = channelId;
//        String password = ProxyConstants.PROPERTY.get(ProxyConstants.PROPERTY_NAME_KEY);
//        if (password == null || password.length() == 0) {
//            throw new IllegalArgumentException("Illegal key from property");
//        }
//        this.password = password;
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
                natMsg.setDate(ByteBufUtil.getBytes(data));
//                System.out.println("返回4");
                theOtherChannel.writeAndFlush(natMsg).addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        ctx.channel().read();
                    } else {
                        NettyUtil.closeOnFlush(ctx.channel());
                    }
                });
            } else {
                throw new NatProxyException("target message received is not ByteBuf");
            }
        } else {
            if (msg instanceof NatMsg) {
                NatMsg natMsg = (NatMsg) msg;
                switch (natMsg.getType()) {
                    case DATA:
//                        System.out.println("发送3");
                        theOtherChannel.writeAndFlush(Unpooled.wrappedBuffer(natMsg.getDate()));
                        break;
                    case DISCONNECT:
                        ctx.close();
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
    public void channelInactive(ChannelHandlerContext ctx) {
        if (isTargetChannel) {
            System.out.println(System.nanoTime() + "断开，4");
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
            System.out.println(System.nanoTime() + "断开，3");
            NettyUtil.closeOnFlush(theOtherChannel);
        }
        System.out.println(System.nanoTime() + "断开前pairChannelMap大小: " + pairChannelMap.size());
        pairChannelMap.remove(channelId);
        System.out.println(System.nanoTime() + "断开后pairChannelMap大小: " + pairChannelMap.size());
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
