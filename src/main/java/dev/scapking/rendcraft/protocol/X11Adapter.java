package dev.scapking.rendcraft.protocol;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * X11 協議適配器。
 * 透過 JNA XGetImage / XQueryTree 獲取 X11 窗口列表與幀。
 * 
 * 實現沿用 waylandcraft 的 X11 捕捉思路：
 * - 枚舉根窗口子樹，讀取 WM_NAME / WM_CLASS / _NET_WM_PID
 * - 幀捕捉使用 XGetImage(ZPixmap) 拿像素，並轉為 RGBA ByteBuffer
 * - 每次操作都開關獨立 X 連接，不保持跨調用狀態
 */
public class X11Adapter implements ProtocolBackend {

    private static final Logger LOGGER = LoggerFactory.getLogger("rendcraft-x11");

    private boolean initialized = false;
    private WindowListener listener;

    // X11 顯示名，null = 交給 XOpenDisplay 使用環境變量 / 默認顯示
    private final String displayName;

    public X11Adapter() {
        this(null);
    }

    public X11Adapter(String displayName) {
        this.displayName = displayName;
    }

    private interface X11 extends Library {
        X11 INSTANCE = Native.load("X11", X11.class);

        Pointer XOpenDisplay(String displayName);
        int XCloseDisplay(Pointer display);
        long XDefaultRootWindow(Pointer display);

        int XQueryTree(Pointer display, long window, long[] rootReturn, long[] parentReturn,
                       Pointer[] childrenReturn, int[] nchildrenReturn);
        int XFree(Pointer data);

        int XGetGeometry(Pointer display, long window, long[] rootReturn, int[] xReturn, int[] yReturn,
                         int[] widthReturn, int[] heightReturn, int[] borderWidthReturn, int[] depthReturn);

        int XTranslateCoordinates(Pointer display, long srcW, long destW, int srcX, int srcY,
                                  int[] destXReturn, int[] destYReturn, long[] childReturn);

        long XInternAtom(Pointer display, String atomName, int onlyIfExists);
        int XGetWindowProperty(Pointer display, long window, long property, long longOffset, long longLength,
                               int delete, long reqType, long[] actualTypeReturn, int[] actualFormatReturn,
                               long[] nitemsReturn, long[] bytesAfterReturn, Pointer[] propReturn);
        int XGetWMName(Pointer display, long window, Pointer[] textPropertyReturn);

        Pointer XGetImage(Pointer display, long window, int x, int y, int width, int height,
                          long planeMask, int format);
        int XDestroyImage(Pointer image);
    }

    private static final int XA_STRING = 31;
    private static final int XA_CARDINAL = 6;

    // XGetImage format
    private static final int ZPIXMAP = 2;

    // XGetWindowProperty delete mode: 0 = none, 1 = get property type as given, 2 = delete
    private static final int XGI_PROP_MODE_NONE = 0;  // prop_delete = False

    private static final X11 X11_LIB = loadX11();

    private static X11 loadX11() {
        try {
            return Native.load("X11", X11.class);
        } catch (Throwable t) {
            LOGGER.debug("libX11 not available on this platform (X11 sharing disabled)", t);
            return null;
        }
    }

    /** 窗口幾何信息（尺寸 + 根窗口座標） */
    public record Geometry(long xid, int width, int height, int rootX, int rootY) {}

    /** 抓幀結果：RGBA 像素（top-down，每像素 4 字節）+ 尺寸 + 窗口根座標 */
    public record Frame(ByteBuffer rgba, int width, int height, int rootX, int rootY) {}

    /** 一個 X11 頂層窗口的元信息 */
    public static class WindowInfo {
        public final String hash;     // 窗口 id（十六進制）
        public final String title;
        public final String appId;    // WM_CLASS 實例名
        public final int pid;
        public final int width;
        public final int height;
        public final boolean visible;

        public WindowInfo(String hash, String title, String appId, int pid,
                          int width, int height, boolean visible) {
            this.hash = hash;
            this.title = title;
            this.appId = appId;
            this.pid = pid;
            this.width = width;
            this.height = height;
            this.visible = visible;
        }
    }

