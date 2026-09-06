package dev.scapking.rendcraft;

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
    }
}
