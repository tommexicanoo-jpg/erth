package com.elecho;

import com.elecho.item.LighterItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.item.VerticallyAttachableBlockItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.math.Direction;

public class ModItems {
    public static final Item EXTINGUISHED_TORCH = Registry.register(Registries.ITEM, ElEcho.id("extinguished_torch"),
            new VerticallyAttachableBlockItem(ModBlocks.EXTINGUISHED_TORCH, ModBlocks.EXTINGUISHED_WALL_TORCH,
                    new Item.Settings(), Direction.DOWN));

    public static final Item ECHO_MIRROR = Registry.register(Registries.ITEM, ElEcho.id("echo_mirror"),
            new BlockItem(ModBlocks.ECHO_MIRROR, new Item.Settings()));

    /** El mechero (de Elías): enciende antorchas apagadas, hogueras, etc. Funciona como pedernal y acero. */
    public static final Item LIGHTER = Registry.register(Registries.ITEM, ElEcho.id("lighter"),
            new LighterItem(new Item.Settings().maxDamage(EchoConfig.INSTANCE.lighterUses)));

    public static final Item OBSERVER_SPAWN_EGG = Registry.register(Registries.ITEM, ElEcho.id("observer_spawn_egg"),
            new SpawnEggItem(ModEntities.OBSERVER, 0x050508, 0xE01010, new Item.Settings()));

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(e -> {
            e.add(EXTINGUISHED_TORCH);
            e.add(ECHO_MIRROR);
            e.add(LIGHTER);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(e -> e.add(LIGHTER));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(e -> e.add(OBSERVER_SPAWN_EGG));
    }
}
