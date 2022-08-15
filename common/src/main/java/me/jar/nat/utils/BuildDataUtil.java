package me.jar.nat.utils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import me.jar.nat.constants.ProxyConstants;

/**
 * 构建字节数组数据工具类
 */
public final class BuildDataUtil {
    /**
     * 前面是数据+标识字节长度，中间是数据，最后是标识字节
     * @param encrypt 数据
     * @return 构建好的字节数组
     */
    public static byte[] buildLengthAndMarkWithData(byte[] encrypt) {
        // fix: 添加特定标识字节，防止解密端不停解密导致CPU占用过高
        int dataLength = encrypt.length + ProxyConstants.MARK_BYTE.length;
        ByteBuf byteBuf = Unpooled.copyInt(dataLength);
        byte[] lengthField = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(lengthField);
        byte[] data = new byte[dataLength + lengthField.length];
        System.arraycopy(lengthField, 0, data, 0, lengthField.length);
        System.arraycopy(encrypt, 0, data, lengthField.length, encrypt.length);
        System.arraycopy(ProxyConstants.MARK_BYTE, 0, data, encrypt.length + lengthField.length, ProxyConstants.MARK_BYTE.length);
        return data;
    }
}
