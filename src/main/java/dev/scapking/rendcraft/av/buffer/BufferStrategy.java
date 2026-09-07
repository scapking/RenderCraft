package dev.scapking.rendcraft.av.buffer;

import java.util.Objects;

/**
 * 音视频缓冲策略接口。
 * 定义缓冲区的基本操作：推入帧、弹出帧、查看队首帧、获取大小、获取丢弃帧计数、清空。
 * 
 * 实现类根据具体策略（先进先出、按时间戳排序、优先级等）进行不同的处理。
 */
public interface BufferStrategy {

    /**
     * 将一个帧推入缓冲区。
     * 如果缓冲区已满，根据策略决定是否丢弃新帧或旧帧。
     *
     * @param frame 要推入的帧
     * @return true 如果帧被成功加入缓冲区，false 如果被丢弃
     */
    boolean push(Frame frame);

    /**
     * 从缓冲区弹出下一个要渲染/播放的帧。
     * 调用此方法后，帧将从缓冲区移除。
     *
     * @return 要处理的帧，如果缓冲区为空则返回 null
     */
    Frame pop();

    /**
     * 查看缓冲区中下一个要处理的帧，但不移除它。
     *
     * @return 队首帧，如果缓冲区为空则返回 null
     */
    Frame peek();

    /**
     * 获取缓冲区中当前存储的帧数。
     *
     * @return 缓冲区大小
     */
    int size();

    /**
     * 获取自缓冲区创建以来丢弃的帧总数。
     * 用于监控和诊断缓冲区压力。
     *
     * @return 丢弃帧计数
     */
    int getDroppedFrames();

    /**
     * 清空缓冲区中的所有帧。
     */
    void clear();

    /**
     * 表示缓冲区中存储的一帧音视频数据。
     * 包含呈现时间戳（ nanos ）和有效载荷数据。
     */
    final class Frame {
        private final long presentationTimeNanos;
        private final byte[] payload;
        private final FrameType type;

        public Frame(long presentationTimeNanos, byte[] payload, FrameType type) {
            this.presentationTimeNanos = presentationTimeNanos;
            this.payload = Objects.requireNonNull(payload, "payload");
            this.type = Objects.requireNonNull(type, "type");
        }

        public long getPresentationTimeNanos() {
            return presentationTimeNanos;
        }

        public byte[] getPayload() {
            return payload;
        }

        public FrameType getType() {
            return type;
        }

        @Override
        public String toString() {
            return "Frame{" +
                    "presentationTimeNanos=" + presentationTimeNanos +
                    ", payloadLength=" + payload.length +
                    ", type=" + type +
                    '}';
        }
    }

    /**
     * 帧类型枚举。
     * 区分视频帧和音频帧，以便采取不同的处理策略。
     */
    enum FrameType {
        VIDEO,
        AUDIO
    }
}
