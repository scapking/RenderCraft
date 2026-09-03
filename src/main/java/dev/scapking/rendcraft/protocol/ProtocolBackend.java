package dev.scapking.rendcraft.protocol;

/**
 * 协议后端抽象接口。
 * Wayland、X11 或未来的其他协议适配器都实现此接口，
 * 上层不直接感知具体协议。
 */
public interface ProtocolBackend {

    /**
     * 初始化协议后端。返回是否成功。
     */
    boolean initialize();

    /**
     * 释放协议后端资源。
     */
    void dispose();

    /**
     * 枚举当前可用的窗口。
     */
    WindowHandle[] listWindows();

    /**
     * 获取指定窗口的元数据。
     */
    WindowMetadata getMetadata(WindowHandle handle);

    /**
     * 捕获指定窗口的一帧，返回帧快照。
     * 若窗口不存在或当前不可捕获，抛出 ProtocolException。
     */
    FrameSnapshot captureFrame(WindowHandle handle) throws ProtocolException;

    /**
     * 请求关闭指定窗口。
     */
    void closeWindow(WindowHandle handle) throws ProtocolException;

    /**
     * 设置窗口显示状态。
     */
    void setWindowVisible(WindowHandle handle, boolean visible) throws ProtocolException;

    /**
     * 注册窗口变化监听器。
     */
    void addWindowListener(WindowListener listener);

    /**
     * 移除窗口变化监听器。
     */
    void removeWindowListener(WindowListener listener);
}
