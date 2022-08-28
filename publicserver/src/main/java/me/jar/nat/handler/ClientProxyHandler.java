package me.jar.nat.handler;

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
 * @Date 2021/4/25-23:39
 */
public class ClientProxyHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientProxyHandler.class);
    private final String serverProxyChannelId;
    private final Map<String, Map<String, PairChannel>> globalChannelMap;
    private final Channel serverProxyChannel;
    private Channel theOtherChannel;
    private String channelId = "";
    private boolean isPortalHandler = false;

    public ClientProxyHandler(Channel serverProxyChannel, Map<String, Map<String, PairChannel>> globalChannelMap) {
        this.serverProxyChannel = serverProxyChannel;
        this.globalChannelMap = globalChannelMap;
        serverProxyChannelId = serverProxyChannel.id().asLongText();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws NatProxyException {
        if (msg instanceof NatMsg) {
            NatMsg natMsg = (NatMsg) msg;
            if (isPortalHandler || ProxyConstants.ROLE_PORTAL.equals(natMsg.getMetaData().get(ProxyConstants.ROLE))) {
                String id = String.valueOf(natMsg.getMetaData().get(ProxyConstants.CHANNEL_ID));
                Map<String, PairChannel> pairChannelMap = globalChannelMap.get(serverProxyChannelId);
                switch (natMsg.getType()) {
                    case CONNECT:
                        System.out.println("建立连接，步骤3，通过serverProxyChannel发送CONNECT");
                        if (pairChannelMap == null) {
                            throw new NatProxyException("cannot find correct pairChannelMap");
                        }
                        PairChannel pairChannel = new PairChannel();
                        pairChannel.setPortalChannel(ctx.channel());
                        pairChannelMap.put(id, pairChannel);
                        channelId = id;
                        isPortalHandler = true;
                        serverProxyChannel.writeAndFlush(natMsg);
                        break;
                    case DATA:
//                        System.out.println("发送2");
                        theOtherChannel.writeAndFlush(natMsg);
                        break;
                    case DISCONNECT:
                        System.out.println(System.nanoTime() + "ROLE_PORTAL收到断开消息");
                        ctx.close();
                        break;
                    default:
                        throw new NatProxyException("message type is not one of CONNECT,DATA,DISCONNECT");
                }
            } else {
                String id = String.valueOf(natMsg.getMetaData().get(ProxyConstants.CHANNEL_ID));
                switch (natMsg.getType()) {
                    case CONNECT:
                        System.out.println("建立连接，步骤7，收到agent的CONNECT");
                        Map<String, PairChannel> pairChannelMap = globalChannelMap.get(serverProxyChannelId);
                        if (pairChannelMap == null) {
                            throw new NatProxyException("cannot find correct pairChannelMap");
                        }
                        PairChannel pairChannel = pairChannelMap.get(id);
                        if (pairChannel == null) {
                            throw new NatProxyException("no pairChannel object found");
                        }
                        if (pairChannel.getPortalChannel() == null) {
                            throw new NatProxyException("no portal channel found");
                        }
                        theOtherChannel = pairChannel.getPortalChannel();
                        pairChannel.setAgentChannel(ctx.channel());
                        channelId = id;
                        theOtherChannel.pipeline().fireUserEventTriggered(natMsg);
                        break;
                    case DATA:
//                        System.out.println("返回5");
                        theOtherChannel.writeAndFlush(natMsg);
                        break;
                    case DISCONNECT:
                        System.out.println(System.nanoTime() + "ROLE_AGENT收到断开消息");
                        ctx.close();
                        break;
                    default:
                        throw new NatProxyException("message type is not one of CONNECT,DATA,DISCONNECT");
                }
            }
        } else {
            throw new NatProxyException("received message is not NatMsg");
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof NatMsg) {
            Map<String, PairChannel> pairChannelMap = globalChannelMap.get(serverProxyChannelId);
            if (pairChannelMap == null) {
                throw new NatProxyException("cannot find correct pairChannelMap");
            }
            NatMsg natMsg = (NatMsg) evt;
            String id = String.valueOf(natMsg.getMetaData().get(ProxyConstants.CHANNEL_ID));
            PairChannel pairChannel = pairChannelMap.get(id);
            if (pairChannel == null) {
                throw new NatProxyException("no pairChannel object found");
            }
            if (pairChannel.getAgentChannel() == null) {
                throw new NatProxyException("no agent channel found");
            }
            theOtherChannel = pairChannel.getAgentChannel();
            System.out.println("建立连接，步骤8，发送CONNECT给portal");
            ctx.writeAndFlush(natMsg);
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        System.out.println("channelId: " + channelId);
        NatMsg natMsg = new NatMsg();
        natMsg.setType(NatMsgType.DISCONNECT);
        Map<String, Object> metaData = new HashMap<>(1);
        metaData.put(ProxyConstants.CHANNEL_ID, channelId);
        natMsg.setMetaData(metaData);
        if (theOtherChannel != null && theOtherChannel.isActive()) {
            theOtherChannel.writeAndFlush(natMsg).addListener(ChannelFutureListener.CLOSE);
        }
        Map<String, PairChannel> pairChannelMap = globalChannelMap.get(serverProxyChannelId);
        if (pairChannelMap != null) {
            String str;
            if (isPortalHandler) {
                str = "(portal端)";
            } else {
                str = "(agent端)";
            }
            System.out.println(System.nanoTime() + "断开" + str + "，client2client。断开前pairChannelMap大小: " + pairChannelMap.size());
            pairChannelMap.remove(channelId);
            System.out.println(System.nanoTime() + "断开" + str + "，client2client。断开后pairChannelMap大小: " + pairChannelMap.size());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("===server2Client caught exception. channel:" + ctx.channel().toString() + ". cause: " + cause.getMessage());
        ctx.close();
    }

}
