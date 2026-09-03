package dev.scapking.rendcraft.protocol;

import java.util.Objects;

/**
 * 窗口句柄——在协议后端中唯一标识一个窗口。
 * 对上层透明，不暴露原始协议句柄。
 */
public final class WindowHandle {
    private final String id;
    private final ProtocolType protocolType;

    public WindowHandle(String id, ProtocolType protocolType) {
        this.id = Objects.requireNonNull(id, "id");
        this.protocolType = Objects.requireNonNull(protocolType, "protocolType");
    }

    public String getId() {
        return id;
    }

    public ProtocolType getProtocolType() {
        return protocolType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WindowHandle)) return false;
        WindowHandle that = (WindowHandle) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "WindowHandle{" +
                "id='" + id + '\'' +
                ", protocol=" + protocolType +
                '}';
    }
}
