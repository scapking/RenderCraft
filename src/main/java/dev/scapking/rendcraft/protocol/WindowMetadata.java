package dev.scapking.rendcraft.protocol;

import java.util.Objects;

/**
 * 窗口元数据——从协议后端获得的窗口信息。
 * 不包含任何渲染/交互细节，仅作为元数据传递。
 */
public final class WindowMetadata {
    private final WindowHandle handle;
    private final String title;
    private final int width;
    private final int height;
    private final boolean visible;

    public WindowMetadata(WindowHandle handle, String title, int width, int height, boolean visible) {
        this.handle = Objects.requireNonNull(handle, "handle");
        this.title = Objects.requireNonNull(title, "title");
        this.width = width;
        this.height = height;
        this.visible = visible;
    }

    public WindowHandle getHandle() {
        return handle;
    }

    public String getTitle() {
        return title;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isVisible() {
        return visible;
    }

    @Override
    public String toString() {
        return "WindowMetadata{" +
                "handle=" + handle +
                ", title='" + title + '\'' +
                ", size=" + width + "x" + height +
                ", visible=" + visible +
                '}';
    }
}
