package dev.scapking.rendcraft.protocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Wayland 协议适配器。
 * 基于 Smithay 或原生 Wayland 获取窗口列表和帧。
 * 当前为骨架，待接入真实 Wayland 后端。
 */
public class WaylandAdapter implements ProtocolBackend {
    private static final Logger LOGGER = LoggerFactory.getLogger(WaylandAdapter.class);
    private boolean initialized = false;
    private WindowListener listener;
    private final CompositorConnection connection;

    public WaylandAdapter(String compositorPath) {
        this.connection = new CompositorConnection(compositorPath);
    }

    public WaylandAdapter(String compositorPath, int port) {
        this.connection = new CompositorConnection(compositorPath, port);
    }

    public WaylandAdapter(String compositorPath, String socketPath) {
        this.connection = new CompositorConnection(compositorPath, socketPath);
    }

    @Override
    public boolean initialize() {
        if (initialized) return true;
        try {
            if (!connection.start()) {
                throw new RuntimeException("Failed to start compositor connection");
            }
        } catch (Exception e) {
            throw new RuntimeException("WaylandAdapter initialization failed", e);
        }
        initialized = true;
        LOGGER.info("WaylandAdapter initialized with connection to compositor");
        return true;
    }

    @Override
    public void dispose() {
        initialized = false;
        try {
            connection.close();
        } catch (Exception e) {
            LOGGER.warn("Error closing compositor connection", e);
        }
        LOGGER.info("WaylandAdapter disposed");
    }

    @Override
    public WindowHandle[] listWindows() {
        if (!initialized) throw new ProtocolException("WaylandAdapter not initialized");
        if (connection == null || !connection.isConnected()) {
            throw new ProtocolException("WaylandAdapter not connected");
        }
        try {
            List<CompositorConnection.WindowInfo> infos = connection.listWindows();
            List<WindowHandle> handles = new ArrayList<>(infos.size());
            for (CompositorConnection.WindowInfo info : infos) {
                handles.add(new WindowHandle(info.handle, ProtocolType.WAYLAND));
            }
            return handles.toArray(new WindowHandle[0]);
        } catch (IOException e) {
            throw new ProtocolException("Failed to list windows", e);
        }
    }

    @Override
    public WindowMetadata getMetadata(WindowHandle handle) {
        if (!initialized) throw new ProtocolException("WaylandAdapter not initialized");
        LOGGER.debug("getMetadata stub for handle={}", handle);
        return new WindowMetadata(handle, "wayland-title", 1280, 720, true);
    }

    @Override
    public FrameSnapshot captureFrame(WindowHandle handle) throws ProtocolException {
        if (!initialized) throw new ProtocolException("WaylandAdapter not initialized");
        if (connection == null || !connection.isConnected()) {
            throw new ProtocolException("WaylandAdapter not connected");
        }
        try {
            CompositorConnection.FrameCapture fc = connection.captureWindow(handle.getId());
            return new FrameSnapshot(
                System.currentTimeMillis(),
                fc.width,
                fc.height,
                fc.data,
                fc.format
            );
        } catch (IOException e) {
            throw new ProtocolException("Failed to capture frame for " + handle, e);
        }
    }

    @Override
    public String getProtocolName() {
        return "wayland";
    }

    @Override
    public void closeWindow(WindowHandle handle) throws ProtocolException {
        LOGGER.info("closeWindow stub for handle={}", handle);
    }

    @Override
    public void setWindowVisible(WindowHandle handle, boolean visible) throws ProtocolException {
        LOGGER.info("setWindowVisible stub: handle={} visible={}", handle, visible);
    }

    @Override
    public void addWindowListener(WindowListener listener) {
        this.listener = listener;
    }

    @Override
    public void removeWindowListener(WindowListener listener) {
        if (this.listener == listener) this.listener = null;
    }
}
