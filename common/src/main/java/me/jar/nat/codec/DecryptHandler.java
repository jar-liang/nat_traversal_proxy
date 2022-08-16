package me.jar.nat.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.util.ReferenceCountUtil;
import me.jar.nat.constants.DecryptDecoderState;
import me.jar.nat.constants.ProxyConstants;
import me.jar.nat.utils.AESUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * @Description
 * @Date 2021/4/23-20:07
 */
public class DecryptHandler extends ReplayingDecoder<DecryptDecoderState> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DecryptHandler.class);

    private String password;
    private int length;

    public DecryptHandler() {
        super(DecryptDecoderState.READ_LENGTH);
        String password = ProxyConstants.PROPERTY.get(ProxyConstants.PROPERTY_NAME_KEY);
        if (password == null || password.length() == 0) {
            throw new IllegalArgumentException("Illegal key from property");
        }
        this.password = password;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        switch (state()) {
            case READ_LENGTH:
                length = in.readInt();
                checkpoint(DecryptDecoderState.READ_CONTENT);
            case READ_CONTENT:
                ByteBuf byteBuf = in.readBytes(length);
                try {
                    checkpoint(DecryptDecoderState.READ_LENGTH);
                    // 先判断是否有特定标识字节，没有则直接关闭通道
                    byte[] markBytes = new byte[4];
                    int readableBytes = byteBuf.readableBytes();
                    byteBuf.getBytes(readableBytes - 4, markBytes);
                    for (int i = 0; i < markBytes.length; i++) {
                        if (markBytes[i] != ProxyConstants.MARK_BYTE[i]) {
                            LOGGER.info("===Illegal data from ip: {}", ctx.channel().remoteAddress());
                            ctx.close();
                            return;
                        }
                    }
                    byte[] encryptSource = new byte[readableBytes - 4];
                    byteBuf.readBytes(encryptSource, 0, readableBytes - 4);
                    try {
                        byte[] decryptBytes = AESUtil.decrypt(encryptSource, password);
                        out.add(Unpooled.wrappedBuffer(decryptBytes));
                    } catch (GeneralSecurityException | UnsupportedEncodingException e) {
                        LOGGER.error("===Decrypt data failed. detail: {}", e.getMessage());
                        ctx.close();
                    }
                } finally {
                    ReferenceCountUtil.release(byteBuf);
                }
                break;
            default:
                LOGGER.error("===It is a illegal decoder state!");
        }
    }
}
