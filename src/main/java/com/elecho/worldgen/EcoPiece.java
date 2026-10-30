package com.elecho.worldgen;

import net.minecraft.block.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.loot.LootTables;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;

/**
 * Pieza única que construye procedimentalmente las 3 estructuras.
 * Coordenadas relativas: x,z desde la esquina mínima; y = 0 es el primer bloque de aire sobre el suelo
 * (y = -1 es la superficie del terreno). Internamente la caja empieza 8 bloques bajo el suelo.
 */
public class EcoPiece extends StructurePiece {
    private static final int G = 8;           // desplazamiento y de la caja bajo el suelo
    private final String kind;

    public EcoPiece(String kind, BlockBox box) {
        super(ModStructures.ECO_PIECE, 0, box);
        this.kind = kind;
    }

    public EcoPiece(NbtCompound nbt) {
        super(ModStructures.ECO_PIECE, nbt);
        this.kind = nbt.getString("Kind");
    }

    @Override
    protected void writeNbt(StructureContext context, NbtCompound nbt) { nbt.putString("Kind", kind); }

    public static int width(String k) { return switch (k) { case "mansion" -> 19; case "carriage" -> 21; default -> 15; }; }
    public static int depth(String k) { return switch (k) { case "mansion" -> 15; case "carriage" -> 11; default -> 25; }; }
    public static int height(String k) { return switch (k) { case "mansion" -> 18; case "carriage" -> 8; default -> 14; }; }

    // ---------------------------------------------------------------- ayudas
    private static BlockState b(Block b) { return b.getDefaultState(); }
    private static final BlockState AIR = Blocks.AIR.getDefaultState();

    private void fill(StructureWorldAccess w, BlockBox c, int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        fillWithOutline(w, c, x1, y1 + G, z1, x2, y2 + G, z2, s, s, false);
    }

    private void put(StructureWorldAccess w, BlockBox c, BlockState s, int x, int y, int z) {
        addBlock(w, s, x, y + G, z, c);
    }

    private static BlockState brick(Random r) {
        int n = r.nextInt(10);
        if (n < 5) return b(Blocks.STONE_BRICKS);
        if (n < 7) return b(Blocks.MOSSY_STONE_BRICKS);
        if (n < 9) return b(Blocks.CRACKED_STONE_BRICKS);
        return b(Blocks.DEEPSLATE_BRICKS);
    }

    private static BlockState pane(boolean alongX) {
        return b(Blocks.GLASS_PANE).with(PaneBlock.EAST, alongX).with(PaneBlock.WEST, alongX)
                .with(PaneBlock.NORTH, !alongX).with(PaneBlock.SOUTH, !alongX);
    }

    private static BlockState stairs(Block s, Direction f) { return b(s).with(StairsBlock.FACING, f); }

    private void walls(StructureWorldAccess w, BlockBox c, Random r, int x1, int z1, int x2, int z2, int y1, int y2, float hole) {
        for (int y = y1; y <= y2; y++)
            for (int x = x1; x <= x2; x++)
                for (int z = z1; z <= z2; z++) {
                    if (x != x1 && x != x2 && z != z1 && z != z2) continue;
                    if (hole > 0 && r.nextFloat() < hole * (y >= 3 ? 1.0f : 0.25f)) continue;
                    put(w, c, brick(r), x, y, z);
                }
    }

