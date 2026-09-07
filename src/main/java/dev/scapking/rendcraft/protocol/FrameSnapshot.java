package dev.scapking.rendcraft.protocol;

import java.util.Objects;

/**
 * 窗口帧快照。
 * 保存某一时刻窗口捕获的图像数据及元数据。
 */
public final class FrameSnapshot {
    private final long captureTimeMs;
    private final int width;
    private final int height;
    private final byte[] imageData;
    private final String format;

    public FrameSnapshot(long captureTimeMs, int width, int height, byte[] imageData, String format) {
        this.captureTimeMs = captureTimeMs;
        this.width = width;
        this.height = height;
        this.imageData = Objects.requireNonNull(imageData);
        this.format = Objects.requireNonNull(format);
    }

    public long getCaptureTimeMs() { return captureTimeMs; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public byte[] getImageData() { return imageData; }
    public String getFormat() { return format; }

    @Override
    public String toString() {
        return "FrameSnapshot{width=%d, height=%d, format=%s, size=%d}".formatted(
                width, height, format, imageData.length);
    }
}
