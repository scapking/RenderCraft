package dev.scapking.rendcraft;

import dev.scapking.rendcraft.protocol.ProtocolBackend;
import dev.scapking.rendcraft.protocol.ProtocolException;
import dev.scapking.rendcraft.protocol.WindowHandle;
import dev.scapking.rendcraft.protocol.WindowMetadata;
import dev.scapking.rendcraft.window.WindowManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Single source of truth for runtime singletons the command tree and
 * any future KeyBindings / IMC handlers will need to reach. The mod
 * initializer wires the {@link WindowManager} and the active
 * {@link ProtocolBackend} here; the rest of the code reads them
 * through {@link #get()} without depending on the fabric
 * mod-instance / loader-instance dance.
 *
 * <p>Only one backend is active at a time. The protocol-swap command
 * ({@code /rc protocol switch <name>}) replaces the reference
 * atomically so the manager sees either the old or the new backend
 * for any given frame, never a half-swapped state.
 */
public final class RenderCraftRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger("rendcraft-runtime");

    private static final AtomicReference<WindowManager> WINDOW_MANAGER = new AtomicReference<>();
    private static final AtomicReference<ProtocolBackend> BACKEND = new AtomicReference<>();

    private RenderCraftRuntime() {}

    public static WindowManager getWindowManager() {
        WindowManager wm = WINDOW_MANAGER.get();
        if (wm == null) {
            throw new IllegalStateException("WindowManager not yet initialized");
        }
        return wm;
    }

    public static ProtocolBackend getBackend() {
        return BACKEND.get();
    }

    public static void setWindowManager(WindowManager wm) {
        WINDOW_MANAGER.set(wm);
        LOGGER.info("WindowManager registered with runtime");
    }

    public static void setBackend(ProtocolBackend backend) {
        ProtocolBackend previous = BACKEND.getAndSet(backend);
        WindowManager wm = WINDOW_MANAGER.get();
        if (wm != null) {
            wm.setBackend(backend);
        }
        if (previous != null && previous != backend) {
            LOGGER.info("Disposing previous backend ({})", previous.getProtocolName());
            previous.dispose();
        }
        LOGGER.info("Backend set to {}", backend == null ? "<none>" : backend.getProtocolName());
    }

    /**
     * Re-sync the {@link WindowManager}'s known window list with what
     * the backend currently reports. Called after protocol swaps and
     * on demand from {@code /rc window list}. Errors are logged and
     * swallowed — the caller only cares about whether the manager now
     * has the latest snapshot.
     */
    public static void refreshWindows() {
        WindowManager wm = WINDOW_MANAGER.get();
        ProtocolBackend backend = BACKEND.get();
        if (wm == null || backend == null) {
            return;
        }
        WindowHandle[] handles;
        try {
            handles = backend.listWindows();
        } catch (ProtocolException e) {
            LOGGER.warn("refreshWindows: backend.listWindows failed: {}", e.getMessage());
            return;
        }
        for (WindowHandle handle : handles) {
            try {
                WindowMetadata meta = backend.getMetadata(handle);
                wm.registerWindow(handle, meta);
            } catch (ProtocolException e) {
                LOGGER.warn("Failed to refresh window {}: {}", handle, e.getMessage());
            }
        }
        LOGGER.info("Refreshed {} window(s) from backend", handles.length);
    }
}
