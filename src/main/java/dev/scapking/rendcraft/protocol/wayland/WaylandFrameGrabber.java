package dev.scapking.rendcraft.protocol.wayland;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Stand-in for the PipeWire consumer that
 * {@link WaylandPortalClient#captureFrame(String, int[])} would
 * normally need. Real Wayland capture goes through
 * <code>xdg-desktop-portal ScreenCast</code> +
 * <code>OpenPipeWireRemote</code> + a libpipewire client; that
 * requires a native helper which RenderCraft deliberately does
 * not ship. The next best thing on a typical Wayland desktop is
 * the <code>grim</code> screenshot utility, which speaks the
 * <code>wlr-screencopy</code> protocol directly and dumps the
 * captured frame as PPM. grim is bundled with Sway/Wlroots and
 * most wlroots-based compositors.
 *
 * <p>The trade-off is that grim can only capture the whole output
 * (or a region), not a specific window handle that the portal
 * session selected. RenderCraft's portal flow still does the
 * user-facing window-pick, but until a real PipeWire consumer is
 * wired in, this class serves the full output as a stand-in.
 *
 * <p>The PPM/P6 format is parsed in pure Java:
 * <pre>
 *   P6\n
 *   &lt;W&gt; &lt;H&gt;\n
 *   255\n
 *   &lt;RGB binary data...&gt;
 * </pre>
 * The output buffer is the standard top-down RGBA8 array Minecraft
 * textures want, with alpha forced to 0xFF.
 */
public class WaylandFrameGrabber {
    private static final Logger LOGGER = LoggerFactory.getLogger(WaylandFrameGrabber.class);

    public static class GrabFailed extends Exception {
        public GrabFailed(String message) { super(message); }
        public GrabFailed(String message, Throwable cause) { super(message, cause); }
    }

    /** Result of a successful grab. */
    public record Frame(int width, int height, byte[] rgba) {}

    /**
     * Try to grab the focused output with <code>grim</code>. Returns
     * null if grim is not on PATH (most common on GNOME where the
     * equivalent tool is <code>gnome-screenshot</code>).
     */
    public Frame tryGrabWithGrim() throws GrabFailed {
        if (!toolAvailable("grim")) {
            return null;
        }
        ProcessBuilder pb = new ProcessBuilder("grim", "-t", "ppm", "-");
        pb.redirectErrorStream(true);
        Process process = null;
        try {
            process = pb.start();
            byte[] stdout = drain(process.getInputStream());
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new GrabFailed("grim timed out after 5s");
            }
            int code = process.exitValue();
            if (code != 0) {
                throw new GrabFailed("grim exited with code " + code + ": "
                        + new String(stdout, StandardCharsets.UTF_8));
            }
            return parsePpm(stdout);
        } catch (IOException e) {
            throw new GrabFailed("Failed to run grim: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GrabFailed("Interrupted while running grim", e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static boolean toolAvailable(String name) {
        try {
            Process p = new ProcessBuilder("which", name).start();
            p.getOutputStream().close();
            p.getErrorStream().close();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Parse a binary PPM (P6) image into a top-down RGBA8 array.
     * The header is the three lines
     * <pre>
     *   P6\n
     *   &lt;W&gt; &lt;H&gt;\n
     *   255\n
     * </pre>
     * followed immediately by W*H*3 bytes of interleaved RGB.
     * Comments ("#" to end of line) are allowed before the raster
     * but we do not honour them on the data lines; that is enough
     * for grim's output.
     */
    public static Frame parsePpm(byte[] data) throws GrabFailed {
        int idx = 0;
        // Read magic "P6"
        if (idx + 2 > data.length || data[idx] != 'P' || data[idx + 1] != '6') {
            throw new GrabFailed("Not a P6 PPM (no magic)");
        }
        idx = skipWhitespaceAndComments(data, idx + 2);
        int width = readInt(data, idx);
        idx = skipWhitespace(data, widthEnd(data, idx));
        int height = readInt(data, idx);
        idx = skipWhitespace(data, heightEnd(data, idx));
        int maxval = readInt(data, idx);
        idx = skipWhitespace(data, maxvalEnd(data, idx));
        if (maxval != 255) {
            throw new GrabFailed("PPM maxval=" + maxval + " not supported (expected 255)");
        }
        // Single whitespace separator before raster
        if (idx < data.length && (data[idx] == ' ' || data[idx] == '\n' || data[idx] == '\r' || data[idx] == '\t')) {
            idx++;
        }
        int rasterStart = idx;
        int rasterLen = data.length - rasterStart;
        int expected = width * height * 3;
        if (rasterLen < expected) {
            throw new GrabFailed("PPM truncated: have " + rasterLen + " bytes, expected " + expected);
        }
        byte[] rgba = new byte[width * height * 4];
        int src = rasterStart;
        int dst = 0;
        for (int i = 0; i < width * height; i++) {
            rgba[dst++] = data[src];
            rgba[dst++] = data[src + 1];
            rgba[dst++] = data[src + 2];
            rgba[dst++] = (byte) 0xFF;
            src += 3;
        }
        return new Frame(width, height, rgba);
    }

    private static int skipWhitespaceAndComments(byte[] data, int idx) {
        while (idx < data.length) {
            byte c = data[idx];
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                idx++;
            } else if (c == '#') {
                // skip to end of line
                while (idx < data.length && data[idx] != '\n') {
                    idx++;
                }
            } else {
                return idx;
            }
        }
        return idx;
    }

    private static int skipWhitespace(byte[] data, int idx) {
        while (idx < data.length) {
            byte c = data[idx];
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                idx++;
            } else {
                return idx;
            }
        }
        return idx;
    }

    private static int readInt(byte[] data, int idx) {
        int start = idx;
        while (idx < data.length && data[idx] >= '0' && data[idx] <= '9') {
            idx++;
        }
        int n = 0;
        for (int i = start; i < idx; i++) {
            n = n * 10 + (data[i] - '0');
        }
        return n;
    }

    private static int widthEnd(byte[] data, int idx) {
        while (idx < data.length && data[idx] >= '0' && data[idx] <= '9') {
            idx++;
        }
        return idx;
    }

    private static int heightEnd(byte[] data, int idx) {
        while (idx < data.length && data[idx] >= '0' && data[idx] <= '9') {
            idx++;
        }
        return idx;
    }

    private static int maxvalEnd(byte[] data, int idx) {
        while (idx < data.length && data[idx] >= '0' && data[idx] <= '9') {
            idx++;
        }
        return idx;
    }

    private static byte[] drain(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 20);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
