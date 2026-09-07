package dev.scapking.rendcraft.av.spatial;

import dev.scapking.rendcraft.protocol.WindowHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 空间音频服务.
 * 实现以窗口为中心向周围区块发出声音，
 * 不在范围内的玩家收不到声音.
 * 
 * 參考:
 * - SpatialAudio (ZCRAFT-NPE): 並行波追蹤
 * - BuroSound: 3D 區域音樂/環境音
 * - Sound Physics Remastered: 真實聲音衰減
 */
public class SpatialAudioService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpatialAudioService.class);

    // 窗口声音发射器映射
    private final Map<WindowHandle, AudioEmitter> emitters = new ConcurrentHashMap<>();

    // 默认声音传播范围 (区块数)
    private int defaultRangeBlocks = 16;

    /**
     * 创建或更新窗口的声音发射器.
     */
    public void setEmitter(WindowHandle windowHandle, AudioEmitter emitter) {
        emitters.put(windowHandle, emitter);
        LOGGER.info("Audio emitter set for window: {}", windowHandle);
    }

    /**
     * 移除窗口的声音发射器.
     */
    public void removeEmitter(WindowHandle windowHandle) {
        emitters.remove(windowHandle);
        LOGGER.info("Audio emitter removed for window: {}", windowHandle);
    }

    /**
     * 获取窗口的声音发射器.
     */
    public AudioEmitter getEmitter(WindowHandle windowHandle) {
        return emitters.get(windowHandle);
    }

    /**
     * 检查玩家是否在窗口声音范围内.
     * @param playerPosition 玩家位置 (x, y, z)
     * @param windowPosition 窗口位置 (x, y, z)
     * @return 是否在范围内
     */
    public boolean isPlayerInRange(int[] playerPosition, int[] windowPosition, int rangeBlocks) {
        int dx = playerPosition[0] - windowPosition[0];
        int dy = playerPosition[1] - windowPosition[1];
        int dz = playerPosition[2] - windowPosition[2];
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return distance <= rangeBlocks;
    }

    /**
     * 获取玩家与窗口之间的距离 (区块).
     */
    public double getDistance(int[] playerPosition, int[] windowPosition) {
        int dx = playerPosition[0] - windowPosition[0];
        int dy = playerPosition[1] - windowPosition[1];
        int dz = playerPosition[2] - windowPosition[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * 计算基于距离的声音衰减因子.
     * @param distance 距离 (区块)
     * @param maxRange 最大范围 (区块)
     * @return 衰减因子 (0.0 - 1.0)
     */
    public double calculateAttenuation(double distance, int maxRange) {
        if (distance >= maxRange) {
            return 0.0;
        }
        // 线性衰减
        return 1.0 - (distance / maxRange);
        // 或者使用平方反比定律:
        // return 1.0 / (1.0 + distance * distance / (maxRange * maxRange));
    }

    /**
     * 获取所有活动的声音发射器.
     */
    public Map<WindowHandle, AudioEmitter> getEmitters() {
        return Map.copyOf(emitters);
    }

    /**
     * 设置默认声音传播范围.
     */
    public void setDefaultRangeBlocks(int rangeBlocks) {
        this.defaultRangeBlocks = rangeBlocks;
    }

    /**
     * 获取默认声音传播范围.
     */
    public int getDefaultRangeBlocks() {
        return defaultRangeBlocks;
    }
}
