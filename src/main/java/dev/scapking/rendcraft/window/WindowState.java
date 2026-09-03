package dev.scapking.rendcraft.window;

import dev.scapking.rendcraft.protocol.WindowHandle;

/**
 * 窗口状态机。
 * 每个窗口在 RenderCraft 中都有明确的生命周期状态，
 * 避免隐式转换和不确定行为。
 */
public enum WindowState {
    /**
     * 尚未接入 RenderCraft 管理。
     */
    NONE,

    /**
     * 已注册到协议后端，但尚未创建渲染实体。
     */
    REGISTERED,

    /**
     * 正在创建渲染实体。
     */
    CREATING,

    /**
     * 可用的窗口，可以捕获、显示、交互。
     */
    ACTIVE,

    /**
     * 被隐藏（仍存在，未销毁）。
     */
    HIDDEN,

    /**
     * 正在关闭。
     */
    CLOSING,

    /**
     * 已销毁，不可再使用。
     */
    DESTROYED
}