    @Override
    public boolean initialize() {
        if (initialized) {
            return true;
        }
        LOGGER.info("X11Adapter initializing; displayName={}", displayName == null ? "(default DISPLAY)" : displayName);
        initialized = true;
        return true;
    }

    @Override
    public void dispose() {
        initialized = false;
        LOGGER.info("X11Adapter disposed");
    }

    @Override
    public WindowHandle[] listWindows() throws ProtocolException {
        if (!initialized) {
            throw new ProtocolException("X11Adapter not initialized");
        }
        List<WindowHandle> handles = new ArrayList<>();
        try {
            List<WindowInfo> windows = listX11Windows(displayName);
            for (WindowInfo w : windows) {
                handles.add(new WindowHandle(w.hash, ProtocolType.X11));
            }
        } catch (Exception e) {
            LOGGER.warn("X11Adapter.listWindows failed", e);
        }
        return handles.toArray(new WindowHandle[0]);
    }

    @Override
    public WindowMetadata getMetadata(WindowHandle handle) throws ProtocolException {
        if (!initialized) {
            throw new ProtocolException("X11Adapter not initialized");
        }
        if (X11_LIB == null) {
            return new WindowMetadata(handle, "x11-title", 1280, 720, true);
        }
        Pointer display = X11_LIB.XOpenDisplay(displayName);
        if (display == null) {
            return new WindowMetadata(handle, "x11-title", 1280, 720, true);
        }
        try {
            long xid = Long.parseUnsignedLong(handle.getId(), 16);
            WindowInfo w = describeWindow(display, xid);
            if (w != null) {
                return new WindowMetadata(handle, w.title, w.width, w.height, w.visible);
            }
        } catch (NumberFormatException e) {
            LOGGER.warn("X11Adapter.getMetadata invalid handle: {}", handle, e);
        } catch (Exception e) {
            LOGGER.warn("X11Adapter.getMetadata failed for {}", handle, e);
        } finally {
            X11_LIB.XCloseDisplay(display);
        }
        return new WindowMetadata(handle, "x11-title", 1280, 720, true);
    }

    @Override
    public FrameSnapshot captureFrame(WindowHandle handle) throws ProtocolException {
        if (!initialized) {
            throw new ProtocolException("X11Adapter not initialized");
        }
        try {
            long xid = Long.parseUnsignedLong(handle.getId(), 16);
            Frame frame = captureX11Rgba(displayName, xid);
            if (frame == null) {
                throw new ProtocolException("X11 capture returned no frame for " + handle);
            }
            ByteBuffer buf = frame.rgba();
            byte[] data = new byte[buf.remaining()];
            buf.get(data);
            return new FrameSnapshot(
                    System.currentTimeMillis(),
                    frame.width(),
                    frame.height(),
                    data,
                    "rgba"
            );
        } catch (ProtocolException e) {
            throw e;
        } catch (NumberFormatException e) {
            throw new ProtocolException("Invalid X11 handle: " + handle, e);
        } catch (Exception e) {
            throw new ProtocolException("Failed to capture frame for " + handle, e);
        }
    }

    @Override
    public String getProtocolName() {
        return "x11";
    }

    @Override
    public void closeWindow(WindowHandle handle) throws ProtocolException {
        LOGGER.info("closeWindow: handle={}", handle);
    }

    @Override
    public void setWindowVisible(WindowHandle handle, boolean visible) throws ProtocolException {
        LOGGER.info("setWindowVisible: handle={} visible={}", handle, visible);
    }

    @Override
    public void addWindowListener(WindowListener listener) {
        this.listener = listener;
    }

    @Override
    public void removeWindowListener(WindowListener listener) {
        if (this.listener == listener) {
            this.listener = null;
        }
    }

    // --------------------------------------------------------------------------
    // X11 輔助實現（沿用 waylandcraft X11WindowLister / X11Capture 思路）
    // --------------------------------------------------------------------------

