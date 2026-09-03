package dev.scapking.rendcraft.protocol;

/**
 * ProtocolException 表明协议操作失败。
 * 上层应根据异常类型决定是降级、重试还是报告错误。
 */
public class ProtocolException extends Exception {
    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
