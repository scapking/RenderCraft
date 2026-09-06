package dev.scapking.rendcraft;

import dev.scapking.rendcraft.protocol.X11Adapter;
import dev.scapking.rendcraft.window.WindowManager;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common-side entrypoint. Runs on every environment but only does work
 * that is safe in the common (shared) source set. Server-only wiring —
 * in particular the {@code /rc} command tree, which references
 * {@code net.minecraft.commands.CommandSourceStack} — lives in
 * {@code src/server/java/.../RenderCraftServer}.
 */
public class RenderCraftCommon implements ModInitializer {
    public static final String MOD_ID = "rendcraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("RenderCraft initialized (common side)");

        // Wire up the runtime singletons. X11 is the only backend we
        // can run from pure Java today; the Wayland backend needs
        // a native bridge (xdg-desktop-portal + PipeWire) and is
        // selected on the client side once that bridge is available.
        RenderCraftRuntime.setWindowManager(new WindowManager());
        RenderCraftRuntime.setBackend(new X11Adapter());
        LOGGER.info("Default backend: X11Adapter (java only; libX11 lazy-loaded)");
    }
}
