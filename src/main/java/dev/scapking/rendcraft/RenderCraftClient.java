package dev.scapking.rendcraft;

import dev.scapking.rendcraft.client.KeyBindings;
import dev.scapking.rendcraft.ime.ImeInputBridge;
import dev.scapking.rendcraft.ime.ImeStatusMonitor;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-only entrypoint. Wires the {@link KeyBindings} registry into
 * the Fabric key-binding API and registers the IME bridge + status
 * monitor the hotkeys talk to.
 *
 * <p>fabric.mod.json declares this as the {@code client} entrypoint,
 * so fabric-loader will only instantiate it on the physical client.
 * We do not need a separate {@code @EnvironmentInterface} marker.
 */
public class RenderCraftClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("rendcraft-client");

    @Override
    public void onInitializeClient() {
        LOGGER.info("RenderCraft initialized (client side)");

        ImeInputBridge bridge = new ImeInputBridge();
        bridge.deactivate();
        KeyBindings.Lazy.set(bridge);

        ImeStatusMonitor monitor = new ImeStatusMonitor();
        // Default: pretend fcitx5 is the active IME so the toggle
        // hotkey is meaningful in the absence of a real D-Bus signal.
        monitor.setImeEngine("fcitx5");

        KeyBindings.register(monitor, RenderCraftRuntime.getWindowManager());
        LOGGER.info("RenderCraft client keybindings registered");
    }
}
