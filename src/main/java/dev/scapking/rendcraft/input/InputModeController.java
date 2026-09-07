package dev.scapking.rendcraft.input;

import dev.scapking.rendcraft.protocol.WindowHandle;

/**
 * 輸入模式轉換執行器。
 * 管理輸入模式間的合法轉換，防止衝突。
 */
public class InputModeController {
    private InputMode currentMode = InputMode.FREE;
    private WindowHandle focusedWindow = null;

    public InputMode getCurrentMode() {
        return currentMode;
    }

    public WindowHandle getFocusedWindow() {
        return focusedWindow;
    }

    public void setFocusedWindow(WindowHandle handle) {
        this.focusedWindow = handle;
    }

    public boolean enterMode(InputMode targetMode) {
        switch (targetMode) {
            case FREE:
                currentMode = InputMode.FREE;
                focusedWindow = null;
                return true;

            case NAVIGATION_CAPTURE:
            case FULL_CAPTURE:
            case LAYOUT_ADJUSTMENT:
                throw new IllegalStateException("Cannot directly enter restricted mode");
        }
        return false;
    }

    public boolean transitionFrom(InputMode from, InputMode to) {
        if (currentMode != from) {
            throw new IllegalStateException("Current mode is not " + from);
        }
        return enterMode(to);
    }

    public void releaseAll() {
        currentMode = InputMode.FREE;
        focusedWindow = null;
    }
}
