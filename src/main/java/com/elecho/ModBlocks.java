package com.elecho;

import com.elecho.block.EchoMirrorBlock;
import com.elecho.block.EchoOriginalBlock;
import com.elecho.block.ExtinguishedTorchBlock;
import com.elecho.block.ExtinguishedWallTorchBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

public class ModBlocks {
    private static AbstractBlock.Settings torchSettings() {
        return AbstractBlock.Settings.create().noCollision().breakInstantly()
                .sounds(BlockSoundGroup.WOOD).pistonBehavior(PistonBehavior.DESTROY);
    }

    public static final Block EXTINGUISHED_TORCH = Registry.register(Registries.BLOCK,
            ElEcho.id("extinguished_torch"), new ExtinguishedTorchBlock(torchSettings()));
    public static final Block EXTINGUISHED_WALL_TORCH = Registry.register(Registries.BLOCK,
            ElEcho.id("extinguished_wall_torch"), new ExtinguishedWallTorchBlock(torchSettings().dropsLike(EXTINGUISHED_TORCH)));

    public static final Block ECHO_MIRROR = Registry.register(Registries.BLOCK, ElEcho.id("echo_mirror"),
            new EchoMirrorBlock(AbstractBlock.Settings.create()
                    .mapColor(MapColor.LIGHT_GRAY)
                    .strength(0.6f)
                    .sounds(BlockSoundGroup.GLASS)
                    .nonOpaque()
                    .luminance(s -> 2)
                    .pistonBehavior(PistonBehavior.DESTROY)));

    public static final Block ECHO_ORIGINAL = Registry.register(Registries.BLOCK, ElEcho.id("echo_original"),
            new EchoOriginalBlock(AbstractBlock.Settings.create()
                    .mapColor(MapColor.PURPLE)
                    .strength(-1.0f, 3600000.0f)
                    .sounds(BlockSoundGroup.SCULK)
                    .luminance(s -> 5)
                    .dropsNothing()));

    public static void register() { /* carga de la clase */ }
}
