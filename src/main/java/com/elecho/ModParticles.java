package com.elecho;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModParticles {
    /** Par de ojos rojos brillantes pegado a hojas, ventanas y superficies oscuras. */
    public static final SimpleParticleType EYES = FabricParticleTypes.simple(true);

    public static void register() {
        Registry.register(Registries.PARTICLE_TYPE, Identifier.of(ElEcho.MOD_ID, "eyes"), EYES);
    }
}
