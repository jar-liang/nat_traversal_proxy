package me.jar.nat.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import me.jar.nat.constants.ProxyConstants;
import me.jar.nat.utils.AESUtil;
import me.jar.nat.utils.BuildDataUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;

/**
 * @Description
 * @Date 2021/4/23-19:59
 */
public class ServerEncryptHandler extends MessageToByteEncoder<byte[]> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerEncryptHandler.class);

    private String password;

    public ServerEncryptHandler() {
        String password = ProxyConstants.PROPERTY.get(ProxyConstants.PROPERTY_NAME_KEY);
        if (password == null || password.length() == 0) {
            throw new IllegalArgumentException("Illegal key from property");
        }
        this.password = password;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, byte[] msg, ByteBuf out) {
        try {
            byte[] encrypt = AESUtil.encrypt(msg, password);
            byte[] data = BuildDataUtil.buildLengthAndMarkWithData(encrypt);
            out.writeBytes(data);
        } catch (GeneralSecurityException | UnsupportedEncodingException e) {
            LOGGER.error("===Decrypt data failed. detail: {}", e.getMessage());
            ctx.close();
        }
    }
}
