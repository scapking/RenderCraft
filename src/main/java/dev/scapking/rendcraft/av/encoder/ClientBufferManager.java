package dev.scapking.rendcraft.av;

import dev.scapking.rendcraft.av.buffer.BufferStrategy;
import dev.scapking.rendcraft.av.buffer.BufferStrategy.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 客户端播放缓冲管理器.
 * 管理客户端的音视频缓冲，支持 4-8 秒预存.
 * 参考 Discord Go Live 架构.
 */
public class ClientBufferManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientBufferManager.class);

    private final BufferStrategy bufferStrategy;
    private final Object playbackLock = new Object();
    private volatile boolean playing = false;
    private volatile long lastPlaybackTimeNanos = 0;
    private final Queue<Frame> pendingFrames = new ConcurrentLinkedQueue<>();

    // 目标缓冲区大小: 4-8 秒
    // 对于 30fps 视频: 120-240 帧
    // 对于 48kHz 音频: 192,000-384,000 采样
    private int targetVideoBufferSize = 180;  // 6 秒 @ 30fps
    private int targetAudioBufferSize = 288000; // 6 秒 @ 48kHz

    public ClientBufferManager(BufferStrategy bufferStrategy) {
        this.bufferStrategy = bufferStrategy;
    }

    /**
     * 接收新帧并加入缓冲.
     */
    public void receiveFrame(Frame frame) {
        if (frame == null) return;

        // 先加入待处理队列
        pendingFrames.offer(frame);

        // 如果正在播放，从队列取帧加入缓冲
        if (playing) {
            Frame frameToBuffer = pendingFrames.poll();
            if (frameToBuffer != null) {
                bufferStrategy.push(frameToBuffer);
            }
        }
    }

    /**
     * 开始播放.
     */
    public void startPlayback() {
        if (playing) return;
        playing = true;
        lastPlaybackTimeNanos = System.nanoTime();
        LOGGER.info("Client playback started");
    }

    /**
     * 停止播放.
     */
    public void stopPlayback() {
        playing = false;
        LOGGER.info("Client playback stopped");
    }

    /**
     * 获取下一个要播放的帧.
     * 线程安全.
     */
    public Frame getNextFrame() {
        synchronized (playbackLock) {
            // 优先从缓冲区获取
            Frame frame = bufferStrategy.pop();
            if (frame == null) {
                // 缓冲区为空，从待处理队列获取
                frame = pendingFrames.poll();
            }
            if (frame != null) {
                lastPlaybackTimeNanos = System.nanoTime();
            }
            return frame;
        }
    }

    /**
     * 检查缓冲区是否 Fill 到目标水平.
     * 用于决定是否开始播放或调整播放速率.
     */
    public boolean isBufferFilledToTarget() {
        int currentSize = bufferStrategy.size();
        // 简单检查: 缓冲区大小是否达到最小目标
        return currentSize >= 60; // 至少 2 秒 @ 30fps
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
        pendingFrames.clear();
    }

    /**
     * 设置目标视频缓冲大小 (帧数).
     * 默认 180 帧 (6 秒 @ 30fps).
     */
    public void setTargetVideoBufferSize(int frames) {
        this.targetVideoBufferSize = frames;
    }

    /**
     * 设置目标音频缓冲大小 (采样数).
     * 默认 288000 采样 (6 秒 @ 48kHz).
     */
    public void setTargetAudioBufferSize(int samples) {
        this.targetAudioBufferSize = samples;
    }
}
