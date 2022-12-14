package me.jar.portal.handler;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.ReferenceCountUtil;
import me.jar.nat.codec.Byte2NatMsgDecoder;
import me.jar.nat.codec.LengthContentDecoder;
import me.jar.nat.codec.NatMsg2ByteEncoder;
import me.jar.nat.constants.NatMsgType;
import me.jar.nat.constants.ProxyConstants;
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
public class ConnectServerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectServerHandler.class);

    private Channel farChannel;
    private final String password;

    public ConnectServerHandler() {
        String password = ProxyConstants.PROPERTY.get(ProxyConstants.PROPERTY_NAME_KEY);
        if (password == null || password.length() == 0) {
            throw new IllegalArgumentException("Illegal key from property");
        }
        this.password = password;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // 直连far端，将数据发送过去
        if (farChannel != null && farChannel.isActive()) {
            if (msg instanceof ByteBuf) {
                ByteBuf data = (ByteBuf) msg;
                NatMsg natMsg = new NatMsg();
                natMsg.setType(NatMsgType.DATA);
                Map<String, Object> metaData = new HashMap<>(2);
                metaData.put(ProxyConstants.CHANNEL_ID, farChannel.id().asLongText());
                metaData.put(ProxyConstants.ROLE, ProxyConstants.ROLE_PORTAL);
                natMsg.setMetaData(metaData);
                try {
                    byte[] encryptData = AESUtil.encrypt(ByteBufUtil.getBytes(data), password);
                    natMsg.setDate(encryptData);
                    farChannel.writeAndFlush(natMsg).addListener((ChannelFutureListener) future -> {
                        if (!future.isSuccess()) {
                            NettyUtil.closeOnFlush(ctx.channel());
                        }
                    });
                } catch (GeneralSecurityException | UnsupportedEncodingException e) {
                    LOGGER.error("===data from endpoint, Encrypt data failed, close connection. detail: {}", e.getMessage());
                    NettyUtil.closeOnFlush(ctx.channel());
                } finally {
                    ReferenceCountUtil.release(data);
                }
            } else {
                NettyUtil.closeOnFlush(ctx.channel());
            }
        } else {
            NettyUtil.closeOnFlush(ctx.channel());
        }
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        farChannel.config().setAutoRead(ctx.channel().isWritable());
        super.channelWritabilityChanged(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if (!ProxyConstants.PROPERTY.containsKey(ProxyConstants.FAR_SERVER_IP) || !ProxyConstants.PROPERTY.containsKey(ProxyConstants.SERVER_CLIENT_PORT)) {
            LOGGER.error("===Property file has no far server ip or server-to-client port, please check!");
            ctx.close();
            return;
        }
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(ctx.channel().eventLoop()).channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .option(ChannelOption.AUTO_READ, false)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast("lengthContent", new LengthContentDecoder());
                        pipeline.addLast("decoder", new Byte2NatMsgDecoder());
                        pipeline.addLast("encoder", new NatMsg2ByteEncoder());
                        pipeline.addLast("receiveFar", new ReceiveServerHandler(ctx.channel()));
                    }
                });
        String host = ProxyConstants.PROPERTY.get(ProxyConstants.FAR_SERVER_IP);
        int port = Integer.parseInt(ProxyConstants.PROPERTY.get(ProxyConstants.SERVER_CLIENT_PORT));
        bootstrap.connect(host, port)
                .addListener((ChannelFutureListener) connectFuture -> {
                    if (connectFuture.isSuccess()) {
                        farChannel = connectFuture.channel();
                    } else {
                        LOGGER.error("===Failed to connect to far server! host: " + host + " , port: " + port);
                        ctx.close();
                    }
                });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        // 发送disconnect消息
        if (farChannel != null && farChannel.isActive()) {
            NatMsg natMsg = new NatMsg();
            natMsg.setType(NatMsgType.DISCONNECT);
            Map<String, Object> metaData = new HashMap<>(2);
            metaData.put(ProxyConstants.CHANNEL_ID, farChannel.id().asLongText());
            metaData.put(ProxyConstants.ROLE, ProxyConstants.ROLE_PORTAL);
            natMsg.setMetaData(metaData);
            farChannel.writeAndFlush(natMsg).addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("===ConnectServerHandler has caught exception, cause: {}", cause.getMessage());
        NettyUtil.closeOnFlush(ctx.channel());
    }

}
