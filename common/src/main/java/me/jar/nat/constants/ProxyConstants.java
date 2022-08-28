package me.jar.nat.constants;

import me.jar.nat.utils.PlatformUtil;

import java.util.Map;

/**
 * @Description
 * @Date 2021/4/21-22:12
 */
public interface ProxyConstants {

    /**
     * 特定标识字节，用于标识数据流是否合法源发出
     */
    byte[] MARK_BYTE = new byte[] {2, 4, 6, 8};

    int WIN_OS = 1;

    int LINUX_OS = 2;

    int OTHER_OS = 3;

    String BASE_PATH_WIN = "C:\\usr\\natTraversalProxy\\";

    String BASE_PATH_LINUX = "/usr/natTraversalProxy/";

    String PROPERTY_NAME_WIN = BASE_PATH_WIN + "property.txt";

    String PROPERTY_NAME_LINUX= BASE_PATH_LINUX + "property.txt";

    String USER_FILE_WIN = BASE_PATH_WIN + "user.txt";

    String USER_FILE_LINUX = BASE_PATH_LINUX + "user.txt";

    String KEY_NAME_PORT = "listenning.port";

    Map<String, String> PROPERTY = PlatformUtil.getProperty();

    String PROPERTY_NAME_KEY = "key";

    String FAR_SERVER_IP = "far.ip";

    String FAR_SERVER_PORT = "far.port";

    String CHANNEL_ID = "channelId";

    String ROLE = "role";

    String ROLE_PORTAL = "p";

    String ROLE_AGENT = "a";

    String SERVER_LISTEN_PORT = "server.listen.port";

    String REGISTER_KEY = "register.key";

    String SERVER_CLIENT_PORT = "server.to.client.port";

    String TARGET_IP = "target.ip";

    String TARGET_PORT = "target.port";

    String USER_NAME = "user.name";

    String USER_PASSWORD = "user.password";

    String PROXY_TYPE = "proxy.type";

    String TYPE_HTTP = "HTTP";

    String TYPE_TCP = "TCP";
}
