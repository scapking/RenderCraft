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
 * 有界音视频缓冲实现。
 * 按 FIFO 顺序管理帧，支持视频和音频帧的分开统计和丢弃策略。
 * 
 * 特性：
 * - 分别管理视频和音频帧的队列
 * - 支持丢弃最旧帧或最新帧的策略
 * - 线程安全
 * - 记录丢弃帧计数
 */
public class BoundedAudioVideoBuffer implements BufferStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(BoundedAudioVideoBuffer.class);

    private final int videoCapacity;
    private final int audioCapacity;
    private final DropPolicy dropPolicy;
    private final Queue<Frame> videoQueue;
    private final Queue<Frame> audioQueue;
    private final AtomicLong droppedVideoFrames;
    private final AtomicLong droppedAudioFrames;
    private final Object lock = new Object();

    public BoundedAudioVideoBuffer(int videoCapacity, int audioCapacity, DropPolicy dropPolicy) {
        this.videoCapacity = videoCapacity;
        this.audioCapacity = audioCapacity;
        this.dropPolicy = dropPolicy;
        this.videoQueue = new ConcurrentLinkedQueue<>();
        this.audioQueue = new ConcurrentLinkedQueue<>();
        this.droppedVideoFrames = new AtomicLong(0);
        this.droppedAudioFrames = new AtomicLong(0);
    }

    public BoundedAudioVideoBuffer(int videoCapacity, int audioCapacity) {
        this(videoCapacity, audioCapacity, DropPolicy.DROP_OLDEST);
    }

    @Override
    public boolean push(Frame frame) {
        if (frame == null) {
            return false;
        }
        synchronized (lock) {
            Queue<Frame> queue = frame.getType() == FrameType.VIDEO ? videoQueue : audioQueue;
            int capacity = frame.getType() == FrameType.VIDEO ? videoCapacity : audioCapacity;
            AtomicLong droppedCounter = frame.getType() == FrameType.VIDEO ? droppedVideoFrames : droppedAudioFrames;

            if (queue.size() >= capacity) {
                if (dropPolicy == DropPolicy.DROP_OLDEST) {
                    Frame removed = queue.poll();
                    if (removed != null) {
                        droppedCounter.incrementAndGet();
                        LOGGER.debug("Dropped oldest {} frame (capacity={})", frame.getType(), capacity);
                    }
                } else if (dropPolicy == DropPolicy.DROP_NEWEST) {
                    droppedCounter.incrementAndGet();
                    LOGGER.debug("Dropped newest {} frame (capacity={})", frame.getType(), capacity);
                    return false;
                }
            }
            boolean added = queue.offer(frame);
            if (!added) {
                droppedCounter.incrementAndGet();
            }
            return added;
        }
    }

    @Override
    public Frame pop() {
        synchronized (lock) {
            Frame video = videoQueue.poll();
            if (video != null) {
                return video;
            }
            Frame audio = audioQueue.poll();
            if (audio != null) {
                return audio;
            }
            return null;
        }
    }

    @Override
    public Frame peek() {
        synchronized (lock) {
            Frame video = videoQueue.peek();
            if (video != null) {
                return video;
            }
            Frame audio = audioQueue.peek();
            if (audio != null) {
                return audio;
            }
            return null;
        }
    }

    @Override
    public int size() {
        synchronized (lock) {
            return videoQueue.size() + audioQueue.size();
        }
    }

    @Override
    public int getDroppedFrames() {
        return droppedVideoFrames.get() + droppedAudioFrames.get();
    }

    @Override
    public void clear() {
        synchronized (lock) {
            videoQueue.clear();
            audioQueue.clear();
        }
    }

    public int getVideoQueueSize() {
        synchronized (lock) {
            return videoQueue.size();
        }
    }

    public int getAudioQueueSize() {
        synchronized (lock) {
            return audioQueue.size();
        }
    }

    public long getDroppedVideoFrames() {
        return droppedVideoFrames.get();
    }

    public long getDroppedAudioFrames() {
        return droppedAudioFrames.get();
    }

    /**
     * 丢弃策略枚举。
     */
    public enum DropPolicy {
        /** 丢弃最旧的帧 */
        DROP_OLDEST,
        /** 丢弃最新的帧 */
        DROP_NEWEST
    }
}
