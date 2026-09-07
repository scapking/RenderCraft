package dev.scapking.rendcraft.protocol;

import dev.scapking.rendcraft.compositor.CompositorConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Wayland 协议适配器。
 *
 * <p>Two backends are supported:
 * <ul>
 *   <li>The legacy text-protocol {@link CompositorConnection} for
 *       completeness; it is no longer the recommended path but is
 *       still wired so old {@code /rc share x11 list} smoke tests
 *       keep working.</li>
 *   <li>The {@code WaylandPortalClient} that drives the freedesktop
 *       ScreenCast v6 portal over D-Bus. This is the path real
 *       captures will go through; the recommended no-argument
 *       {@link #WaylandAdapter()} constructor instantiates it.
 *       The portal client lives in a separate {@code dbusClient}
 *       source set so that the {@code org.freedesktop.dbus.*}
 *       dependency does not have to be visible from the main
 *       Minecraft source set, and is reached here through
 *       reflection.</li>
 * </ul>
 *
 * <p>Frame capture is still a stub: the portal hands back a PipeWire
 * file descriptor that only a native (libpipewire) or a forked
 * helper process can drive.
 */
public class WaylandAdapter implements ProtocolBackend {
    private static final Logger LOGGER = LoggerFactory.getLogger(WaylandAdapter.class);
    private boolean initialized = false;
    private WindowListener listener;
    private final CompositorConnection connection;

    /**
     * The portal client, reached through reflection so the main
     * source set does not need dbus-java on its compile classpath.
     * Methods invoked reflectively: {@code createSession},
     * {@code closeCurrentSession}, {@code close}.
     */
    private final Object portalClient;
    private final Method portalCreateSession;
    private final Method portalCloseCurrentSession;
    private final Method portalClose;
    private final Method portalCaptureFrame;

    public WaylandAdapter(String compositorPath) {
        this.connection = new CompositorConnection(compositorPath);
        this.portalClient = null;
        this.portalCreateSession = null;
        this.portalCloseCurrentSession = null;
        this.portalClose = null;
        this.portalCaptureFrame = null;
    }

    public WaylandAdapter(String compositorPath, int port) {
        this.connection = new CompositorConnection(compositorPath, port);
        this.portalClient = null;
        this.portalCreateSession = null;
        this.portalCloseCurrentSession = null;
        this.portalClose = null;
        this.portalCaptureFrame = null;
    }

    public WaylandAdapter(String compositorPath, String socketPath) {
        this.connection = new CompositorConnection(compositorPath, socketPath);
        this.portalClient = null;
        this.portalCreateSession = null;
        this.portalCloseCurrentSession = null;
        this.portalClose = null;
        this.portalCaptureFrame = null;
    }

    /** Recommended constructor: drive the freedesktop portal directly. */
    public WaylandAdapter() {
        this.connection = null;
        Object client = null;
        Method create = null;
        Method closeCurrent = null;
        Method closeAll = null;
        Method captureFrame = null;
        try {
            Class<?> portalClass = Class.forName(
                    "dev.scapking.rendcraft.protocol.wayland.WaylandPortalClient");
            client = portalClass.getDeclaredConstructor().newInstance();
            create = portalClass.getMethod("createSession");
            closeCurrent = portalClass.getMethod("closeCurrentSession");
            closeAll = portalClass.getMethod("close");
            captureFrame = portalClass.getMethod("captureFrame");
            try {
                create.invoke(client);
            } catch (Exception e) {
                LOGGER.warn("Portal CreateSession failed: {}", unwrap(e).getMessage());
            }
        } catch (Throwable t) {
            LOGGER.warn("WaylandPortalClient unavailable, WaylandAdapter in stub-only mode: {}",
                    t.getMessage());
            client = null;
            create = null;
            closeCurrent = null;
            closeAll = null;
            captureFrame = null;
        }
        this.portalClient = client;
        this.portalCreateSession = create;
        this.portalCloseCurrentSession = closeCurrent;
        this.portalClose = closeAll;
        this.portalCaptureFrame = captureFrame;
    }

    private static Throwable unwrap(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t;
    }

    @Override
    public boolean initialize() {
        if (initialized) return true;
        if (connection != null) {
            try {
                if (!connection.start()) {
                    throw new RuntimeException("Failed to start compositor connection");
                }
            } catch (Exception e) {
                throw new RuntimeException("WaylandAdapter initialization failed", e);
            }
        }
        initialized = true;
        LOGGER.info("WaylandAdapter initialized (portal={}, legacy connection={})",
                portalClient != null ? "ready" : "absent",
                connection != null ? "ready" : "absent");
        return true;
    }

    @Override
    public void dispose() {
        initialized = false;
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception e) {
                LOGGER.warn("Error closing compositor connection", e);
            }
        }
        if (portalClient != null && portalClose != null) {
            try {
                portalClose.invoke(portalClient);
            } catch (Exception e) {
                LOGGER.debug("Error closing portal client: {}", e.getMessage());
            }
        }
        LOGGER.info("WaylandAdapter disposed");
    }

    @Override
    public String getProtocolName() {
        return "wayland";
    }

    @Override
    public WindowHandle[] listWindows() throws ProtocolException {
        if (!initialized) throw new ProtocolException("WaylandAdapter not initialized");
        if (connection == null && portalClient == null) {
            return new WindowHandle[0];
        }
        try {
            if (connection != null) {
                var infos = connection.listWindows();
                List<WindowHandle> handles = new ArrayList<>(infos.size());
                for (CompositorConnection.WindowInfo info : infos) {
                    handles.add(new WindowHandle(info.handle, ProtocolType.WAYLAND));
                }
                return handles.toArray(new WindowHandle[0]);
            }
        } catch (IOException e) {
            throw new ProtocolException("Failed to list windows", e);
        }
        return new WindowHandle[0];
    }

    @Override
    public WindowMetadata getMetadata(WindowHandle handle) throws ProtocolException {
        if (!initialized) throw new ProtocolException("WaylandAdapter not initialized");
        LOGGER.debug("getMetadata stub for handle={}", handle);
        return new WindowMetadata(handle, "wayland-title", 1280, 720, true);
    }

    @Override
    public FrameSnapshot captureFrame(WindowHandle handle) throws ProtocolException {
        if (!initialized) throw new ProtocolException("WaylandAdapter not initialized");
        if (portalClient == null && connection == null) {
            throw new ProtocolException(
                    "Wayland capture unavailable on this host (no portal, no legacy connection). "
                            + "Make sure xdg-desktop-portal is running.");
        }
        if (connection != null) {
            try {
                CompositorConnection.FrameCapture fc = connection.captureWindow(handle.getId());
                return new FrameSnapshot(
                        System.currentTimeMillis(),
                        fc.width,
                        fc.height,
                        fc.data,
                        fc.format);
            } catch (IOException e) {
                throw new ProtocolException("Failed to capture frame for " + handle, e);
            }
        }
        // Portal path: ask the portal client to grab a frame.
        // WaylandPortalClient.captureFrame shells out to `grim` and
        // parses the resulting PPM into an RGBA8 buffer, so we get
        // a real FrameSnapshot here. If grim is not installed (e.g.
        // on GNOME) the portal client raises a ProtocolException we
        // pass through unchanged.
        if (portalCaptureFrame == null) {
            throw new ProtocolException(
                "WaylandAdapter.captureFrame has no portal client bound. "
                    + "Construct via new WaylandAdapter() so the dbus-send "
                    + "and grim paths are wired up.");
        }
        try {
            Object rawFrame = portalCaptureFrame.invoke(portalClient);
            if (!(rawFrame instanceof dev.scapking.rendcraft.protocol.wayland.WaylandFrameGrabber.Frame)) {
                throw new ProtocolException(
                        "Unexpected return type from WaylandPortalClient.captureFrame: "
                                + (rawFrame == null ? "null" : rawFrame.getClass().getName()));
            }
            dev.scapking.rendcraft.protocol.wayland.WaylandFrameGrabber.Frame frame =
                    (dev.scapking.rendcraft.protocol.wayland.WaylandFrameGrabber.Frame) rawFrame;
            return new FrameSnapshot(
                    System.currentTimeMillis(),
                    frame.width(),
                    frame.height(),
                    frame.rgba(),
                    "rgba");
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new ProtocolException("Wayland frame capture failed: " + cause.getMessage(), cause);
        } catch (IllegalAccessException e) {
            throw new ProtocolException("Reflection error while invoking captureFrame", e);
        } catch (Exception e) {
            throw new ProtocolException("Unexpected error while invoking captureFrame", e);
        }
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
