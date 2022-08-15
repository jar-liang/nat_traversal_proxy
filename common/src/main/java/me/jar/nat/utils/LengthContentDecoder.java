package me.jar.nat.utils;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import me.jar.nat.constants.DecodeState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class LengthContentDecoder extends ReplayingDecoder<DecodeState> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LengthContentDecoder.class);
    private int length = 0;
    public LengthContentDecoder() {
        super(DecodeState.LENGTH);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        switch (state()) {
            case LENGTH:
                length = in.readInt();
                if (length < 0 || length > 104857600) {
                    LOGGER.error("LengthContentDecoder decode failed, length is illegal! length: " + length);
                    int discard = length - in.readableBytes();
                    if (discard < 0) {
                        // buffer contains more bytes then the frameLength so we can discard all now
                        in.skipBytes(length);
                    } else {
                        // Enter the discard mode and discard everything received so far.
                        in.skipBytes(in.readableBytes());
                    }
                    ctx.close();
                    return;
                }
                checkpoint(DecodeState.CONTENT);
            case CONTENT:
                ByteBuf byteBuf = in.readBytes(length);
                checkpoint(DecodeState.LENGTH);
                out.add(byteBuf);
                break;
            default:
                LOGGER.error("===[LengthContentDecoder] - It is a illegal decoder state!");
        }
    }
}
