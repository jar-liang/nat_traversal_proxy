package me.jar.portal.handler;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import me.jar.nat.codec.Byte2NatMsgDecoder;
import me.jar.nat.codec.LengthContentDecoder;
import me.jar.nat.codec.NatMsg2ByteEncoder;
import me.jar.nat.constants.NatMsgType;
import me.jar.nat.constants.ProxyConstants;
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
public class ConnectServerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectServerHandler.class);

    private Channel farChannel;

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
                byte[] dataBytes = ByteBufUtil.getBytes(data);
                natMsg.setDate(dataBytes);
//                System.out.println("已建立连接，步骤11，读到数据，发送: " + natMsg.toString());
//                System.out.println("发送1");
                farChannel.writeAndFlush(natMsg).addListener((ChannelFutureListener) future -> {
                    if (!future.isSuccess()) {
                        NettyUtil.closeOnFlush(ctx.channel());
                    }
                });
            } else {
                NettyUtil.closeOnFlush(ctx.channel());
            }
        } else {
            NettyUtil.closeOnFlush(ctx.channel());
        }
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        System.out.println("browser执行channelWritabilityChanged，是否可写：" + ctx.channel().isWritable());
        farChannel.config().setAutoRead(ctx.channel().isWritable());
        super.channelWritabilityChanged(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
//        if (!ProxyConstants.PROPERTY.containsKey(ProxyConstants.FAR_SERVER_IP) || !ProxyConstants.PROPERTY.containsKey(ProxyConstants.SERVER_CLIENT_PORT)) {
//            LOGGER.error("===Property file has no far server ip or server-to-client port, please check!");
//            ctx.close();
//            return;
//        }
        System.out.println("建立连接，步骤1，连接服务器");
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
//        String host = ProxyConstants.PROPERTY.get(ProxyConstants.FAR_SERVER_IP);
//        int port = Integer.parseInt(ProxyConstants.PROPERTY.get(ProxyConstants.SERVER_CLIENT_PORT));
        String host = "127.0.0.1";
        int port = 22222;
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
        System.out.println(System.nanoTime() + "断开，1");
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
        LOGGER.error("===ConnectFarHandler has caught exception, cause: {}", cause.getMessage());
        NettyUtil.closeOnFlush(ctx.channel());
    }

}
