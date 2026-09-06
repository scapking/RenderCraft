package dev.scapking.rendcraft.av;

import dev.scapking.rendcraft.av.buffer.BufferStrategy;
import dev.scapking.rendcraft.av.buffer.BufferStrategy.Frame;
import dev.scapking.rendcraft.av.buffer.BufferStrategy.FrameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 服务器发射缓冲管理器.
 * 管理服务器端的音视频缓冲，支持 4-8 秒缓存.
 * 参考 Discord Go Live 架构: 捕获有 fallback 系统，
 * 缓冲不可无限增长，否则延迟飞升.
 */
public class ServerBufferManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerBufferManager.class);

    private final BufferStrategy bufferStrategy;
    private final Object captureLock = new Object();
    private volatile boolean capturing = false;

    // 丢帧计数器
    private final AtomicLong droppedFrames = new AtomicLong(0);
    private final AtomicLong droppedVideoFrames = new AtomicLong(0);
    private final AtomicLong droppedAudioFrames = new AtomicLong(0);

    // 目标缓冲区大小: 4-8 秒
    private int targetVideoBufferSize = 180;  // 6 秒 @ 30fps
    private int targetAudioBufferSize = 288000; // 6 秒 @ 48kHz

    // 缓冲区压力指标
    private final AtomicLong bufferOverflowCount = new AtomicLong(0);

    public ServerBufferManager(BufferStrategy bufferStrategy) {
        this.bufferStrategy = bufferStrategy;
    }

    /**
     * 接收新捕获的帧.
     * 如果缓冲区已满，根据策略丢弃旧帧或新帧.
     */
    public boolean receiveFrame(Frame frame) {
        if (frame == null) return false;

        synchronized (captureLock) {
            int currentSize = bufferStrategy.size();
            int capacity = getCapacityForFrameType(frame.getType());

            if (currentSize >= capacity) {
                // 缓冲区已满，丢弃最旧的帧 (DROP_OLDEST 策略)
                bufferStrategy.pop(); // 移除最旧的帧
                droppedFrames.incrementAndGet();
                if (frame.getType() == FrameType.VIDEO) {
                    droppedVideoFrames.incrementAndGet();
                } else {
                    droppedAudioFrames.incrementAndGet();
                }
                bufferOverflowCount.incrementAndGet();
                LOGGER.debug("Buffer overflow, dropped frame (size={}, capacity={})",
                        currentSize, capacity);
            }

            // 加入新帧
            boolean added = bufferStrategy.push(frame);
            if (!added) {
                droppedFrames.incrementAndGet();
                LOGGER.warn("Failed to add frame to buffer");
            }
            return added;
        }
    }

    /**
     * 获取要发送给客户端的帧.
     * 线程安全.
     */
    public Frame getFrameForClient() {
        synchronized (captureLock) {
            return bufferStrategy.peek();
        }
    }

    /**
     * 移除已发送的帧.
     */
    public void removeFrame() {
        synchronized (captureLock) {
            bufferStrategy.pop();
        }
    }

    /**
     * 获取帧容量 (基于帧类型).
     */
    private int getCapacityForFrameType(FrameType type) {
        if (type == FrameType.VIDEO) {
            return targetVideoBufferSize;
        } else {
            return targetAudioBufferSize;
        }
    }

    /**
     * 开始捕获.
     */
    public void startCapture() {
        if (capturing) return;
        capturing = true;
        LOGGER.info("Server capture started");
    }

    /**
     * 停止捕获.
     */
    public void stopCapture() {
        capturing = false;
        LOGGER.info("Server capture stopped");
    }

    /**
     * 获取丢帧计数.
     */
    public long getDroppedFrames() {
        return droppedFrames.get();
    }

    /**
     * 获取视频丢帧计数.
     */
    public long getDroppedVideoFrames() {
        return droppedVideoFrames.get();
    }

    /**
     * 获取音频丢帧计数.
     */
    public long getDroppedAudioFrames() {
        return droppedAudioFrames.get();
    }

    /**
     * 获取缓冲区溢出次数.
     */
    public long getBufferOverflowCount() {
        return bufferOverflowCount.get();
    }

    /**
     * 获取当前缓冲区大小.
     */
    public int getBufferSize() {
        return bufferStrategy.size();
    }

    /**
     * 清空缓冲区.
     */
    public void clear() {
        bufferStrategy.clear();
    }

    /**
     * 设置目标视频缓冲大小 (帧数).
     */
    public void setTargetVideoBufferSize(int frames) {
        this.targetVideoBufferSize = frames;
    }

    /**
     * 设置目标音频缓冲大小 (采样数).
     */
    public void setTargetAudioBufferSize(int samples) {
        this.targetAudioBufferSize = samples;
    }

    /**
     * 重置统计.
     */
    public void resetStats() {
        droppedFrames.set(0);
        droppedVideoFrames.set(0);
        droppedAudioFrames.set(0);
        bufferOverflowCount.set(0);
    }
}
