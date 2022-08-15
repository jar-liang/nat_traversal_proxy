package me.jar.nat.utils;

import com.alibaba.fastjson.JSON;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import me.jar.nat.constants.ProxyConstants;
import me.jar.nat.message.NatMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

public class NatMsg2ByteWithEncryptEncoder extends MessageToByteEncoder<NatMsg> {
    private static final Logger LOGGER = LoggerFactory.getLogger(NatMsg2ByteWithEncryptEncoder.class);
    private String password;

    public NatMsg2ByteWithEncryptEncoder() {
        String password = ProxyConstants.PROPERTY.get(ProxyConstants.PROPERTY_NAME_KEY);
        if (password == null || password.length() == 0) {
            throw new IllegalArgumentException("Illegal key from property");
        }
        this.password = password;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, NatMsg msg, ByteBuf out) {
        int type = msg.getType().getType();
        Map<String, Object> metaData = msg.getMetaData() != null ? msg.getMetaData() : new HashMap<>();
        byte[] metaDataBytes = JSON.toJSONBytes(metaData);
        ByteBuf byteBuf = Unpooled.copyInt(type, metaDataBytes.length);

        if (msg.getDate() != null && msg.getDate().length > 0) {
            byte[] date = msg.getDate();
            try {
                byte[] encrypt = AESUtil.encrypt(date, password);
                // fix: 添加特定标识字节，防止解密端不停解密导致CPU占用过高
                ByteBuf wrappedBuffer = Unpooled.wrappedBuffer(encrypt, ProxyConstants.MARK_BYTE);
                ByteBuf byteBufInt = Unpooled.copyInt(wrappedBuffer.readableBytes());
                ByteBuf typeAndMetaDataAndData = Unpooled.wrappedBuffer(byteBuf, Unpooled.wrappedBuffer(metaDataBytes), byteBufInt, wrappedBuffer);
                out.writeInt(typeAndMetaDataAndData.readableBytes());
                out.writeBytes(typeAndMetaDataAndData);
            } catch (GeneralSecurityException | UnsupportedEncodingException e) {
                LOGGER.error("===Decrypt data failed. detail: {}", e.getMessage());
                ctx.close();
            }
        } else {
            ByteBuf typeAndMetaData = Unpooled.wrappedBuffer(byteBuf, Unpooled.wrappedBuffer(metaDataBytes));
            out.writeInt(typeAndMetaData.readableBytes());
            out.writeBytes(typeAndMetaData);
        }
    }
}
