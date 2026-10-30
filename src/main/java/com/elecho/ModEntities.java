package com.elecho;

import com.elecho.entity.EchoEntity;
import com.elecho.entity.ObserverEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public class ModEntities {
    /** Nunca aparece por spawn natural (SpawnGroup.MISC): solo el mod lo crea, y nunca se guarda en el mundo. */
    public static final EntityType<EchoEntity> ECHO = Registry.register(Registries.ENTITY_TYPE, ElEcho.id("echo"),
            EntityType.Builder.create(EchoEntity::new, SpawnGroup.MISC)
                    .dimensions(0.6f, 1.8f).eyeHeight(1.62f).maxTrackingRange(10).build("echo"));

    public static final EntityType<ObserverEntity> OBSERVER = Registry.register(Registries.ENTITY_TYPE, ElEcho.id("observer"),
            EntityType.Builder.create(ObserverEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.6f, 2.9f).maxTrackingRange(12).makeFireImmune().build("observer"));

    public static void register() {
        FabricDefaultAttributeRegistry.register(ECHO, EchoEntity.createEchoAttributes());
        FabricDefaultAttributeRegistry.register(OBSERVER, ObserverEntity.createObserverAttributes());
    }
}
