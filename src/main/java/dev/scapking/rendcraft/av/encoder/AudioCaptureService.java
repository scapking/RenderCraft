package dev.scapking.rendcraft.av.encoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.nio.ByteBuffer;

/**
 * 音频捕获服务.
 * 从 PipeWire 或系统音频总线捕获音频数据.
 */
public class AudioCaptureService implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(AudioCaptureService.class);
    private volatile boolean running = false;
    private ByteBuffer buffer = ByteBuffer.allocate(48000); // 1 秒 48kHz 16bit 单声道
    private CaptureCallback callback;

    public interface CaptureCallback {
        void onAudioData(byte[] data, int offset, int length, long timestampMs);
    }

    public void setCallback(CaptureCallback callback) {
        this.callback = callback;
    }

    public void start() {
        if (running) return;
        running = true;
        LOGGER.info("Audio capture service started");
        // TODO: 实际 PipeWire 或其他音频后端集成
        captureLoop();
    }

    public void stop() {
        running = false;
        LOGGER.info("Audio capture service stopped");
    }

    private void captureLoop() {
        // 模拟捕获循环，实际实现中使用 PipeWire、 pulseaudio 或其他后端
        while (running) {
            try {
                // 模拟从音频源捕获数据
                byte[] dummyData = new byte[4800]; // 100ms 数据
                if (callback != null) {
                    callback.onAudioData(dummyData, 0, dummyData.length, System.currentTimeMillis());
                }
                Thread.sleep(100); // 100ms 间隔
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.error("Audio capture error", e);
            }
        }
    }

    @Override
    public void close() {
        stop();
    }
}
