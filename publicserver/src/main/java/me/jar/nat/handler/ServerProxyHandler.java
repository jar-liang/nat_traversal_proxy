package me.jar.nat.handler;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.CharsetUtil;
import me.jar.nat.channel.PairChannel;
import me.jar.nat.codec.Byte2NatMsgDecoder;
import me.jar.nat.codec.LengthContentDecoder;
import me.jar.nat.codec.NatMsg2ByteEncoder;
import me.jar.nat.constants.NatMsgType;
import me.jar.nat.constants.ProxyConstants;
import me.jar.nat.exception.NatProxyException;
import me.jar.nat.message.NatMsg;
import me.jar.nat.starter.PublicServerStarter;
import me.jar.nat.utils.CommonHandler;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Description
 * @Date 2021/4/25-23:39
 */
public class ServerProxyHandler extends CommonHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerProxyHandler.class);
    private boolean isRegister = false;
    private Channel clientServerChannel;
    private final Map<String, Map<String, PairChannel>> globalChannelMap;
    private final ChannelGroup countChannels;

    private final Map<String, String> userAndPwdMap = new HashMap<>();

    public ServerProxyHandler(Map<String, Map<String, PairChannel>> globalChannelMap, ChannelGroup countChannels) {
        this.globalChannelMap = globalChannelMap;
        this.countChannels = countChannels;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof NatMsg) {
            NatMsg natMsg = (NatMsg) msg;
            if (natMsg.getType() == NatMsgType.REGISTER) {
                doRegister(natMsg);
                return;
            }
            if (isRegister) {
                String id = String.valueOf(natMsg.getMetaData().get(ProxyConstants.CHANNEL_ID));
                switch (natMsg.getType()) {
                    case DISCONNECT:
                        Map<String, PairChannel> pairChannelMap = globalChannelMap.get(channel.id().asLongText());
                        if (pairChannelMap == null) {
                            LOGGER.error("received DISCONNECT from agent, but no pairChannelMap found");
                            return;
                        }
                        PairChannel pairChannel = pairChannelMap.get(id);
                        if (pairChannel == null) {
                            LOGGER.error("received DISCONNECT from agent, but no pairChannel found");
                            return;
                        }
                        NettyUtil.closeOnFlush(pairChannel.getPortalChannel());
                        NettyUtil.closeOnFlush(pairChannel.getAgentChannel());
                        pairChannelMap.remove(id);
                        break;
                    case KEEPALIVE:
                        // 心跳包，不处理
                        break;
                    default:
                        throw new NatProxyException("channel is registered, message type is not one of DISCONNECT,CONNECT,KEEPALIVE");
                }
            } else {
                NettyUtil.closeOnFlush(ctx.channel());
            }
        }
    }

    private void doRegister(NatMsg natMsg) {
        NatMsg returnNatMsg = new NatMsg();
        returnNatMsg.setType(NatMsgType.REGISTER_RESULT);
        Map<String, Object> retnMetaData = new HashMap<>();
        Map<String, Object> metaData = natMsg.getMetaData();
        String path = PublicServerStarter.getUrl().getPath();
        boolean findUserFile = getUserAndPwdMap(path);

        if (!findUserFile) {
            retnMetaData.put("result", "0");
            retnMetaData.put("reason", "server inner error, contact the admin!");
            sendBackMsgAndDealResult(returnNatMsg, retnMetaData);
            return;
        }

        String userNameRegister = String.valueOf(metaData.get("userName"));
        boolean isLegal = false;
        if (userAndPwdMap.containsKey(userNameRegister) && userAndPwdMap.get(userNameRegister).equals(metaData.get("password"))) {
            isLegal = true;
        }
        if (!isLegal) {
            // 没有密钥或密钥错误，返回提示， 不执行注册
            retnMetaData.put("result", "0");
            retnMetaData.put("reason", "Token is wrong");
        } else {
            // 启动一个新的serverBootstrap
            EventLoopGroup bossGroup = new NioEventLoopGroup(1);
            EventLoopGroup workerGroup = new NioEventLoopGroup();
            try {
                Object portObj = metaData.get("port");
                int server2ClientPortNum = Integer.parseInt(String.valueOf(portObj));
                LOGGER.info("register port: " + server2ClientPortNum);
                ChannelInitializer<SocketChannel> channelInitializer = new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        // 添加与客户端交互的handler
                        pipeline.addLast("lengthField", new LengthContentDecoder());
                        pipeline.addLast("decoder", new Byte2NatMsgDecoder());
                        pipeline.addLast("encoder", new NatMsg2ByteEncoder());
                        pipeline.addLast("connectClient", new ClientProxyHandler(channel, globalChannelMap, userAndPwdMap));
                    }
                };

                ServerBootstrap serverBootstrap = new ServerBootstrap();
                serverBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                        .childOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                        .childOption(ChannelOption.SO_KEEPALIVE, true)
                        .childHandler(channelInitializer);
                ChannelFuture cf = serverBootstrap.bind(server2ClientPortNum).sync();
                clientServerChannel = cf.channel();
                countChannels.add(clientServerChannel);
                globalChannelMap.put(channel.id().asLongText(), new ConcurrentHashMap<>());
                cf.channel().closeFuture().addListener((ChannelFutureListener) future -> {
                    LOGGER.error("server2Client close, bossGroup and workerGroup shutdown!");
                    bossGroup.shutdownGracefully();
                    workerGroup.shutdownGracefully();
                });
                retnMetaData.put("result", "1");
                isRegister = true;
                LOGGER.info("server2Client starting, port is " + server2ClientPortNum);
            } catch (Exception e) {
                LOGGER.error("==server2Client starts failed, detail: " + e.getMessage());
                retnMetaData.put("result", "0");
                retnMetaData.put("reason", "client server cannot start");
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
        }
        sendBackMsgAndDealResult(returnNatMsg, retnMetaData);
    }

    private boolean getUserAndPwdMap(String path) {
        if (!userAndPwdMap.isEmpty()) {
            return true;
        }
        boolean findUserFile = true;
        if (path.contains(".jar")) {
            String tempPath = null;
            if (PlatformUtil.PLATFORM_CODE == ProxyConstants.WIN_OS) {
                tempPath = path.substring(path.indexOf("/") + 1, path.indexOf(".jar"));
            } else if (PlatformUtil.PLATFORM_CODE == ProxyConstants.LINUX_OS) {
                tempPath = path.substring(path.indexOf("/"), path.indexOf(".jar"));
            } else {
                // 打印日志提示，不支持的系统
                LOGGER.warn("===Unsupported System!");
                findUserFile = false;
            }
            if (tempPath != null) {
                String targetDirPath = tempPath.substring(0, tempPath.lastIndexOf("/") + 1);
                File file = new File(targetDirPath);
                if (file.exists() && file.isDirectory()) {
                    File[] properties = file.listFiles(pathname -> pathname.getName().contains("user"));
                    if (properties == null || properties.length != 1) {
                        LOGGER.error("jar file directory should be only one property file! please check");
                        findUserFile = false;
                    } else {
                        File property = properties[0];
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(property), CharsetUtil.UTF_8))) {
                            while (true) {
                                String line = reader.readLine();
                                if (line == null) {
                                    break;
                                }
                                if (line.length() == 0) {
                                    continue;
                                }
                                String[] split = line.split("\\|");
                                if (split.length == 2) {
                                    String userName = split[0];
                                    String pwd = split[1];
                                    if (userName != null && userName.length() > 0
                                            && pwd != null && pwd.length() > 0) {
                                        userAndPwdMap.put(userName.trim(), pwd.trim());
                                    }
                                }
                            }
                            if (userAndPwdMap.isEmpty()) {
                                LOGGER.error("read user file, but no data! please check!");
                                findUserFile = false;
                            }
                        } catch (IOException e) {
                            // 打印日志提示，读取配置文件失败
                            LOGGER.error("error code: 02, reading user file failed，please check!", e);
                            findUserFile = false;
                        }
                    }
                } else {
                    LOGGER.error("get jar file directory failed! please check!");
                }
            }
        } else {
            LOGGER.error("path not contain '.jar' string! please check!");
            findUserFile = false;
        }
        return findUserFile;
    }

    private void sendBackMsgAndDealResult(NatMsg retnNatMsg, Map<String, Object> retnMetaData) {
        retnNatMsg.setMetaData(retnMetaData);
        channel.writeAndFlush(retnNatMsg);
        if (!isRegister) {
            LOGGER.error("client agent registered failed, reason: " + retnMetaData.get("reason"));
            channel.close();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        LOGGER.warn("===server agent to client agent connection inactive. channel: " + ctx.channel().toString());
        if (clientServerChannel != null) {
            LOGGER.warn("due to server agent and client agent connection inactive, server2Client has to close. channel: " + clientServerChannel.toString());
            clientServerChannel.close();
        }
        String id = ctx.channel().id().asLongText();
        if (globalChannelMap.containsKey(id)) {
            Map<String, PairChannel> pairChannelMap = globalChannelMap.get(id);
            for (PairChannel pairChannel : pairChannelMap.values()) {
                if (pairChannel != null) {
                    NettyUtil.closeOnFlush(pairChannel.getAgentChannel());
                    NettyUtil.closeOnFlush(pairChannel.getPortalChannel());
                }
            }
            globalChannelMap.remove(id);
        }
    }
}
