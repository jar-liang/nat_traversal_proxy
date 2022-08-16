package me.jar.nat.codec;

import com.alibaba.fastjson.JSON;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import me.jar.nat.constants.ProxyConstants;
import me.jar.nat.constants.NatMsgType;
import me.jar.nat.message.NatMsg;
import me.jar.nat.utils.AESUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;

public class Byte2TransferMsgWithDecryptDecoder extends MessageToMessageDecoder<ByteBuf> {
    private static final Logger LOGGER = LoggerFactory.getLogger(Byte2TransferMsgWithDecryptDecoder.class);

    private String password;

    public Byte2TransferMsgWithDecryptDecoder() {
        String password = ProxyConstants.PROPERTY.get(ProxyConstants.PROPERTY_NAME_KEY);
        if (password == null || password.length() == 0) {
            throw new IllegalArgumentException("Illegal key from property");
        }
        this.password = password;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
        int type = msg.readInt();
        NatMsgType natMsgType = NatMsgType.toNatMsgType(type);

        int metaDataLength = msg.readInt();
        byte[] metaDataBytes = new byte[metaDataLength];
        msg.readBytes(metaDataBytes);
        Map<String, Object> metaData = JSON.parseObject(metaDataBytes, Map.class);

        byte[] decryptBytes = null;
        if (msg.isReadable()) {
            int dataLength = msg.readInt();
            byte[] data = ByteBufUtil.getBytes(msg);
            // 先判断是否有特定标识字节，没有则直接关闭通道
            int readableBytes = data.length;
            if (dataLength != readableBytes) {
                LOGGER.error("read data's length is not equals getBytes true length. dataLength:{}, readable bytes length:{}", dataLength, readableBytes);
                ctx.close();
                return;
            }
            int markLength = ProxyConstants.MARK_BYTE.length;
            if (readableBytes < markLength) {
                LOGGER.error("data bytes length is less than {}. ip {}", markLength, ctx.channel().remoteAddress());
                ctx.close();
                return;
            }
            for (int i = 0; i < markLength; i++) {
                if (data[readableBytes + i - markLength] != ProxyConstants.MARK_BYTE[i]) {
                    LOGGER.info("===Illegal data from ip: {}", ctx.channel().remoteAddress());
                    ctx.close();
                    return;
                }
            }
            byte[] tempData = new byte[readableBytes - markLength];
            System.arraycopy(data, 0, tempData, 0, readableBytes - markLength);
            try {
                decryptBytes = AESUtil.decrypt(tempData, password);
            } catch (GeneralSecurityException | UnsupportedEncodingException e) {
                LOGGER.error("===Decrypt data failed. detail: {}", e.getMessage());
                ctx.close();
                return;
            }
        }

        NatMsg natMsg = new NatMsg();
        natMsg.setType(natMsgType);
        natMsg.setMetaData(metaData);
        natMsg.setDate(decryptBytes);

        out.add(natMsg);
    }
}
