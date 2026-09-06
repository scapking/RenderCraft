package dev.scapking.rendcraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.scapking.rendcraft.ime.ImeInputBridge;
import dev.scapking.rendcraft.ime.ImeStatusMonitor;
import dev.scapking.rendcraft.protocol.ProtocolException;
import dev.scapking.rendcraft.window.WindowManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side hotkey wiring. Three bindings cover the most common
 * RenderCraft entry points from the keyboard:
 * <ul>
 *   <li>R — refresh the window list and show a chat toast with the count.</li>
 *   <li>I — toggle the IME bridge on/off (mirrors {@code /rc input}).</li>
 *   <li>H — hide every known window (mirrors {@code /rc window hide \*}).</li>
 * </ul>
 * Each binding is registered through {@link KeyBindingHelper} so it
 * appears in the standard Minecraft controls menu and is remappable
 * by the user. The handler runs in {@link ClientTickEvents#END_CLIENT_TICK}
 * which is the canonical place to poll for key state.
 */
public final class KeyBindings {
    private static final Logger LOGGER = LoggerFactory.getLogger("rendcraft-keys");
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("rendcraft", "main"));

    private static final KeyMapping REFRESH_WINDOWS = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.rendcraft.refresh_windows",
                    InputConstants.Type.KEYSYM,
                    InputConstants.getKey("key.keyboard.r").getValue(),
                    CATEGORY));

    private static final KeyMapping TOGGLE_IME = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.rendcraft.toggle_ime",
                    InputConstants.Type.KEYSYM,
                    InputConstants.getKey("key.keyboard.i").getValue(),
                    CATEGORY));

    private static final KeyMapping HIDE_ALL = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.rendcraft.hide_all",
                    InputConstants.Type.KEYSYM,
                    InputConstants.getKey("key.keyboard.h").getValue(),
                    CATEGORY));

    private static ImeStatusMonitor activeStatusMonitor;
    private static WindowManager activeWindowManager;
    private static net.minecraft.client.Minecraft client;

    private KeyBindings() {}

    public static void register(ImeStatusMonitor statusMonitor, WindowManager windowManager) {
        activeStatusMonitor = statusMonitor;
        activeWindowManager = windowManager;
        ClientTickEvents.END_CLIENT_TICK.register(KeyBindings::onClientTick);
        LOGGER.info("Registered RenderCraft keybindings: R=refresh, I=toggle IME, H=hide all");
    }

    private static void onClientTick(net.minecraft.client.Minecraft mc) {
        client = mc;
        if (REFRESH_WINDOWS.consumeClick()) {
            handleRefresh();
        }
        if (TOGGLE_IME.consumeClick()) {
            handleToggleIme();
        }
        if (HIDE_ALL.consumeClick()) {
            handleHideAll();
        }
    }

    private static void handleRefresh() {
        if (activeWindowManager == null) {
            LOGGER.warn("Refresh hotkey pressed but WindowManager is not registered yet");
            return;
        }
        // Re-sync windows from the active backend and toast the count.
        try {
            var handles = activeWindowManager.listWindows();
            if (client != null && client.player != null) {
                client.player.displayClientMessage(
                        Component.literal("RenderCraft: " + handles.length + " window(s)"), false);
            }
            LOGGER.info("Refresh hotkey: {} window(s) currently tracked", handles.length);
        } catch (ProtocolException e) {
            LOGGER.warn("Refresh hotkey failed: {}", e.getMessage());
        }
    }

    private static void handleToggleIme() {
        if (activeStatusMonitor == null) {
            LOGGER.warn("IME toggle hotkey pressed but no ImeStatusMonitor is registered");
            return;
        }
        boolean wasOn = activeStatusMonitor.isImeEnabled();
        activeStatusMonitor.setImeEnabled(!wasOn);
        ImeInputBridge bridge = KeyBindings.Lazy.get();
        if (bridge != null) {
            if (!wasOn) {
                bridge.activate();
            } else {
                bridge.deactivate();
            }
        }
        if (client != null && client.player != null) {
            client.player.displayClientMessage(
                    Component.literal("RenderCraft IME " + (!wasOn ? "enabled" : "disabled")), false);
        }
    }

    private static void handleHideAll() {
        if (activeWindowManager == null) {
            return;
        }
        try {
            var handles = activeWindowManager.listWindows();
            for (var h : handles) {
                activeWindowManager.requestHide(h);
            }
            if (client != null && client.player != null) {
                client.player.displayClientMessage(
                        Component.literal("RenderCraft: hid " + handles.length + " window(s)"), false);
            }
        } catch (ProtocolException e) {
            LOGGER.warn("Hide-all hotkey failed: {}", e.getMessage());
        }
    }

    /**
     * Tiny indirection so {@link KeyBindings} does not need a direct
     * reference to an ImeInputBridge instance — the common initializer
     * can register its bridge through this static accessor from any
     * thread before the first key is pressed.
     */
    public static final class Lazy {
        private static volatile ImeInputBridge bridge;

        private Lazy() {}

        public static void set(ImeInputBridge b) {
            bridge = b;
        }

        static ImeInputBridge get() {
            return bridge;
        }
    }
}
