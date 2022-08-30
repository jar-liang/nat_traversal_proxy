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
    private final Map<String, String> userAndPwdMap;

    public ClientProxyHandler(Channel serverProxyChannel, Map<String, Map<String, PairChannel>> globalChannelMap, Map<String, String> userAndPwdMap) {
        this.serverProxyChannel = serverProxyChannel;
        this.globalChannelMap = globalChannelMap;
        serverProxyChannelId = serverProxyChannel.id().asLongText();
        this.userAndPwdMap = userAndPwdMap;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws NatProxyException {
        if (msg instanceof NatMsg) {
            NatMsg natMsg = (NatMsg) msg;
            Map<String, Object> metaData = natMsg.getMetaData();
            if (isPortalHandler || ProxyConstants.ROLE_PORTAL.equals(natMsg.getMetaData().get(ProxyConstants.ROLE))) {
                String id = String.valueOf(metaData.get(ProxyConstants.CHANNEL_ID));
                Map<String, PairChannel> pairChannelMap = globalChannelMap.get(serverProxyChannelId);
                switch (natMsg.getType()) {
                    case CONNECT:
                        if (userAndPwdMap == null || userAndPwdMap.isEmpty()) {
                            throw new NatProxyException("userAndPwdMap is empty, please check!");
                        }
                        String userNameRegister = String.valueOf(metaData.get("userName"));
                        if (!userAndPwdMap.containsKey(userNameRegister) || !userAndPwdMap.get(userNameRegister).equals(metaData.get("password"))) {
                            throw new NatProxyException("illegal connect request from portal");
                        }
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
                        theOtherChannel.writeAndFlush(natMsg);
                        break;
                    case DISCONNECT:
                        ctx.close();
                        break;
                    default:
                        throw new NatProxyException("message type is not one of CONNECT,DATA,DISCONNECT");
                }
            } else {
                String id = String.valueOf(metaData.get(ProxyConstants.CHANNEL_ID));
                switch (natMsg.getType()) {
                    case CONNECT:
                        channelId = id;
                        if (userAndPwdMap == null || userAndPwdMap.isEmpty()) {
                            throw new NatProxyException("userAndPwdMap is empty, please check!");
                        }
                        String userNameRegister = String.valueOf(metaData.get("userName"));
                        if (!userAndPwdMap.containsKey(userNameRegister) || !userAndPwdMap.get(userNameRegister).equals(metaData.get("password"))) {
                            throw new NatProxyException("illegal connect request from agent");
                        }
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
                        theOtherChannel.pipeline().fireUserEventTriggered(natMsg);
                        break;
                    case DATA:
                        theOtherChannel.writeAndFlush(natMsg);
                        break;
                    case DISCONNECT:
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
            ctx.writeAndFlush(natMsg);
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        NatMsg natMsg = new NatMsg();
        natMsg.setType(NatMsgType.DISCONNECT);
        Map<String, Object> metaData = new HashMap<>(1);
        metaData.put(ProxyConstants.CHANNEL_ID, channelId);
        natMsg.setMetaData(metaData);
        boolean hasConnected = false;
        if (theOtherChannel != null && theOtherChannel.isActive()) {
            hasConnected = true;
            theOtherChannel.writeAndFlush(natMsg).addListener(ChannelFutureListener.CLOSE);
        }
        Map<String, PairChannel> pairChannelMap = globalChannelMap.get(serverProxyChannelId);
        if (pairChannelMap != null) {
            if (!hasConnected && !isPortalHandler && channelId.length() > 0 && pairChannelMap.get(channelId) != null) {
                PairChannel pairChannel = pairChannelMap.get(channelId);
                Channel portalChannel = pairChannel.getPortalChannel();
                if (portalChannel != null && portalChannel.isActive()) {
                    portalChannel.writeAndFlush(natMsg).addListener(ChannelFutureListener.CLOSE);
                }
            }
            pairChannelMap.remove(channelId);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("===clientProxyHandler caught exception. cause: " + cause.getMessage());
        ctx.close();
    }

}
