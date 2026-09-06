package dev.scapking.rendcraft.av.spatial;

import dev.scapking.rendcraft.protocol.WindowHandle;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 空间音频传输服务。
 * 
 * 实现目标：以窗口为中心向周围区块发出声音，不在范围内的玩家收不到。
 * 
 * 工作方式：
 * 1. 每个活动窗口注册为一个音频源，包含位置和范围
 * 2. 服务器每刻检查所有玩家，确定哪些玩家在每个音频源的范围内
 * 3. 使用Minecraft的位置声音系统向范围内玩家播放声音
 * 4. 根据距离应用衰减（Minecraft内置 + 自定义衰减模型）
 * 
 * 多人共享：
 * - 服务器端权威：范围检查在服务器进行
 * - 所有玩家根据位置感受声音
 * - 可与权限系统结合控制谁可以听
 * 
 * 音频来源：
 * - 当前使用占位符声音事件（应替换为实际捕获音频）
 * - 未来可集成AudioCaptureService捕获的实时音频
 * - 或使用预录制音频文件
 */
public class SpatialAudioTransmissionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpatialAudioTransmissionService.class);
    
    // 活动音频源：窗口句柄 → 音频源信息
    private final Map<WindowHandle, AudioSource> audioSources = new ConcurrentHashMap<>();
    
    // 范围管理器
    private final AudioRangeManager rangeManager = new AudioRangeManager();
    
    // Minecraft服务器级别
    @Nullable
    private ServerLevel serverLevel;
    
    // 声音事件（占位符）
    @Nullable
    private SoundEvent soundEvent;
    
    // 默认配置
    private float defaultVolume = 1.0f;
    private float defaultPitch = 1.0f;
    private int defaultRangeBlocks = 16;
    private SoundCategory defaultCategory = SoundCategory.NEUTRAL;
    private AttenuationModel attenuationModel = AttenuationModel.LINEAR;
    
    // 服务状态
    private boolean enabled = true;
    private int totalPlaybackCount = 0;
    private int totalSources = 0;
    private int tickCount = 0;
    
    // 性能优化：限制 tick 频率
    private long lastTickTime = 0;
    private static final long TICK_INTERVAL_MS = 50; // 20 ticks/sec
    
    /**
     * 衰减模型。
     * 控制声音如何随距离衰减。
     */
    public enum AttenuationModel {
        /** 线性衰减：音量 = baseVolume * (1 - distance/range) */
        LINEAR,
        
        /** 逆平方衰减：更真实的物理模型 */
        INVERSE_SQUARE,
        
        /** 指数衰减：自然的声音消失效果 */
        EXPONENTIAL,
        
        /** 恒定音量：范围内恒定，范围外瞬间静音 */
        CONSTANT
    }
    
    /**
     * 音频源信息。
     * 与一个窗口关联，定义声音的位置、范围和属性。
     */
    public static class AudioSource {
        public final WindowHandle windowHandle;
        public int x, y, z;           // 位置
        public int rangeBlocks;        // 传播范围（区块）
        public float volume = 1.0f;   // 音量 (0.0-1.0)
        public float pitch = 1.0f;    // 音调 (0.5-2.0)
        public SoundCategory category = SoundCategory.NEUTRAL;
        public boolean enabled = true;
        public long lastPlaybackTime = 0;
        public int playbackIntervalTicks = 0;  // 0 = 每 tick 播放
        
        public AudioSource(WindowHandle windowHandle, int x, int y, int z, int rangeBlocks) {
            this.windowHandle = windowHandle;
            this.x = x; this.y = y; this.z = z;
            this.rangeBlocks = rangeBlocks;
        }
        
        public AudioSource(WindowHandle windowHandle, int x, int y, int z) {
            this(windowHandle, x, y, z, 16);
        }
        
        public void setPosition(int x, int y, int z) {
            this.x = x; this.y = y; this.z = z;
        }
        
        public void setRange(int range) {
            this.rangeBlocks = range;
        }
        
        public boolean isPlayerInRange(int[] playerPos) {
            return SpatialAudioService.getInstance().isPlayerInRange(
                playerPos, new int[]{x, y, z}, rangeBlocks);
        }
    }
    
    /**
     * 初始化服务。
     * 应在服务器启动时调用一次。
     */
    public void initialize(@Nullable ServerLevel level) {
        this.serverLevel = level;
        LOGGER.info("SpatialAudioTransmissionService initialized, dimension: {}", 
            level == null ? "null" : level.dimension().type().getName());
    }
    
    /**
     * 设置声音事件。
     * 默认使用矿灯环境声作为占位符。
     */
    public void setSoundEvent(@Nullable SoundEvent event) {
        this.soundEvent = event;
        LOGGER.info("Sound event set: {}", 
            event == null ? "null" : event.getId());
    }
    
    /**
     * 设置默认声音事件（占位符）。
     */
    public void setDefaultSoundEvent() {
        this.soundEvent = SoundEvents.ENTITY_TURTLE_AMBIENT;
        LOGGER.info("Set default sound event: {}", soundEvent.getId());
    }
    
    /**
     * 注册音频源（使用默认范围）。
     */
    public void registerAudioSource(WindowHandle windowHandle, int x, int y, int z) {
        registerAudioSource(windowHandle, x, y, z, defaultRangeBlocks);
    }
    
    /**
     * 注册音频源（带自定义范围）。
     * 
     * @param windowHandle 窗口句柄
     * @param x 位置X（区块坐标）
     * @param y 位置Y（区块坐标）
     * @param z 位置Z（区块坐标）
     * @param rangeBlocks 传播范围（区块数）
     */
    public void registerAudioSource(WindowHandle windowHandle, int x, int y, int z, int rangeBlocks) {
        if (windowHandle == null) {
            LOGGER.warn("Cannot register audio source with null window handle");
            return;
        }
        
        AudioSource source = new AudioSource(windowHandle, x, y, z, rangeBlocks);
        audioSources.put(windowHandle, source);
        totalSources++;
        
        LOGGER.debug("Registered audio source: {} at ({},{},{}), range={} blocks",
            windowHandle.getId(), x, y, z, rangeBlocks);
    }
    
    /**
     * 注册音频源（带完整配置）。
     */
    public void registerAudioSource(WindowHandle windowHandle, int x, int y, int z,
                                    int rangeBlocks, float volume, float pitch,
                                    SoundCategory category, int playbackInterval) {
        if (windowHandle == null) return;
        
        AudioSource source = new AudioSource(windowHandle, x, y, z, rangeBlocks);
        source.volume = Mth.clamp(volume, 0.0f, 1.0f);
        source.pitch = Mth.clamp(pitch, 0.5f, 2.0f);
        source.category = category;
        source.playbackIntervalTicks = playbackInterval;
        
        audioSources.put(windowHandle, source);
        totalSources++;
        
        LOGGER.debug("Registered audio source with full config: {}", windowHandle.getId());
    }
    
    /**
     * 注销音频源。
     */
    public void unregisterAudioSource(WindowHandle windowHandle) {
        if (windowHandle == null || !audioSources.containsKey(windowHandle)) return;
        
        audioSources.remove(windowHandle);
        totalSources--;
        LOGGER.debug("Unregistered audio source: {}", windowHandle.getId());
    }
    
    /**
     * 更新音频源位置。
     * 用于窗口移动时更新声音位置。
     */
    public void updateAudioSourcePosition(WindowHandle windowHandle, int x, int y, int z) {
        AudioSource source = audioSources.get(windowHandle);
        if (source != null) {
            source.setPosition(x, y, z);
            LOGGER.debug("Updated audio source position: {}", windowHandle.getId());
        }
    }
    
    /**
     * 更新音频源范围。
     */
    public void updateAudioSourceRange(WindowHandle windowHandle, int rangeBlocks) {
        AudioSource source = audioSources.get(windowHandle);
        if (source != null) {
            source.setRange(rangeBlocks);
            LOGGER.debug("Updated audio source range: {} -> {} blocks",
                windowHandle.getId(), rangeBlocks);
        }
    }
    
    /**
     * 服务器刻更新。
     * 应在服务器的每个刻调用，用于检查范围并播放声音。
     * 
     * 工作流程：
     * 1. 获取所有在线玩家及其位置
     * 2. 对每个音频源，检查哪些玩家在范围内
     * 3. 向范围内的玩家播放声音
     * 4. 应用距离衰减
     */
    public void serverTick() {
        if (!enabled || serverLevel == null || soundEvent == null) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        
        // 限流：避免每刻都播放
        if (currentTime - lastTickTime < TICK_INTERVAL_MS) {
            return;
        }
        lastTickTime = currentTime;
        tickCount++;
        
        // 获取所有在线玩家
        List<ServerPlayer> players = serverLevel.getServer().getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return;
        }
        
        // 构建玩家位置映射
        Map<UUID, int[]> playerPositions = new HashMap<>(players.size());
        for (ServerPlayer player : players) {
            playerPositions.put(player.getUUID(), 
                new int[]{
                    Mth.floor(player.getX()),
                    Mth.floor(player.getY()),
                    Mth.floor(player.getZ())
                });
        }
        
        // 对每个音频源，检查范围并播放
        for (AudioSource source : audioSources.values()) {
            if (!source.enabled) {
                continue;
            }
            
            // 检查播放间隔
            if (source.playbackIntervalTicks > 0) {
                if (currentTime - source.lastPlaybackTime < source.playbackIntervalTicks * TICK_INTERVAL_MS) {
                    continue;
                }
            }
            
            // 获取范围内玩家
            Set<UUID> playersInRange = rangeManager.getPlayersInRange(
                source.windowHandle, playerPositions);
            
            if (playersInRange.isEmpty()) {
                continue;
            }
            
            // 向每个范围内的玩家播放声音
            for (UUID playerUuid : playersInRange) {
                ServerPlayer player = serverLevel.getServer().getPlayerList()
                    .getPlayer(playerUuid);
                if (player == null) continue;
                
                // 计算距离
                int[] playerPos = playerPositions.get(playerUuid);
                double distance = SpatialAudioService.getInstance()
                    .getDistance(playerPos, new int[]{source.x, source.y, source.z});
                
                // 计算衰减后的音量
                float attenuatedVolume = calculateAttenuatedVolume(
                    distance, source.rangeBlocks, source.volume);
                
                if (attenuatedVolume <= 0.001f) {
                    continue;
                }
                
                // 播放声音
                try {
                    serverLevel.playSound(
                        soundEvent,
                        SoundSource.NEUTRAL,
                        player,
                        source.x + 0.5,  // 区块中心偏移
                        source.y + 0.5,
                        source.z + 0.5,
                        attenuatedVolume,
                        source.pitch
                    );
                    totalPlaybackCount++;
                } catch (Exception e) {
                    LOGGER.warn("Failed to play sound for player {} at ({},{},{})",
                        player.getName().getString(), source.x, source.y, source.z, e);
                }
            }
            
            // 更新上次播放时间
            source.lastPlaybackTime = currentTime;
        }
    }
    
    /**
     * 计算衰减后的音量。
     * 
     * @param distance 玩家与声源的距离（区块）
     * @param range 最大范围（区块）
     * @param baseVolume 基础音量
     * @return 衰减后的音量 (0.0 - 1.0)
     */
    private float calculateAttenuatedVolume(double distance, int range, float baseVolume) {
        if (distance >= range) {
            return 0.0f;
        }
        
        double normalizedDistance = distance / range;  // [0, 1]
        
        return (float) switch (attenuationModel) {
            case LINEAR -> {
                // 线性衰减：从1.0线性减少到0.0
                yield baseVolume * (1.0 - normalizedDistance);
            }
            case INVERSE_SQUARE -> {
                // 逆平方衰减：更真实的物理模型
                // 在范围内保持较高音量，接近范围边缘快速下降
                double factor = 1.0 / (1.0 + normalizedDistance * normalizedDistance * 3.0);
                yield baseVolume * factor;
            }
            case EXPONENTIAL -> {
                // 指数衰减：自然的声音消失
                double factor = Math.exp(-normalizedDistance * 3.0);
                yield baseVolume * factor;
            }
            case CONSTANT -> {
                // 恒定音量：范围内恒定，范围外瞬间静音
                yield baseVolume;
            }
        };
    }
    
    /**
     * 获取指定音频源范围内的玩家列表。
     * 
     * @param windowHandle 窗口句柄
     * @param allPlayers 所有玩家的位置映射
     * @return 范围内的玩家UUID集合
     */
    public Set<UUID> getPlayersInRange(WindowHandle windowHandle, 
                                        Map<UUID, int[]> allPlayers) {
        AudioSource source = audioSources.get(windowHandle);
        if (source == null || !source.enabled) {
            return Collections.emptySet();
        }
        
        return rangeManager.getPlayersInRange(windowHandle, allPlayers);
    }
    
    /**
     * 获取玩家可以听到的音频源列表。
     * 
     * @param playerPosition 玩家位置 [x, y, z]
     * @return 玩家可以听到的音频源列表
     */
    public List<AudioSource> getAudioSourcesForPlayer(int[] playerPosition) {
        List<AudioSource> audibleSources = new ArrayList<>();
        
        for (AudioSource source : audioSources.values()) {
            if (!source.enabled) continue;
            
            if (source.isPlayerInRange(playerPosition)) {
                audibleSources.add(source);
            }
        }
        
        return audibleSources;
    }
    
    /**
     * 获取活动音频源的数量。
     */
    public int getSourceCount() {
        return totalSources;
    }
    
    /**
     * 获取总播放次数（调试用）。
     */
    public int getTotalPlaybackCount() {
        return totalPlaybackCount;
    }
    
    /**
     * 重置统计信息。
     */
    public void resetStats() {
        totalPlaybackCount = 0;
        tickCount = 0;
    }
    
    /**
     * 获取音频源数量（调试用）。
     */
    public int getTickCount() {
        return tickCount;
    }
    
    /**
     * 设置默认音量。
     */
    public void setDefaultVolume(float volume) {
        this.defaultVolume = Mth.clamp(volume, 0.0f, 1.0f);
    }
    
    /**
     * 设置默认音调。
     */
    public void setDefaultPitch(float pitch) {
        this.defaultPitch = Mth.clamp(pitch, 0.5f, 2.0f);
    }
    
    /**
     * 设置默认范围。
     */
    public void setDefaultRange(int rangeBlocks) {
        this.defaultRangeBlocks = Mth.clamp(rangeBlocks, 1, 256);
    }
    
    /**
     * 设置默认声音类别。
     */
    public void setDefaultCategory(SoundCategory category) {
        this.defaultCategory = category;
    }
    
    /**
     * 设置衰减模型。
     */
    public void setAttenuationModel(AttenuationModel model) {
        this.attenuationModel = model;
    }
    
    /**
     * 启用/禁用服务。
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        LOGGER.info("Spatial audio transmission service {}", 
            enabled ? "enabled" : "disabled");
    }
    
    /**
     * 获取范围管理器。
     */
    public AudioRangeManager getRangeManager() {
        return rangeManager;
    }
    
    /**
     * 获取服务器级别。
     */
    @Nullable
    public ServerLevel getServerLevel() {
        return serverLevel;
    }
    
    /**
     * 清除所有音频源。
     */
    public void clearAll() {
        audioSources.clear();
        totalSources = 0;
        totalPlaybackCount = 0;
        tickCount = 0;
        LOGGER.info("Cleared all audio sources");
    }
}
