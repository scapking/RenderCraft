package dev.scapking.rendcraft.compositor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 与原生合成器（Rust/Smithay）通信的连接管理器。
 * 负责启动合成器进程、管理 IPC 通道（Unix domain socket 或 TCP）、
 * 发送命令和接收窗口图像数据。
 * 
 * 通信协议（简化）：
 * - 客户端（Java）连接到合成器。
 * - 命令：ListWindows, CaptureWindow <handle>, CloseWindow <handle>, SetVisible <handle> <bool>
 * - 响应：JSON 格式元数据、原始帧数据（/png/jpeg 或原始 rgba）。
 */
public class CompositorConnection implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompositorConnection.class);
    private Process compositorProcess;
    private Socket socket;
    private OutputStream out;
    private BufferedReader in;
    private InputStream binaryIn;
    private final String compositorPath;
    private final int port;
    private final String socketPath;
    private final boolean useTcp;
    private volatile boolean connected = false;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public CompositorConnection(String compositorPath) {
        this(compositorPath, 0);
    }

    public CompositorConnection(String compositorPath, int port) {
        this.compositorPath = compositorPath;
        this.port = port;
        this.socketPath = null;
        this.useTcp = port > 0;
    }

    public CompositorConnection(String compositorPath, String socketPath) {
        this.compositorPath = compositorPath;
        this.socketPath = socketPath;
        this.port = 0;
        this.useTcp = false;
    }

    public boolean start() {
        try {
            if (useTcp) {
                startTcpCompositor();
            } else {
                startUnixSocketCompositor();
            }
            connected = true;
            LOGGER.info("Compositor connection established");
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to start compositor", e);
            return false;
        }
    }

    private void startTcpCompositor() throws IOException {
        compositorProcess = new ProcessBuilder(compositorPath, "--tcp", String.valueOf(port))
                .redirectErrorStream(true)
                .start();
        // 等待合成器准备好
        try {
            Thread.sleep(1000); // TODO: better readiness check
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("Compositor startup interrupted", ie);
        }
        socket = new Socket("localhost", port);
        out = socket.getOutputStream();
        InputStream raw = socket.getInputStream();
        in = new BufferedReader(new InputStreamReader(raw, StandardCharsets.UTF_8));
        binaryIn = raw;
    }

    private void startUnixSocketCompositor() throws IOException {
        // Plain java.net.Socket does not support AF_UNIX. A real
        // implementation would open a SocketChannel against a
        // UnixDomainSocketAddress (JDK 16+). We fail loudly here so the
        // mod fails fast instead of silently doing nothing.
        throw new UnsupportedOperationException(
                "Unix-domain socket backend not implemented; use the TCP backend (-PwlTcpPort=...)");
    }

    public void sendCommand(String command) throws IOException {
        out.write((command + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    public String readResponse() throws IOException {
        return in.readLine();
    }

    public FrameCapture captureWindow(String handle) throws IOException {
        sendCommand("CaptureWindow " + handle);
        String response = readResponse();
        // 协议雏形：响应首行格式如下（当前仅用于演示/开发阶段）
        //   FRAME <width> <height> <format> <size>
        // 随后紧跟 size 字节的帧载荷。
        if (response == null) {
            throw new IOException("Invalid capture response: null");
        }
        String[] parts = response.split(" ", 4);
        if (parts.length < 4 || !parts[0].equals("FRAME")) {
            throw new IOException("Invalid capture response: " + response);
        }
        int width;
        int height;
        try {
            width = Integer.parseInt(parts[1]);
            height = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid capture dimensions: " + response, e);
        }
        // parts[3] 包含格式和大小，如 "raw 1024"
        String[] meta = parts[3].split(" ", 2);
        if (meta.length < 2) {
            throw new IOException("Invalid frame metadata: " + parts[3]);
        }
        String format = meta[0];
        int size;
        try {
            size = Integer.parseInt(meta[1]);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid frame size: " + parts[3], e);
        }
        if (size < 0) {
            throw new IOException("Invalid frame size: " + size);
        }
        byte[] imageData;
        if (size == 0) {
            imageData = new byte[0];
        } else {
            imageData = readFramePayload(size);
        }
        return new FrameCapture(width, height, format, imageData);
    }

    public List<WindowInfo> listWindows() throws IOException {
        sendCommand("ListWindows");
        String response = readResponse();
        // 假设响应是 JSON 数组
        // 解析 JSON 获取窗口列表
        // 简化：手动解析
        List<WindowInfo> windows = new ArrayList<>();
        // TODO: JSON 解析
        return windows;
    }

    public void close() {
        try {
            if (socket != null && !socket.isClosed()) {
                sendCommand("Quit");
                socket.close();
            }
        } catch (IOException e) {
            LOGGER.warn("Error closing connection", e);
        }
        if (compositorProcess != null) {
            compositorProcess.destroy();
            try { compositorProcess.waitFor(); } catch (InterruptedException e) {}
        }
        executor.shutdownNow();
        connected = false;
    }

    public boolean isConnected() { return connected; }

    // 内部类：帧捕获结果
    public static class FrameCapture {
        public final int width;
        public final int height;
        public final String format;
        public final byte[] data;

        public FrameCapture(int width, int height, String format, byte[] data) {
            this.width = width;
            this.height = height;
            this.format = format;
            this.data = data;
        }
    }

    private static final String LF = "\n";

    /**
     * 文本命令/响应协议里的帧读取器（当前基于换行分隔的文本响应）。
     * <p>
     * 注意：这是协议雏形。参考 Waylandcraft 的做法，真实捕捉不该永远依赖
     * “发字符串、读字符串、帧数据还在同一流里”这种模型。后面应该把帧传输
     * 从文本协议里拆出去，或者直接换成原生捕捉路径。
     */
    private byte[] readFramePayload(int size) throws IOException {
        byte[] buf = new byte[size];
        int remaining = size;
        while (remaining > 0) {
            int read = binaryIn.read(buf, size - remaining, remaining);
            if (read <= 0) {
                throw new IOException("Premature end of frame payload: expected " + size + " bytes");
            }
            remaining -= read;
        }
        return buf;
    }

    // 内部类：窗口信息
    public static class WindowInfo {
        public final String handle;
        public final String title;
        public final int width;
        public final int height;
        public final boolean visible;

        public WindowInfo(String handle, String title, int width, int height, boolean visible) {
            this.handle = handle;
            this.title = title;
            this.width = width;
            this.height = height;
            this.visible = visible;
        }
    }
}
