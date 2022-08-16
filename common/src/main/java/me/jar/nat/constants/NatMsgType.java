package me.jar.nat.constants;

import me.jar.nat.exception.NatProxyException;

public enum NatMsgType {
    REGISTER(1),
    REGISTER_RESULT(2),
    CONNECT(3),
    DISCONNECT(4),
    DATA(5),
    KEEPALIVE(6)
    ;

    int type;

    NatMsgType(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }

    public static NatMsgType toNatMsgType(int code) throws NatProxyException {
        NatMsgType[] values = NatMsgType.values();
        for (NatMsgType value : values) {
            if (value.type == code) {
                return value;
            }
        }
        throw new NatProxyException("unavailable transfer message type code: " + code);
    }
}
