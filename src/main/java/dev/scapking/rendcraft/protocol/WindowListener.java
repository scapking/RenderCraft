package dev.scapking.rendcraft.protocol;

/**
 * 窗口变化监听器。
 * 用于感知 Wayland/X11 窗口生命周期和属性变化。
 */
public interface WindowListener {
    void onWindowCreated(WindowHandle handle);
    void onWindowClosed(WindowHandle handle);
    void onWindowMoved(WindowHandle handle, int x, int y);
    void onWindowResized(WindowHandle handle, int width, int height);
    void onWindowTitleChanged(WindowHandle handle, String title);
}
