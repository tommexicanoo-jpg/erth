package com.elecho.worldgen;

import com.elecho.ElEcho;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.world.gen.structure.StructureType;

public class ModStructures {
    public static final StructureType<EcoStructure> ECO_STRUCTURE_TYPE =
            Registry.register(Registries.STRUCTURE_TYPE, Identifier.of(ElEcho.MOD_ID, "eco_structure"),
                    (StructureType<EcoStructure>) () -> EcoStructure.CODEC);

    public static final StructurePieceType ECO_PIECE =
            Registry.register(Registries.STRUCTURE_PIECE, Identifier.of(ElEcho.MOD_ID, "eco_piece"),
                    (StructurePieceType) (ctx, nbt) -> new EcoPiece(nbt));

    public static void register() { /* carga de la clase */ }
}
