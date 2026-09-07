package dev.scapking.rendcraft.ime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IME 输入法桥接器。
 * 基于原生 Minecraft text-input v3 接口，不依赖模拟层。
 */
public class ImeInputBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImeInputBridge.class);
    private boolean active = false;
    private String currentText = "";
    private int cursorPosition = 0;
    public void activate() { active = true; LOGGER.info("IME bridge activated"); }
    public void deactivate() { active = false; LOGGER.info("IME bridge deactivated"); }
    public boolean isActive() { return active; }
    public void setText(String text) { this.currentText = text; }
    public String getText() { return currentText; }
    public void setCursorPosition(int position) { this.cursorPosition = position; }
    public int getCursorPosition() { return cursorPosition; }
    public void processImeEvent(ImeEvent event) {
        switch (event.getType()) {
            case COMMIT_STRING:
                currentText = event.getString();
                LOGGER.info("IME committed string: {}", currentText);
                break;
            case PREEDIT_STRING:
                LOGGER.info("IME preedit: {}", event.getString());
                break;
            case CURSOR_POS:
                cursorPosition = event.getCursorPos();
                break;
            default:
                LOGGER.warn("Unknown IME event type: {}", event.getType());
        }
    }
    public enum EventType { COMMIT_STRING, PREEDIT_STRING, CURSOR_POS, DELETE_SURROUNDING, DISABLE, ENABLE }
    public static class ImeEvent {
        private EventType type;
        private String text;
        private int cursorPos;
        public ImeEvent(EventType type) { this.type = type; }
        public ImeEvent(String text) { this.type = EventType.COMMIT_STRING; this.text = text; }
        public EventType getType() { return type; }
        public String getString() { return text; }
        public int getCursorPos() { return cursorPos; }
    }
}
