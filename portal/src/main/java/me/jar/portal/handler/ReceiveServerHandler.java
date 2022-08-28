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
import me.jar.nat.utils.NettyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * @Description
 * @Date 2021/4/27-22:17
 */
public class ReceiveServerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiveServerHandler.class);

    private final Channel clientChannel;

    public ReceiveServerHandler(Channel clientChannel) {
        this.clientChannel = clientChannel;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws NatProxyException {
        if (msg instanceof NatMsg) {
            NatMsg natMsg = (NatMsg) msg;
            switch (natMsg.getType()) {
                case CONNECT:
                    System.out.println("建立连接，步骤9，收到发回来的CONNECT");
                    String id = String.valueOf(natMsg.getMetaData().get(ProxyConstants.CHANNEL_ID));
                    if (ctx.channel().id().asLongText().equals(id)) {
                        System.out.println("建立连接，步骤10，连接建立完成，开始让读数据");
                        clientChannel.config().setAutoRead(true);
                    } else {
                        throw new NatProxyException("connect message channel id does not match ReceiveServerHandler channel id");
                    }
                    break;
                case DATA:
                    byte[] date = natMsg.getDate();
//                    System.out.println("返回6");
                    clientChannel.writeAndFlush(Unpooled.wrappedBuffer(date));
                    break;
                case DISCONNECT:
                    System.out.println(System.nanoTime() + "portal收到断开消息");
                    ctx.close();
                    break;
                default:
                    throw new NatProxyException("message type is not one of CONNECT,DATA,DISCONNECT");
            }
        }
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        System.out.println("portal执行channelWritabilityChanged，是否可写：" + ctx.channel().isWritable());
        clientChannel.config().setAutoRead(ctx.channel().isWritable());
        super.channelWritabilityChanged(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        System.out.println("建立连接，步骤2，发送CONNECT");
        NatMsg natMsg = new NatMsg();
        natMsg.setType(NatMsgType.CONNECT);
        Map<String, Object> metaData = new HashMap<>(4);
        metaData.put(ProxyConstants.CHANNEL_ID, ctx.channel().id().asLongText());
        metaData.put(ProxyConstants.ROLE, ProxyConstants.ROLE_PORTAL);
        String targetIp = "192.168.0.110";
        String targetPort = "8080";
        metaData.put(ProxyConstants.TARGET_IP, targetIp);
        metaData.put(ProxyConstants.TARGET_PORT, targetPort);
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
        System.out.println(System.nanoTime() + "断开，2");
        NettyUtil.closeOnFlush(clientChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("===ReceiveFarHandler caught exception, cause: {}", cause.getMessage() + ". host: " + ctx.channel().remoteAddress().toString());
        NettyUtil.closeOnFlush(clientChannel);
        ctx.close();
    }

}
