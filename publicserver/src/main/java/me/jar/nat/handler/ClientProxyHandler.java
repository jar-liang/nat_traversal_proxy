package me.jar.nat.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.CharsetUtil;
import me.jar.nat.channel.PairChannel;
import me.jar.nat.constants.NatMsgType;
import me.jar.nat.constants.ProxyConstants;
import me.jar.nat.exception.NatProxyException;
import me.jar.nat.message.NatMsg;
import me.jar.nat.starter.PublicServerStarter;
import me.jar.nat.utils.NettyUtil;
import me.jar.nat.utils.PlatformUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
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
    private final Map<String, String> userAndPwdMap = new HashMap<>();

    public ClientProxyHandler(Channel serverProxyChannel, Map<String, Map<String, PairChannel>> globalChannelMap) {
        this.serverProxyChannel = serverProxyChannel;
        this.globalChannelMap = globalChannelMap;
        serverProxyChannelId = serverProxyChannel.id().asLongText();
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
                        System.out.println("建立连接，步骤3，通过serverProxyChannel发送CONNECT");
                        String path = PublicServerStarter.getUrl().getPath();
                        boolean findUserFile = getUserAndPwdMap(path);
                        if (!findUserFile) {
                            throw new NatProxyException("no valid user file");
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
                String id = String.valueOf(metaData.get(ProxyConstants.CHANNEL_ID));
                switch (natMsg.getType()) {
                    case CONNECT:
                        System.out.println("建立连接，步骤7，收到agent的CONNECT");
                        channelId = id;
                        String path = PublicServerStarter.getUrl().getPath();
                        boolean findUserFile = getUserAndPwdMap(path);
                        if (!findUserFile) {
                            throw new NatProxyException("no valid user file");
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
            if (channelId.length() > 0 && pairChannelMap.get(channelId) != null) {
                PairChannel pairChannel = pairChannelMap.get(channelId);
                Channel portalChannel = pairChannel.getPortalChannel();
                if (portalChannel != null && portalChannel.isActive()) {
                    System.out.println("agent没connect成功，发送disconnect给portal");
                    portalChannel.writeAndFlush(natMsg).addListener(ChannelFutureListener.CLOSE);
                }
            }
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

    private boolean getUserAndPwdMap(String path) {
        userAndPwdMap.put("aaa","12345678");
        userAndPwdMap.put("bbb","87654321");
        return true;
//        if (!userAndPwdMap.isEmpty()) {
//            return true;
//        }
//        boolean findUserFile = true;
//        if (path.contains(".jar")) {
//            String tempPath = null;
//            if (PlatformUtil.PLATFORM_CODE == ProxyConstants.WIN_OS) {
//                tempPath = path.substring(path.indexOf("/") + 1, path.indexOf(".jar"));
//            } else if (PlatformUtil.PLATFORM_CODE == ProxyConstants.LINUX_OS) {
//                tempPath = path.substring(path.indexOf("/"), path.indexOf(".jar"));
//            } else {
//                // 打印日志提示，不支持的系统
//                LOGGER.warn("===Unsupported System!");
//                findUserFile = false;
//            }
//            if (tempPath != null) {
//                String targetDirPath = tempPath.substring(0, tempPath.lastIndexOf("/") + 1);
//                File file = new File(targetDirPath);
//                if (file.exists() && file.isDirectory()) {
//                    File[] properties = file.listFiles(pathname -> pathname.getName().contains("user"));
//                    if (properties == null || properties.length != 1) {
//                        LOGGER.error("jar file directory should be only one property file! please check");
//                        findUserFile = false;
//                    } else {
//                        File property = properties[0];
//                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(property), CharsetUtil.UTF_8))) {
//                            while (true) {
//                                String line = reader.readLine();
//                                if (line == null) {
//                                    break;
//                                }
//                                if (line.length() == 0) {
//                                    continue;
//                                }
//                                String[] split = line.split("\\|");
//                                if (split.length == 2) {
//                                    String userName = split[0];
//                                    String pwd = split[1];
//                                    if (userName != null && userName.length() > 0
//                                            && pwd != null && pwd.length() > 0) {
//                                        userAndPwdMap.put(userName.trim(), pwd.trim());
//                                    }
//                                }
//                            }
//                            if (userAndPwdMap.isEmpty()) {
//                                LOGGER.error("read user file, but no data! please check!");
//                                findUserFile = false;
//                            }
//                        } catch (IOException e) {
//                            // 打印日志提示，读取配置文件失败
//                            LOGGER.error("error code: 02, reading user file failed，please check!", e);
//                            findUserFile = false;
//                        }
//                    }
//                } else {
//                    LOGGER.error("get jar file directory failed! please check!");
//                }
//            }
//        } else {
//            LOGGER.error("path not contain '.jar' string! please check!");
//            findUserFile = false;
//        }
//        return findUserFile;
    }
}
