package dev.scapking.rendcraft.window;

import dev.scapking.rendcraft.protocol.WindowHandle;

import java.util.List;
import java.util.Objects;

/**
 * A saved arrangement of windows. Templates are referenced by name and
 * are either temporary (in-memory only) or permanent (persisted to
 * configuration once we have a config layer).
 *
 * <p>The class lives next to {@link TemplateLayoutManager} because the
 * manager is the only thing that creates and mutates templates; making
 * it package-private keeps the mutation surface small.
 */
final class LayoutTemplate {
    private final String name;
    private final List<WindowHandle> windows;
    private final boolean permanent;
    private TemplateSlotConfig slotConfig;

    LayoutTemplate(String name, List<WindowHandle> windows, boolean permanent) {
        this.name = Objects.requireNonNull(name, "name");
        this.windows = List.copyOf(Objects.requireNonNull(windows, "windows"));
        this.permanent = permanent;
    }

    String getName() {
        return name;
    }

    List<WindowHandle> getWindows() {
        return windows;
    }

    boolean isPermanent() {
        return permanent;
    }

    TemplateSlotConfig getSlotConfig() {
        return slotConfig;
    }

    void setSlotConfig(TemplateSlotConfig slotConfig) {
        this.slotConfig = slotConfig;
    }
}
