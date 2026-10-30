package com.elecho.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

import java.util.Optional;

/** Estructura procedural: "mansion", "carriage" o "chapel" (campo kind del JSON). */
public class EcoStructure extends Structure {
    public static final MapCodec<EcoStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            configCodecBuilder(i),
            Codec.STRING.fieldOf("kind").forGetter(s -> s.kind)
    ).apply(i, EcoStructure::new));

    private final String kind;

    public EcoStructure(Config config, String kind) {
        super(config);
        this.kind = kind;
    }

    @Override
    protected Optional<StructurePosition> getStructurePosition(Context ctx) {
        int w = EcoPiece.width(kind), d = EcoPiece.depth(kind);
        int x = ctx.chunkPos().getStartX() + 2, z = ctx.chunkPos().getStartZ() + 2;
        int[][] corners = {{0, 0}, {w, 0}, {0, d}, {w, d}, {w / 2, d / 2}};
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int[] c : corners) {
            int cx = x + c[0], cz = z + c[1];
            int h = ctx.chunkGenerator().getHeightInGround(cx, cz, Heightmap.Type.WORLD_SURFACE_WG, ctx.world(), ctx.noiseConfig());
            VerticalBlockSample col = ctx.chunkGenerator().getColumnSample(cx, cz, ctx.world(), ctx.noiseConfig());
            if (!col.getState(h - 1).getFluidState().isEmpty()) return Optional.empty(); // sin agua
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        if (max - min > 5) return Optional.empty();   // demasiado irregular
        final int y = min;
        BlockBox box = new BlockBox(x, y - 8, z, x + w - 1, y + EcoPiece.height(kind), z + d - 1);
        return Optional.of(new StructurePosition(new BlockPos(x, y, z),
                collector -> collector.addPiece(new EcoPiece(kind, box))));
    }

    @Override
    public StructureType<?> getType() { return ModStructures.ECO_STRUCTURE_TYPE; }
}