    /**
     * 列出指定 X11 顯示上的頂層窗口。
     * displayName 為 null 時使用 XOpenDisplay(null) 語義（環境變量 / 默認顯示）。
     */
    public static List<WindowInfo> listX11Windows(String displayName) {
        List<WindowInfo> result = new ArrayList<>();
        if (X11_LIB == null) {
            LOGGER.debug("X11 library not available; skipping window enumeration");
            return result;
        }
        Pointer display = X11_LIB.XOpenDisplay(displayName);
        if (display == null) {
            LOGGER.debug("No X11 display available (requested '{}')", displayName);
            return result;
        }
        try {
            long root = X11_LIB.XDefaultRootWindow(display);
            Pointer[] children = new Pointer[1];
            int[] count = new int[1];

            if (X11_LIB.XQueryTree(display, root, new long[1], new long[1], children, count) != 0) {
                Pointer childPtr = children[0];
                if (childPtr != null && count[0] > 0) {
                    long[] childWindows = childPtr.getLongArray(0, count[0]);
                    for (long window : childWindows) {
                        WindowInfo info = describeWindow(display, window);
                        if (info != null) {
                            result.add(info);
                        }
                    }
                }
                if (childPtr != null) {
                    X11_LIB.XFree(childPtr);
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("Failed to enumerate X11 windows", t);
        } finally {
            X11_LIB.XCloseDisplay(display);
        }
        return result;
    }

    private static WindowInfo describeWindow(Pointer display, long window) {
        try {
            String title = fetchStringProperty(display, window, "WM_NAME");
            if (title == null || title.isEmpty()) {
                return null;
            }

            String wmClass = fetchStringProperty(display, window, "WM_CLASS");
            String appId = null;
            if (wmClass != null) {
                int nul = wmClass.indexOf('\0');
                appId = (nul >= 0 ? wmClass.substring(0, nul) : wmClass).trim();
                if (appId.isEmpty()) {
                    appId = null;
                }
            }

            int pid = 0;
            String pidStr = fetchCardinalProperty(display, window, "_NET_WM_PID");
            if (pidStr != null) {
                try {
                    pid = Integer.parseInt(pidStr);
                } catch (NumberFormatException ignored) {
                    // ignore
                }
            }

            // 幾何信息（若失敗也允許只返回標題窗口）
            int width = 0;
            int height = 0;
            boolean visible = false;
            // Geometry lookup requires opening a new X display; deferring it
            // here would make this static method non-static. Skipping geometry
            // keeps the listing path allocation-free; callers can query
            // X11Adapter.getGeometry(xid) explicitly when they need it.
            visible = (fetchStringProperty(display, window, "WM_NAME") != null);

            return new WindowInfo(Long.toHexString(window), title, appId, pid, width, height, visible);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 獲取窗口幾何信息（尺寸 + 根窗口座標）。
     */
    public static Geometry getGeometry(String displayName, long xid) {
        return getGeometry(displayName, X11_LIB, xid);
    }

    private static Geometry getGeometry(String displayName, X11 lib, long xid) {
        Pointer display = lib.XOpenDisplay(displayName);
        if (display == null) {
            return null;
        }
        try {
            long root = lib.XDefaultRootWindow(display);
            long[] rootRet = new long[1];
            int[] x = new int[1];
            int[] y = new int[1];
            int[] w = new int[1];
            int[] h = new int[1];
            int[] bw = new int[1];
            int[] depth = new int[1];

            if (lib.XGetGeometry(display, xid, rootRet, x, y, w, h, bw, depth) == 0) {
                return null;
            }

            int[] rootX = new int[1];
            int[] rootY = new int[1];
            long[] child = new long[1];
            if (lib.XTranslateCoordinates(display, xid, root, 0, 0, rootX, rootY, child) != 0) {
                return new Geometry(xid, w[0], h[0], rootX[0], rootY[0]);
            }
            return new Geometry(xid, w[0], h[0], x[0], y[0]);
        } catch (Throwable t) {
            LOGGER.warn("Failed to query X11 geometry for 0x{}", Long.toHexString(xid), t);
            return null;
        } finally {
            lib.XCloseDisplay(display);
        }
    }

    /**
     * 抓取指定 X11 窗口的 RGBA 幀（top-down）。
     * 窗口不可見/最小化/已銷毀時返回 null。
     */
    public static Frame captureX11Rgba(String displayName, long xid) {
        if (X11_LIB == null) {
            return null;
        }
        return captureX11Rgba(displayName, X11_LIB, xid);
    }

    private static Frame captureX11Rgba(String displayName, X11 lib, long xid) {
        Pointer display = lib.XOpenDisplay(displayName);
        if (display == null) {
            return null;
        }
        try {
            long[] rootRet = new long[1];
            int[] x = new int[1];
            int[] y = new int[1];
            int[] w = new int[1];
            int[] h = new int[1];
            int[] bw = new int[1];
            int[] depth = new int[1];

            if (lib.XGetGeometry(display, xid, rootRet, x, y, w, h, bw, depth) == 0) {
                return null;
            }
            if (w[0] <= 0 || h[0] <= 0) {
                return null;
            }

            Pointer img = lib.XGetImage(display, xid, 0, 0, w[0], h[0], 0xffffffffL, ZPIXMAP);
            if (img == null) {
                return null;
            }
            try {
                Frame frame = convertToRgba(img, w[0], h[0]);
                if (frame == null) {
                    return null;
                }

                int[] rootX = new int[1];
                int[] rootY = new int[1];
                long[] child = new long[1];
                if (lib.XTranslateCoordinates(display, xid, rootRet[0], 0, 0, rootX, rootY, child) != 0) {
                    return new Frame(frame.rgba(), frame.width(), frame.height(), rootX[0], rootY[0]);
                }
                return new Frame(frame.rgba(), frame.width(), frame.height(), x[0], y[0]);
            } finally {
                lib.XDestroyImage(img);
            }
        } catch (Throwable t) {
            LOGGER.debug("Failed to capture X11 window 0x{}", Long.toHexString(xid), t);
            return null;
        } finally {
            lib.XCloseDisplay(display);
        }
    }

    /**
     * 把 XImage 結構內存轉成 RGBA ByteBuffer（top-down）。
     *
     * XImage 結構（64 位 Linux）字段偏移：
     *   0  width, 4 height, 8 xoffset, 12 format, 16 data(指針), 24 byte_order,
     *   28 bitmap_unit, 32 bitmap_bit_order, 36 bitmap_pad, 40 depth,
     *   44 bytes_per_line, 48 bits_per_pixel, 56 red_mask, 64 green_mask, 72 blue_mask
     */
    private static Frame convertToRgba(Pointer img, int width, int height) {
        int byteOrder = img.getInt(24);
        int depth = img.getInt(40);
        int bytesPerLine = img.getInt(44);
        int bitsPerPixel = img.getInt(48);
        long redMask = img.getLong(56);
        long greenMask = img.getLong(64);
        long blueMask = img.getLong(72);
        Pointer data = img.getPointer(16);
        if (data == null) {
            return null;
        }

        // 只支持 32bpp（xwayland 默認 24 深/32bpp）與 24bpp
        if (bitsPerPixel != 32 && bitsPerPixel != 24) {
            LOGGER.warn("Unsupported X11 pixel depth: bpp={} depth={}", bitsPerPixel, depth);
            return null;
        }

        int bpp = bitsPerPixel / 8;
        int redShift = Long.numberOfTrailingZeros(redMask);
        int greenShift = Long.numberOfTrailingZeros(greenMask);
        int blueShift = Long.numberOfTrailingZeros(blueMask);
        // byte_order: 0 = LSBFirst, 1 = MSBFirst
        boolean msbFirst = (byteOrder == 1);

        ByteBuffer rgba = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
        byte[] row = new byte[Math.max(bytesPerLine, width * bpp)];

        for (int rowIdx = 0; rowIdx < height; rowIdx++) {
            // 服務器像素內存按 byte_order 存儲；x86 上 LSBFirst == Java native order。
            // 直接 memcpy 到 byte[]，再按掩碼提取。
            data.read((long) rowIdx * bytesPerLine, row, 0, bytesPerLine);

            int base = rowIdx * width * 4;
            for (int col = 0; col < width; col++) {
                int pixel;
                int off = col * bpp;
                if (msbFirst) {
                    if (bpp == 4) {
                        pixel = ((row[off] & 0xFF) << 24)
                                | ((row[off + 1] & 0xFF) << 16)
                                | ((row[off + 2] & 0xFF) << 8)
                                | (row[off + 3] & 0xFF);
                    } else {
                        pixel = ((row[off] & 0xFF) << 16)
                                | ((row[off + 1] & 0xFF) << 8)
                                | (row[off + 2] & 0xFF);
                    }
                } else if (bpp == 4) {
                    pixel = (row[off] & 0xFF)
                            | ((row[off + 1] & 0xFF) << 8)
                            | ((row[off + 2] & 0xFF) << 16)
                            | ((row[off + 3] & 0xFF) << 24);
                } else {
                    pixel = (row[off] & 0xFF)
                            | ((row[off + 1] & 0xFF) << 8)
                            | ((row[off + 2] & 0xFF) << 16);
                }

                rgba.put(base + col * 4, (byte) ((pixel & redMask) >>> redShift));
                rgba.put(base + col * 4 + 1, (byte) ((pixel & greenMask) >>> greenShift));
                rgba.put(base + col * 4 + 2, (byte) ((pixel & blueMask) >>> blueShift));
                rgba.put(base + col * 4 + 3, (byte) 0xFF);
            }
        }
        return new Frame(rgba, width, height, 0, 0);
    }

    // --------------------------------------------------------------------------
    // X 屬性輔助
    // --------------------------------------------------------------------------

    private static String fetchStringProperty(Pointer display, long window, String atomName) {
        if (X11_LIB == null) {
            return null;
        }
        long atom = X11_LIB.XInternAtom(display, atomName, 1);
        if (atom == 0) {
            return null;
        }

        // Request AnyPropertyType to accept STRING, UTF8_STRING, or other encodings.
        long[] type = new long[1];
        int[] format = new int[1];
        long[] nitems = new long[1];
        long[] bytesAfter = new long[1];
        Pointer[] prop = new Pointer[1];

        int status = X11_LIB.XGetWindowProperty(display, window, atom, 0, 1024, 0,
                                                 0, type, format, nitems, bytesAfter, prop);
        if (status != 0 || prop[0] == null || nitems[0] <= 0) {
            return null;
        }

        try {
            // Only handle 8-bit string formats directly.
            if (format[0] != 8) {
                return null;
            }
            int len = (int) nitems[0];
            byte[] raw = prop[0].getByteArray(0, len);
            int end = 0;
            while (end < len && raw[end] != 0) {
                end++;
            }
            if (end == 0) {
                return null;
            }
            return new String(raw, 0, end, StandardCharsets.UTF_8);
        } finally {
            X11_LIB.XFree(prop[0]);
        }
    }

    private static String fetchCardinalProperty(Pointer display, long window, String atomName) {
        if (X11_LIB == null) {
            return null;
        }
        long atom = X11_LIB.XInternAtom(display, atomName, 1);
        if (atom == 0) {
            return null;
        }

        long[] type = new long[1];
        int[] format = new int[1];
        long[] nitems = new long[1];
        long[] bytesAfter = new long[1];
        Pointer[] prop = new Pointer[1];

        int status = X11_LIB.XGetWindowProperty(display, window, atom, 0, 1, 0,
                                                 XA_CARDINAL, type, format, nitems, bytesAfter, prop);
        if (status != 0 || prop[0] == null || nitems[0] <= 0) {
            return null;
        }

        try {
            if (format[0] == 32) {
                return String.valueOf(prop[0].getInt(0));
            }
            if (format[0] == 16) {
                return String.valueOf(prop[0].getShort(0) & 0xFFFF);
            }
            if (format[0] == 8) {
                return String.valueOf(prop[0].getByte(0) & 0xFF);
            }
            return null;
        } finally {
            X11_LIB.XFree(prop[0]);
        }
    }
}