package com.elecho;

import com.elecho.net.DreadPayload;
import com.elecho.net.TimerPayload;
import com.elecho.worldgen.ModStructures;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EL ECHO: la fusión de "The Echo" (Elías, los Ecos, la paranoia, el laberinto del sueño)
 * y "El Eco" (la noche eterna, la bruma, el Observador, las antorchas que se apagan).
 */
public class ElEcho implements ModInitializer {
    public static final String MOD_ID = "elecho";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        EchoConfig.load();
        PayloadTypeRegistry.playS2C().register(DreadPayload.ID, DreadPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TimerPayload.ID, TimerPayload.CODEC);
        ModBlocks.register();
        ModItems.register();
        ModEntities.register();
        ModParticles.register();
        ModStructures.register();
        EchoManager.register();
        EchoCommands.register();
        ThanksWatcher.startup();
        LOGGER.info("...aquí nunca amanece. El mundo ya no te recuerda.");
    }
}
