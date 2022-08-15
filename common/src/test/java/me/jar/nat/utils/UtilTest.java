package me.jar.nat.utils;

import com.alibaba.fastjson.JSON;
import me.jar.nat.constants.ProxyConstants;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * @Description
 * @Date 2021/4/24-17:42
 */
public class UtilTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(UtilTest.class);

    @Test
    public void testGetPlatform() {
        int platform = PlatformUtil.getPlatform();
        LOGGER.info("Running platform is {}", platform == ProxyConstants.WIN_OS ? "Windows" : platform == ProxyConstants.LINUX_OS ? "Linux" : "Other OS");
    }

    @Test
    public void testGetProperty() {
        Map<String, String> property = PlatformUtil.getProperty();
        property.forEach((key, value) -> LOGGER.info("{} = {}", key, value));
    }

    @Test
    public void testJsonTransfer() {
        Map<String, Object> data = new HashMap<>();
        data.put("length", 5);
        data.put("id", "1555");
        System.out.println(data);
        byte[] bytes = JSON.toJSONBytes(data);
        Map<String, Object> o = JSON.parseObject(bytes, Map.class);
        System.out.println(o);
    }
}
