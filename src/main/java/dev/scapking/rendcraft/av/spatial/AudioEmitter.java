package dev.scapking.rendcraft.av.spatial;

import dev.scapking.rendcraft.protocol.WindowHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 音频发射器.
 * 表示一个窗口的声音发射点.
 * 可以设置声音的位置、范围、音量等.
 */
public class AudioEmitter {
    private static final Logger LOGGER = LoggerFactory.getLogger(AudioEmitter.class);

    private final WindowHandle windowHandle;
    private int[] position; // [x, y, z] 区块坐标
    private int rangeBlocks;
    private float volume = 1.0f;
    private boolean enabled = true;

    public AudioEmitter(WindowHandle windowHandle) {
        this.windowHandle = windowHandle;
        this.position = new int[]{0, 0, 0};
        this.rangeBlocks = 16; // 默认 16 区块范围
    }

    public WindowHandle getWindowHandle() {
        return windowHandle;
    }

    public int[] getPosition() {
        return position;
    }

    public void setPosition(int x, int y, int z) {
        this.position = new int[]{x, y, z};
        LOGGER.debug("Emitter position set: ({}, {}, {})", x, y, z);
    }

    public int getRangeBlocks() {
        return rangeBlocks;
    }

    public void setRangeBlocks(int rangeBlocks) {
        this.rangeBlocks = rangeBlocks;
        LOGGER.debug("Emitter range set: {} blocks", rangeBlocks);
    }

    public float getVolume() {
        return volume;
    }

    public void setVolume(float volume) {
        this.volume = Math.max(0.0f, Math.min(1.0f, volume));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        LOGGER.info("Emitter {} enabled: {}", windowHandle, enabled);
    }

    /**
     * 获取此发射器的有效范围 (区块).
     */
    public int getEffectiveRange() {
        return enabled ? rangeBlocks : 0;
    }

    /**
     * 检查给定位置是否在此发射器的范围内.
     */
    public boolean isInRange(int[] playerPosition) {
        if (!enabled) return false;
        int dx = playerPosition[0] - position[0];
        int dy = playerPosition[1] - position[1];
        int dz = playerPosition[2] - position[2];
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return distance <= rangeBlocks;
    }
}
