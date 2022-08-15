package me.jar.nat.message;

import com.alibaba.fastjson.JSON;
import me.jar.nat.constants.NatMsgType;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public class NatMsg {
    private NatMsgType type;
    private Map<String, Object> metaData;
    private byte[] date;

    public NatMsgType getType() {
        return type;
    }

    public void setType(NatMsgType type) {
        this.type = type;
    }

    public Map<String, Object> getMetaData() {
        return metaData;
    }

    public void setMetaData(Map<String, Object> metaData) {
        this.metaData = metaData;
    }

    public byte[] getDate() {
        return date;
    }

    public void setDate(byte[] date) {
        this.date = date;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NatMsg that = (NatMsg) o;
        return type == that.type && Objects.equals(metaData, that.metaData) && Arrays.equals(date, that.date);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(type, metaData);
        result = 31 * result + Arrays.hashCode(date);
        return result;
    }

    @Override
    public String toString() {
        return "NatMsg{" +
                "type=" + type.getType() +
                ", metaData=" + JSON.toJSONString(metaData) +
                ", date[]_length=" + (date == null ? 0 : date.length) +
                '}';
    }
}
