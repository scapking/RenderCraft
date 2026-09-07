package dev.scapking.rendcraft.av.spatial;

import dev.scapking.rendcraft.protocol.WindowHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 音频范围管理器.
 * 管理基于区块的声音传播范围.
 * 决定哪些玩家可以听到哪个窗口的声音.
 * 
 * 用於实现目標: 以窗口為中心向周圍區塊發出聲音，
 * 不在範圍內的玩家收不到聲音.
 */
public class AudioRangeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AudioRangeManager.class);

    // 窗口声音发射器映射
    private final Map<WindowHandle, AudioEmitter> emitters = new ConcurrentHashMap<>();
    // 每个发射器的范围控制
    private final Map<WindowHandle, RangeConfig> rangeConfigs = new ConcurrentHashMap<>();

    public static class RangeConfig {
        public int rangeBlocks = 16;
        public boolean enabled = true;
        public float volume = 1.0f;
        public boolean allowOverlap = false;

        public RangeConfig() {}

        public RangeConfig(int rangeBlocks, boolean enabled, float volume, boolean allowOverlap) {
            this.rangeBlocks = rangeBlocks;
            this.enabled = enabled;
            this.volume = volume;
            this.allowOverlap = allowOverlap;
        }
    }

    /**
     * 注册窗口的音频范围.
     */
    public void registerRange(WindowHandle windowHandle, RangeConfig config) {
        emitters.put(windowHandle, new AudioEmitter(windowHandle));
        rangeConfigs.put(windowHandle, config);
        LOGGER.info("Registered audio range for window: {}", windowHandle);
    }

    /**
     * 获取窗口的范围配置.
     */
    public RangeConfig getRangeConfig(WindowHandle windowHandle) {
        return rangeConfigs.get(windowHandle);
    }

    /**
     * 更新窗口的范围配置.
     */
    public void updateRangeConfig(WindowHandle windowHandle, RangeConfig config) {
        rangeConfigs.put(windowHandle, config);
        LOGGER.info("Updated range config for window: {}", windowHandle);
    }

    /**
     * 检查玩家是否可以听到指定窗口的声音.
     * @param playerUUID 玩家唯一标识
     * @param playerPosition 玩家位置 [x, y, z]
     * @param windowHandle 窗口句柄
     * @return 是否可以听到
     */
    public boolean canPlayerHear(String playerUUID, int[] playerPosition, WindowHandle windowHandle) {
        RangeConfig config = rangeConfigs.get(windowHandle);
        if (config == null || !config.enabled) {
            return false;
        }

        AudioEmitter emitter = emitters.get(windowHandle);
        if (emitter == null) {
            return false;
        }

        // 检查距离
        double distance = emitter.getPosition()[0] - playerPosition[0]; // 简化示例
        // 实际实现中计算 3D 距离
        return distance <= config.rangeBlocks;
    }

    /**
     * 获取可以听到指定窗口声音的玩家集合.
     * @param windowHandle 窗口句柄
     * @param allPlayers 所有玩家的位置映射
     * @return 可以听到的玩家 UUID 集合
     */
    public Set<String> getPlayersInRange(WindowHandle windowHandle, Map<String, int[]> allPlayers) {
        Set<String> playersInRange = ConcurrentHashMap.newKeySet();
        RangeConfig config = rangeConfigs.get(windowHandle);
        if (config == null || !config.enabled) {
            return playersInRange;
        }

        AudioEmitter emitter = emitters.get(windowHandle);
        if (emitter == null) {
            return playersInRange;
        }

        int[] emitterPos = emitter.getPosition();
        for (Map.Entry<String, int[]> entry : allPlayers.entrySet()) {
            String playerUUID = entry.getKey();
            int[] playerPos = entry.getValue();
            double distance = Math.sqrt(
                Math.pow(playerPos[0] - emitterPos[0], 2) +
                Math.pow(playerPos[1] - emitterPos[1], 2) +
                Math.pow(playerPos[2] - emitterPos[2], 2)
            );
            if (distance <= config.rangeBlocks) {
                playersInRange.add(playerUUID);
            }
        }
        return playersInRange;
    }

    /**
     * 移除窗口的范围注册.
     */
    public void unregisterRange(WindowHandle windowHandle) {
        emitters.remove(windowHandle);
        rangeConfigs.remove(windowHandle);
        LOGGER.info("Unregistered audio range for window: {}", windowHandle);
    }

    /**
     * 获取所有已注册的窗口句柄.
     */
    public Set<WindowHandle> getRegisteredWindows() {
        return rangeConfigs.keySet();
    }
}
