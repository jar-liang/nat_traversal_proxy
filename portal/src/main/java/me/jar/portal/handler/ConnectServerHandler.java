package me.jar.portal.handler;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.ReferenceCountUtil;
import me.jar.nat.codec.DecryptHandler;
import me.jar.nat.codec.EncryptHandler;
import me.jar.nat.constants.ProxyConstants;
import me.jar.nat.utils.NettyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Description
 * @Date 2021/4/27-21:50
 */
public class ConnectServerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectServerHandler.class);

    private Channel farChannel;
    private int lastLength = 0;
    private static final boolean IS_NEED_WAITING = ProxyConstants.TYPE_HTTP.equalsIgnoreCase(ProxyConstants.PROPERTY.get(ProxyConstants.PROXY_TYPE));

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws InterruptedException {
        // 直连far端，将数据发送过去
        if (farChannel != null && farChannel.isActive()) {
            if (msg instanceof ByteBuf) {
                ByteBuf data = (ByteBuf) msg;
                if (IS_NEED_WAITING) {
                    if (lastLength > 10240) {
                        Thread.sleep(150L);
                    }
                    lastLength = data.readableBytes();
                }
                farChannel.writeAndFlush(data);
            }
        } else {
            if (!ProxyConstants.PROPERTY.containsKey(ProxyConstants.FAR_SERVER_IP) || !ProxyConstants.PROPERTY.containsKey(ProxyConstants.KEY_NAME_PORT)) {
                LOGGER.error("===Property file has no far server ip or port, please check!");
                ReferenceCountUtil.release(msg);
                ctx.close();
                return;
            }

            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(ctx.channel().eventLoop()).channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast("decrypt", new DecryptHandler());
                            pipeline.addLast("encrypt", new EncryptHandler());
                            pipeline.addLast("idleEvt", new IdleStateHandler(0, 0, 10));
                            pipeline.addLast("receiveFar", new ReceiveServerHandler(ctx.channel()));
                        }
                    });
            String host = ProxyConstants.PROPERTY.get(ProxyConstants.FAR_SERVER_IP);
            int port = Integer.parseInt(ProxyConstants.PROPERTY.get(ProxyConstants.SERVER_CLIENT_PORT));
            bootstrap.connect(host, port)
                    .addListener((ChannelFutureListener) connectFuture -> {
                        if (connectFuture.isSuccess()) {
                            farChannel = connectFuture.channel();
                            farChannel.writeAndFlush(msg);
                        } else {
                            LOGGER.error("===Failed to connect to far server! host: " + host + " , port: " + port);
                            ReferenceCountUtil.release(msg);
                            ctx.close();
                        }
                    });
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        LOGGER.debug("===Client disconnected.");
        NettyUtil.closeOnFlush(farChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("===ConnectFarHandler has caught exception, cause: {}", cause.getMessage());
        NettyUtil.closeOnFlush(farChannel);
        ctx.close();
    }

}
