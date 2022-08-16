package me.jar.portal.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;
import me.jar.nat.utils.NettyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (clientChannel.isActive()) {
            clientChannel.writeAndFlush(msg);
        } else {
            LOGGER.error("===Client channel disconnected, no transferring data.");
            ReferenceCountUtil.release(msg);
            ctx.close();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        LOGGER.debug("===Far channel disconnected");
        NettyUtil.closeOnFlush(clientChannel);
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("===ReceiveFarHandler caught exception, cause: {}", cause.getMessage() + ". host: " + ctx.channel().remoteAddress().toString());
        NettyUtil.closeOnFlush(clientChannel);
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.ALL_IDLE) {
                LOGGER.warn("no data read and write more than 10s, close connection");
                NettyUtil.closeOnFlush(clientChannel);
                ctx.close();
            }
        }
    }
}
