package dev.scapking.rendcraft.window;

import dev.scapking.rendcraft.protocol.FrameSnapshot;
import dev.scapking.rendcraft.protocol.ProtocolBackend;
import dev.scapking.rendcraft.protocol.ProtocolException;
import dev.scapking.rendcraft.protocol.WindowHandle;
import dev.scapking.rendcraft.protocol.WindowMetadata;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvironmentAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 窗口状态机实现。
 * 管理 ProtocolBackend 与 RenderCraft 上层之间的窗口状态。
 */
@Environment(Environment.Access.ThreadSafe)
public class WindowManager implements EnvironmentAccessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(WindowManager.class);

    private final Map<WindowHandle, WindowState> windowStates = new ConcurrentHashMap<>();
    private final Map<WindowHandle, WindowMetadata> windowMetadata = new ConcurrentHashMap<>();
    private ProtocolBackend backend;

    public WindowManager() {
    }

    public void setBackend(ProtocolBackend backend) {
        this.backend = backend;
    }

    public boolean registerWindow(WindowHandle handle, WindowMetadata metadata) {
        if (handle == null || metadata == null) {
            return false;
        }
        windowMetadata.put(handle, metadata);
        windowStates.put(handle, WindowState.ACTIVE);
        LOGGER.info("Window registered: {}", handle);
        return true;
    }

    public WindowState getState(WindowHandle handle) {
        return windowStates.getOrDefault(handle, WindowState.NONE);
    }

    public WindowMetadata getMetadata(WindowHandle handle) {
        return windowMetadata.get(handle);
    }

    public void setState(WindowHandle handle, WindowState newState) {
        WindowState oldState = windowStates.getOrDefault(handle, WindowState.NONE);
        if (oldState == newState) {
            return;
        }
        LOGGER.info("Window state transition: {} -> {} (handle: {})", oldState, newState, handle);
        windowStates.put(handle, newState);
    }

    public void requestClose(WindowHandle handle) {
        WindowState currentState = getState(handle);
        if (currentState == WindowState.DESTROYED || currentState == WindowState.NONE) {
            LOGGER.warn("Attempted to close window in invalid state: {} (handle: {})", currentState, handle);
            return;
        }
        setState(handle, WindowState.CLOSING);
        try {
            if (backend != null) {
                backend.closeWindow(handle);
            }
        } catch (ProtocolException e) {
            LOGGER.error("Failed to close window via backend: {}", handle, e);
        } finally {
            setState(handle, WindowState.DESTROYED);
            windowMetadata.remove(handle);
            windowStates.remove(handle);
        }
    }

    public void requestHide(WindowHandle handle) {
        WindowState currentState = getState(handle);
        if (currentState != WindowState.ACTIVE && currentState != WindowState.HIDDEN) {
            LOGGER.warn("Cannot hide window in state: {} (handle: {})", currentState, handle);
            return;
        }
        setState(handle, WindowState.HIDDEN);
        try {
            if (backend != null) {
                backend.setWindowVisible(handle, false);
            }
        } catch (ProtocolException e) {
            LOGGER.error("Failed to hide window via backend: {}", handle, e);
        }
    }

    public void requestShow(WindowHandle handle) {
        WindowState currentState = getState(handle);
        if (currentState != WindowState.HIDDEN && currentState != WindowState.ACTIVE) {
            LOGGER.warn("Cannot show window in state: {} (handle: {})", currentState, handle);
            return;
        }
        setState(handle, WindowState.ACTIVE);
        try {
            if (backend != null) {
                backend.setWindowVisible(handle, true);
            }
        } catch (ProtocolException e) {
            LOGGER.error("Failed to show window via backend: {}", handle, e);
        }
    }

    public FrameSnapshot captureFrame(WindowHandle handle) throws ProtocolException {
        if (backend == null) {
            throw new ProtocolException("Backend not set");
        }
        return backend.captureFrame(handle);
    }

    public WindowHandle[] listWindows() {
        if (backend == null) {
            return new WindowHandle[0];
        }
        return backend.listWindows();
    }
}
