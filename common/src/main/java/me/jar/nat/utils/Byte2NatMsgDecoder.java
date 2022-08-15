package me.jar.nat.utils;

import com.alibaba.fastjson.JSON;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import me.jar.nat.constants.NatMsgType;
import me.jar.nat.message.NatMsg;

import java.util.List;
import java.util.Map;

public class Byte2NatMsgDecoder extends MessageToMessageDecoder<ByteBuf> {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
        int type = msg.readInt();
        NatMsgType natMsgType = NatMsgType.toNatMsgType(type);

        int metaDataLength = msg.readInt();
        byte[] metaDataBytes = new byte[metaDataLength];
        msg.readBytes(metaDataBytes);
        Map<String, Object> metaData = JSON.parseObject(metaDataBytes, Map.class);

        byte[] data = null;
        if (msg.isReadable()) {
            data = ByteBufUtil.getBytes(msg);
        }

        NatMsg natMsg = new NatMsg();
        natMsg.setType(natMsgType);
        natMsg.setMetaData(metaData);
        natMsg.setDate(data);

        out.add(natMsg);
    }
}
