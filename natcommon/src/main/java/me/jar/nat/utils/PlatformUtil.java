package me.jar.nat.utils;

import io.netty.util.CharsetUtil;
import me.jar.nat.constants.ProxyConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * @Description
 * @Date 2021/4/24-17:31
 */
public final class PlatformUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformUtil.class);
    public static final int PLATFORM_CODE;

    static {
        PLATFORM_CODE = getPlatform();
    }

    private PlatformUtil() {
    }

    public static Map<String, String> getProperty() {
        Map<String, String> propertyMap = new HashMap<>(20);
        String propertyFileName;
        if (PLATFORM_CODE == ProxyConstants.WIN_OS) {
            propertyFileName = ProxyConstants.BASE_PATH_WIN + "property.txt";
        } else if (PLATFORM_CODE == ProxyConstants.LINUX_OS) {
            propertyFileName = ProxyConstants.BASE_PATH_LINUX + "property.txt";
        } else {
            // 打印日志提示，不支持的系统
            LOGGER.warn("===Unsupported System!");
            return propertyMap;
        }
        File file = new File(propertyFileName);
        if (!file.exists()) {
            LOGGER.warn("property.txt not exist! if not running on server, it OK");
            return propertyMap;
        }
        parseProperty2Map(propertyMap, propertyFileName);
        return propertyMap;
    }

    public static Map<String, String> getProperty(String index) {
        Map<String, String> propertyMap = new HashMap<>(20);
        String propertyFileName;
        if (PLATFORM_CODE == ProxyConstants.WIN_OS) {
            propertyFileName = ProxyConstants.BASE_PATH_WIN + "property_" + index + ".txt";
        } else if (PLATFORM_CODE == ProxyConstants.LINUX_OS) {
            propertyFileName = ProxyConstants.BASE_PATH_LINUX + "property_" + index + ".txt";
        } else {
            // 打印日志提示，不支持的系统
            LOGGER.warn("===Unsupported System!");
            return propertyMap;
        }
        parseProperty2Map(propertyMap, propertyFileName);
        return propertyMap;
    }

    /**
     * 获取操作系统类型
     *
     * @return 整数，1代表Windows，2代表Linux，3代表其它系统
     */
    public static int getPlatform() {
        String osName = System.getProperty("os.name");
        if (osName == null || osName.length() == 0) {
            return ProxyConstants.OTHER_OS;
        }
        if (osName.contains("Windows")) {
            LOGGER.info("===Running on Windows.");
            return ProxyConstants.WIN_OS;
        } else if (osName.contains("Linux")) {
            LOGGER.info("===Running on Linux");
            return ProxyConstants.LINUX_OS;
        } else {
            LOGGER.info("===Running on other os, which is {}", osName);
            return ProxyConstants.OTHER_OS;
        }
    }

    private static void parseProperty2Map(Map<String, String> propertyMap, String propertyFileName) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(propertyFileName), CharsetUtil.UTF_8))) {
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                if (line.length() == 0) {
                    continue;
                }
                String[] split = line.split("=");
                if (split.length == 2) {
                    propertyMap.put(split[0], split[1]);
                }
            }
        } catch (IOException e) {
            // 打印日志提示，读取配置文件失败
            LOGGER.error("===Reading property file failed，please check!", e);
        }
    }

    public static Map<String, String> parseProperty2Map(URL location) {
        Map<String, String> propertyMap = new HashMap<>();
        String path = location.getPath();
        if (path.contains(".jar")) {
            String tempPath = null;
            if (PLATFORM_CODE == ProxyConstants.WIN_OS) {
                tempPath = path.substring(path.indexOf("/") + 1, path.indexOf(".jar"));
            } else if (PLATFORM_CODE == ProxyConstants.LINUX_OS) {
                tempPath = path.substring(path.indexOf("/"), path.indexOf(".jar"));
            } else {
                // 打印日志提示，不支持的系统
                LOGGER.warn("===Unsupported System!");
            }
            if (tempPath != null) {
                String targetDirPath = tempPath.substring(0, tempPath.lastIndexOf("/") + 1);
                File file = new File(targetDirPath);
                if (file.exists() && file.isDirectory()) {
                    File[] properties = file.listFiles(pathname -> pathname.getName().contains("property"));
                    if (properties == null || properties.length != 1) {
                        LOGGER.error("jar file directory should be only one property file! please check");
                    } else {
                        File property = properties[0];
                        try {
                            parseProperty2Map(propertyMap, property.getCanonicalPath());
                        } catch (IOException e) {
                            LOGGER.error("get property file path failed! detail: " + e.getMessage());
                        }
                    }
                } else {
                    LOGGER.error("get jar file directory failed! please check!");
                }
            }
        } else {
            LOGGER.error("jar file directory has no jar file! please check!");
        }
        return propertyMap;
    }
}
