package dev.scapking.rendcraft.ime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NativeInputAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(NativeInputAdapter.class);
    private final ImeInputBridge imeBridge;
    private final ImeStatusMonitor statusMonitor;
    public NativeInputAdapter(ImeInputBridge imeBridge, ImeStatusMonitor statusMonitor) {
        this.imeBridge = imeBridge;
        this.statusMonitor = statusMonitor;
    }
    public void handleInputEvent(InputEvent event) {
        if (statusMonitor.isImeEnabled()) {
            imeBridge.processImeEvent(event.toImeEvent());
        } else {
            LOGGER.debug("Direct input handling");
        }
    }
    public static class InputEvent {
        private int keyCode;
        private boolean ctrlDown;
        private boolean shiftDown;
        private boolean altDown;
        public InputEvent(int keyCode, boolean ctrlDown, boolean shiftDown, boolean altDown) {
            this.keyCode = keyCode;
            this.ctrlDown = ctrlDown;
            this.shiftDown = shiftDown;
            this.altDown = altDown;
        }
        public ImeInputBridge.ImeEvent toImeEvent() {
            return new ImeInputBridge.ImeEvent("inputEvent");
        }
    }
}
