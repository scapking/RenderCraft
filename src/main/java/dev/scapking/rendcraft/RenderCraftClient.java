package dev.scapking.rendcraft;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side entrypoint stub.
 * Real client wiring (render overlays, key bindings, IMC plumbing) lands
 * once the protocol adapter is in place; the rest of the mod is
 * environment-agnostic and runs from RenderCraftCommon.
 */
public class RenderCraftClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("rendcraft-client");

    @Override
    public void onInitializeClient() {
        LOGGER.info("RenderCraft initialized (client side)");
    }
}