    private void webs(StructureWorldAccess w, BlockBox c, Random r, int n, int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int i = 0; i < n; i++) {
            int x = x1 + r.nextInt(x2 - x1 + 1), y = y1 + r.nextInt(y2 - y1 + 1), z = z1 + r.nextInt(z2 - z1 + 1);
            put(w, c, b(Blocks.COBWEB), x, y, z);
        }
    }

    private void candles(StructureWorldAccess w, BlockBox c, Random r, int n, int x1, int y, int z1, int x2, int z2) {
        for (int i = 0; i < n; i++) {
            int x = x1 + r.nextInt(x2 - x1 + 1), z = z1 + r.nextInt(z2 - z1 + 1);
            put(w, c, b(Blocks.CANDLE).with(CandleBlock.CANDLES, 1 + r.nextInt(3)).with(CandleBlock.LIT, r.nextInt(3) != 0), x, y, z);
        }
    }

    @Override
    public void generate(StructureWorldAccess world, StructureAccessor sa, ChunkGenerator cg, Random random,
                         BlockBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        switch (kind) {
            case "mansion" -> mansion(world, chunkBox, random);
            case "carriage" -> carriage(world, chunkBox, random);
            default -> chapel(world, chunkBox, random);
        }
    }

    // ================================================================ MANSIÓN GÓTICA
    private void mansion(StructureWorldAccess w, BlockBox c, Random r) {
        final int W = 19, D = 15;
        fill(w, c, 0, 0, 0, W - 1, 20, D - 1, AIR);                                // despejar
        fill(w, c, 0, -7, 0, W - 1, -2, D - 1, b(Blocks.COBBLESTONE));              // cimientos
        fill(w, c, 0, -1, 0, W - 1, -1, D - 1, b(Blocks.DARK_OAK_PLANKS));          // suelo planta baja
        walls(w, c, r, 0, 0, W - 1, D - 1, 0, 4, 0.0f);                              // planta baja
        fill(w, c, 1, 5, 1, W - 2, 5, D - 2, b(Blocks.DARK_OAK_PLANKS));            // forjado
        walls(w, c, r, 0, 0, W - 1, D - 1, 5, 9, 0.0f);                              // planta alta
        fill(w, c, 1, 10, 1, W - 2, 10, D - 2, b(Blocks.DARK_OAK_PLANKS));          // techo planta alta

        // tejado a dos aguas escalonado (deepslate)
        for (int l = 0; l <= 7; l++)
            fill(w, c, l, 11 + l, l, W - 1 - l, 11 + l, D - 1 - l, b(Blocks.DEEPSLATE_BRICKS));
        // tejado roto
        for (int i = 0; i < 30; i++) put(w, c, AIR, 2 + r.nextInt(W - 4), 11 + r.nextInt(3), 2 + r.nextInt(D - 4));

        // torreones góticos en las esquinas
        int[][] t = {{0, 0}, {W - 3, 0}, {0, D - 3}, {W - 3, D - 3}};
        for (int[] p : t) {
            for (int y = 0; y <= 13; y++)
                for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
                    if (x != 1 || z != 1) put(w, c, brick(r), p[0] + x, y, p[1] + z);
            fill(w, c, p[0], 14, p[1], p[0] + 2, 14, p[1] + 2, b(Blocks.DEEPSLATE_BRICKS));
            fill(w, c, p[0] + 1, 15, p[1] + 1, p[0] + 1, 17, p[1] + 1, b(Blocks.DEEPSLATE_BRICK_WALL));
        }

        // ventanas (algunas rotas)
        for (int floor = 0; floor < 2; floor++) {
            int y0 = 2 + floor * 5;
            for (int x = 4; x <= W - 5; x += 3) for (int y = y0; y <= y0 + 1; y++) {
                if (floor == 0 && x >= W / 2 - 1 && x <= W / 2 + 1) continue;
                put(w, c, r.nextInt(100) < 55 ? pane(true) : AIR, x, y, 0);
                put(w, c, r.nextInt(100) < 55 ? pane(true) : AIR, x, y, D - 1);
            }
            for (int z = 4; z <= D - 5; z += 3) for (int y = y0; y <= y0 + 1; y++) {
                put(w, c, r.nextInt(100) < 55 ? pane(false) : AIR, 0, y, z);
                put(w, c, r.nextInt(100) < 55 ? pane(false) : AIR, W - 1, y, z);
            }
        }
        // puerta principal (arco abierto)
        fill(w, c, W / 2 - 1, 0, 0, W / 2 + 1, 3, 0, AIR);
        put(w, c, b(Blocks.DEEPSLATE_BRICK_STAIRS), W / 2 - 1, 4, 0);

        // tabiques interiores con pasos
        for (int x : new int[]{6, 12}) {
            fill(w, c, x, 0, 1, x, 4, D - 2, b(Blocks.STONE_BRICKS));
            fill(w, c, x, 0, 6, x, 2, 8, AIR);
            fill(w, c, x, 6, 1, x, 9, D - 2, b(Blocks.DARK_OAK_PLANKS));
            fill(w, c, x, 6, 6, x, 8, 8, AIR);
        }
        // escalera central
        fill(w, c, 8, 5, 3, 9, 5, 7, AIR);
        for (int i = 0; i <= 5; i++) {
            if (i > 0) fill(w, c, 8, 0, 2 + i, 9, i - 1, 2 + i, b(Blocks.DARK_OAK_PLANKS));
            fill(w, c, 8, i, 2 + i, 9, i, 2 + i, stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH));
        }
        // decoración
        webs(w, c, r, 40, 1, 1, 1, W - 2, 9, D - 2);
        candles(w, c, r, 10, 1, 0, 1, W - 2, D - 2);
        candles(w, c, r, 8, 1, 6, 1, W - 2, D - 2);
        for (int i = 0; i < 14; i++) put(w, c, AIR, 1 + r.nextInt(W - 2), 0, 1 + r.nextInt(D - 2)); // suelo hundido, polvo
        addChest(w, c, r, 3, 0, D - 3, LootTables.WOODLAND_MANSION_CHEST);
        addChest(w, c, r, W - 4, 6, 3, LootTables.ABANDONED_MINESHAFT_CHEST);
        addChest(w, c, r, W - 3, 0, D - 3, LootTables.SIMPLE_DUNGEON_CHEST);
        put(w, c, b(Blocks.SOUL_LANTERN), 9, 0, D - 2);
        put(w, c, b(Blocks.SKELETON_SKULL), 4, 6, 2);
    }

    // ================================================================ CARRUAJE ABANDONADO
    private void carriage(StructureWorldAccess w, BlockBox c, Random r) {
        final int W = 21, D = 11;
        fill(w, c, 0, 0, 0, W - 1, 7, D - 1, AIR);
        for (int x = 0; x < W; x++) {
            for (int z = 2; z <= 8; z++) {
                int n = r.nextInt(10);
                put(w, c, n < 6 ? b(Blocks.DIRT_PATH) : n < 8 ? b(Blocks.COARSE_DIRT) : b(Blocks.GRAVEL), x, -1, z);
            }
            if (r.nextInt(3) == 0) put(w, c, b(Blocks.PODZOL), x, -1, 1);
            if (r.nextInt(3) == 0) put(w, c, b(Blocks.PODZOL), x, -1, 9);
        }
        int cx = 6, cz = 3;
        fill(w, c, cx, 1, cz, cx + 6, 1, cz + 4, b(Blocks.DARK_OAK_PLANKS));       // plataforma
        // ruedas (cruz de vallas + barras); una falta
        int missing = r.nextInt(4);
        int[][] wheels = {{cx + 1, cz - 1}, {cx + 5, cz - 1}, {cx + 1, cz + 5}, {cx + 5, cz + 5}};
        for (int i = 0; i < 4; i++) {
            if (i == missing) continue;
            int wx = wheels[i][0], wz = wheels[i][1];
            put(w, c, b(Blocks.IRON_BARS), wx, 1, wz);
            put(w, c, b(Blocks.DARK_OAK_FENCE), wx - 1, 1, wz);
            put(w, c, b(Blocks.DARK_OAK_FENCE), wx + 1, 1, wz);
            put(w, c, b(Blocks.DARK_OAK_FENCE), wx, 0, wz);
            put(w, c, b(Blocks.DARK_OAK_FENCE), wx, 2, wz);
        }
        // cabina
        for (int y = 2; y <= 3; y++)
            for (int x = cx + 1; x <= cx + 5; x++) for (int z = cz; z <= cz + 4; z++) {
                if (x != cx + 1 && x != cx + 5 && z != cz && z != cz + 4) continue;
                BlockState s = b(Blocks.DARK_OAK_PLANKS);
                if (y == 3 && (z == cz || z == cz + 4) && x == cx + 3) s = r.nextInt(2) == 0 ? pane(true) : AIR;
                if (r.nextInt(12) == 0) s = AIR;
                put(w, c, s, x, y, z);
            }
        fill(w, c, cx + 3, 2, cz, cx + 3, 3, cz, AIR);                              // puerta
        for (int x = cx + 1; x <= cx + 5; x++) for (int z = cz; z <= cz + 4; z++)
            if (r.nextInt(10) < 6) put(w, c, b(Blocks.DARK_OAK_SLAB), x, 4, z);       // techo a medias
        addChest(w, c, r, cx + 3, 2, cz + 3, LootTables.VILLAGE_PLAINS_CHEST);
        webs(w, c, r, 10, cx + 2, 2, cz + 1, cx + 4, 3, cz + 3);
        // lanza de enganche y restos del caballo
        for (int x = cx + 6; x <= cx + 10; x++) put(w, c, b(Blocks.DARK_OAK_FENCE), x, 1, cz + 2);
        put(w, c, b(Blocks.BONE_BLOCK), cx + 12, 0, cz + 2);
        put(w, c, b(Blocks.BONE_BLOCK), cx + 13, 0, cz + 1);
        put(w, c, b(Blocks.SKELETON_SKULL), cx + 13, 0, cz + 3);
        // farol roto en un poste
        fill(w, c, 2, 0, 1, 2, 3, 1, b(Blocks.DARK_OAK_FENCE));
        put(w, c, b(Blocks.DARK_OAK_FENCE), 3, 3, 1);
        put(w, c, b(Blocks.SOUL_LANTERN).with(LanternBlock.HANGING, true), 3, 2, 1);
        for (int i = 0; i < 8; i++) put(w, c, b(Blocks.DEAD_BUSH), r.nextInt(W), 0, r.nextBoolean() ? 1 : 9);
        put(w, c, b(Blocks.COBWEB), cx, 2, cz);
    }

    // ================================================================ CAPILLA EN RUINAS
    private void chapel(StructureWorldAccess w, BlockBox c, Random r) {
        final int X1 = 2, X2 = 12, Z1 = 7, Z2 = 24;
        fill(w, c, 0, 0, 0, 14, 14, 24, AIR);
        fill(w, c, X1, -7, Z1, X2, -2, Z2, b(Blocks.COBBLESTONE));
        for (int x = X1; x <= X2; x++) for (int z = Z1; z <= Z2; z++) put(w, c, brick(r), x, -1, z);
        walls(w, c, r, X1, Z1, X2, Z2, 0, 6, 0.10f);
        fill(w, c, 6, 0, Z1, 8, 3, Z1, AIR);                                         // portón
        put(w, c, b(Blocks.STONE_BRICK_STAIRS), 5, 3, Z1);
        // ventanas ojivales rotas
        for (int z = 10; z <= 22; z += 3) for (int y = 2; y <= 4; y++) {
            put(w, c, r.nextInt(2) == 0 ? pane(false) : AIR, X1, y, z);
            put(w, c, r.nextInt(2) == 0 ? pane(false) : AIR, X2, y, z);
        }
        // tejado a dos aguas roto
        for (int l = 0; l <= 5; l++) {
            for (int z = Z1; z <= Z2; z++) {
                if (r.nextInt(100) < 55) put(w, c, stairs(Blocks.STONE_BRICK_STAIRS, Direction.EAST), X1 + l, 7 + l, z);
                if (r.nextInt(100) < 55) put(w, c, stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST), X2 - l, 7 + l, z);
            }
        }
        for (int z = Z1; z <= Z2; z++) if (r.nextInt(100) < 50) put(w, c, b(Blocks.STONE_BRICK_SLAB), 7, 12, z);
        // columnas rotas
        for (int z : new int[]{12, 18}) for (int x : new int[]{3, 11}) {
            int h = 2 + r.nextInt(5);
            for (int y = 0; y < h; y++) put(w, c, b(Blocks.STONE_BRICK_WALL), x, y, z);
        }
        // bancos (algunos rotos)
        for (int z = 10; z <= 19; z += 2) for (int x : new int[]{4, 5, 9, 10})
            if (r.nextInt(100) < 65) put(w, c, stairs(Blocks.DARK_OAK_STAIRS, Direction.NORTH), x, 0, z);
        // altar
        fill(w, c, 4, 0, 21, 10, 0, 23, b(Blocks.STONE_BRICKS));
        put(w, c, b(Blocks.CHISELED_STONE_BRICKS), 7, 1, 22);
        put(w, c, b(Blocks.CANDLE).with(CandleBlock.CANDLES, 3).with(CandleBlock.LIT, true), 7, 2, 22);
        put(w, c, b(Blocks.SOUL_LANTERN), 5, 1, 22);
        put(w, c, b(Blocks.SOUL_LANTERN), 9, 1, 22);
        addChest(w, c, r, 7, 1, 23, LootTables.SIMPLE_DUNGEON_CHEST);
        webs(w, c, r, 30, X1 + 1, 1, Z1 + 1, X2 - 1, 6, Z2 - 1);
        candles(w, c, r, 6, X1 + 1, 0, Z1 + 1, X2 - 1, Z2 - 1);
        // camino y cementerio
        for (int z = 0; z < Z1; z++) for (int x = 6; x <= 8; x++)
            put(w, c, r.nextBoolean() ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.COBBLESTONE), x, -1, z);
        for (int x = 1; x <= 13; x++) for (int z = 0; z < Z1; z++) {
            if (x >= 6 && x <= 8) continue;
            if (r.nextInt(3) == 0) put(w, c, r.nextBoolean() ? b(Blocks.COARSE_DIRT) : b(Blocks.PODZOL), x, -1, z);
        }
        for (int i = 0; i < 9; i++) {
            int x = 1 + r.nextInt(13), z = 1 + r.nextInt(5);
            if (x >= 6 && x <= 8) continue;
            if (r.nextBoolean()) {
                put(w, c, b(Blocks.MOSSY_COBBLESTONE_WALL), x, 0, z);
                put(w, c, b(Blocks.MOSSY_COBBLESTONE_SLAB), x, 1, z);
            } else {
                fill(w, c, x, 0, z, x, 2, z, b(Blocks.DARK_OAK_FENCE));
                put(w, c, b(Blocks.DARK_OAK_FENCE), Math.min(14, x + 1), 2, z);
                put(w, c, b(Blocks.DARK_OAK_FENCE), Math.max(0, x - 1), 2, z);
            }
        }
        for (int i = 0; i < 6; i++) put(w, c, b(Blocks.DEAD_BUSH), 1 + r.nextInt(13), 0, r.nextInt(6));
    }
}
