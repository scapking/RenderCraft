package dev.scapking.rendcraft.av.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 音画同步模型.
 * 管理音频和视频的呈现时间戳，保持同步.
 * 参考 Discord Go Live 架构: 音频和视频分开线程处理，
 * 播放端有统一时钟/缓冲管理.
 */
public class SyncModel {
    private static final Logger LOGGER = LoggerFactory.getLogger(SyncModel.class);

    private final AtomicLong audioClockNanos = new AtomicLong(0);
    private final AtomicLong videoClockNanos = new AtomicLong(0);
    private final AtomicLong lastSyncAdjustNanos = new AtomicLong(0);

    private volatile long baseTimeNanos = 0;
    private volatile boolean synced = false;

    /**
     * 初始化同步模型.
     * 设定基础时间作为参考.
     */
    public void initialize() {
        this.baseTimeNanos = System.nanoTime();
        this.audioClockNanos.set(0);
        this.videoClockNanos.set(0);
        this.synced = false;
        LOGGER.info("SyncModel initialized");
    }

    /**
     * 更新音频时钟.
     * @param presentationTimeNanos 音频帧的呈现时间戳 (纳秒)
     */
    public void updateAudioClock(long presentationTimeNanos) {
        if (baseTimeNanos == 0) {
            baseTimeNanos = System.nanoTime();
        }
        long now = System.nanoTime() - baseTimeNanos;
        // 音频时钟基于实际呈现时间
        audioClockNanos.set(presentationTimeNanos);
        checkSync();
    }

    /**
     * 更新视频时钟.
     * @param presentationTimeNanos 视频帧的呈现时间戳 (纳秒)
     */
    public void updateVideoClock(long presentationTimeNanos) {
        if (baseTimeNanos == 0) {
            baseTimeNanos = System.nanoTime();
        }
        long now = System.nanoTime() - baseTimeNanos;
        // 视频时钟基于实际呈现时间
        videoClockNanos.set(presentationTimeNanos);
        checkSync();
    }

    /**
     * 检查并调整音画同步.
     * 如果视频落后于音频，可能需要丢帧或调整.
     */
    private void checkSync() {
        long audio = audioClockNanos.get();
        long video = videoClockNanos.get();
        long diff = video - audio;

        // 如果差异超过 50ms (50,000,000 纳秒)，记录警告
        if (Math.abs(diff) > 50_000_000) {
            LOGGER.warn("Audio/video drift detected: {} ms", diff / 1_000_000.0);
            // TODO: 实施同步策略 (丢帧、等待、调整时钟)
        } else {
            synced = true;
        }
    }

    /**
     * 获取音频时钟 (纳秒).
     */
    public long getAudioClock() {
        return audioClockNanos.get();
    }

    /**
     * 获取视频时钟 (纳秒).
     */
    public long getVideoClock() {
        return videoClockNanos.get();
    }

    /**
     * 获取时钟差异 (视频 - 音频), 纳秒.
     */
    public long getClockDifference() {
        return videoClockNanos.get() - audioClockNanos.get();
    }

    /**
     * 是否同步.
     */
    public boolean isSynced() {
        return synced;
    }

    /**
     * 重置同步状态.
     */
    public void reset() {
        audioClockNanos.set(0);
        videoClockNanos.set(0);
        lastSyncAdjustNanos.set(0);
        synced = false;
        LOGGER.info("SyncModel reset");
    }
}
