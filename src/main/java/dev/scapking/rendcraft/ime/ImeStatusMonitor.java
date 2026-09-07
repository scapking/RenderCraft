package dev.scapking.rendcraft.ime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImeStatusMonitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImeStatusMonitor.class);
    private boolean imeEnabled = false;
    private String imeEngine = "unknown";
    public void setImeEnabled(boolean enabled) { this.imeEnabled = enabled; LOGGER.info("IME enabled: {}", enabled); }
    public boolean isImeEnabled() { return imeEnabled; }
    public void setImeEngine(String engine) { this.imeEngine = engine; LOGGER.info("IME engine: {}", engine); }
    public String getImeEngine() { return imeEngine; }
    public void monitorStatus() {
        if (imeEnabled && "fcitx5".equals(imeEngine)) {
            LOGGER.debug("Fcitx5 IME active");
        } else if (imeEnabled && "ibus".equals(imeEngine)) {
            LOGGER.debug("IBus IME active");
        }
    }
}
