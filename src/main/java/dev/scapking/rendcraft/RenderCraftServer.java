package dev.scapking.rendcraft;

import dev.scapking.rendcraft.command.RcCommandTree;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dedicated-server-only entrypoint. The {@code /rc} command tree lives
 * in {@code src/server/java} because it references
 * {@code net.minecraft.commands.CommandSourceStack}, so the registration
 * callback has to live next to it.
 */
public class RenderCraftServer implements DedicatedServerModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("rendcraft-server");

    @Override
    public void onInitializeServer() {
        LOGGER.info("RenderCraft initialized (dedicated server side)");

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            RcCommandTree.register(dispatcher);
            LOGGER.info("Registered /rc command tree (env={})", environment);
        });
    }
}
