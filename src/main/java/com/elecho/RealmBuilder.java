package com.elecho;

import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.*;

/**
 * Genera un laberinto nuevo (aleatorio) de deepslate dentro de elecho:mirror_realm: el recuerdo que la noche
 * guarda de ti cuando duermes. Pasillos de 3 bloques de ancho, muros de 1, techo a 4 de altura.
 */
public final class RealmBuilder {

    public static final int CELL = 3;
    public static final int PITCH = CELL + 1;
    public static final int HEIGHT = 4;
    private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

    public record Result(BlockPos start, BlockPos original, List<BlockPos> traps, List<BlockPos> cells) {}

    private RealmBuilder() {}

    private static BlockState wall(Random r) {
        int n = r.nextInt(10);
        if (n < 6) return Blocks.DEEPSLATE_BRICKS.getDefaultState();
        if (n < 8) return Blocks.CRACKED_DEEPSLATE_BRICKS.getDefaultState();
        return Blocks.COBBLED_DEEPSLATE.getDefaultState();
    }

    private static BlockState floor(Random r) {
        return r.nextInt(6) == 0 ? Blocks.CRACKED_DEEPSLATE_TILES.getDefaultState() : Blocks.DEEPSLATE_TILES.getDefaultState();
    }

    private static void carveCell(boolean[][] solid, int cx, int cz) {
        int x0 = 1 + cx * PITCH, z0 = 1 + cz * PITCH;
        for (int x = x0; x < x0 + CELL; x++) for (int z = z0; z < z0 + CELL; z++) solid[x][z] = false;
    }

    private static void carvePassage(boolean[][] solid, int cx, int cz, int dx, int dz) {
        int x0 = 1 + cx * PITCH, z0 = 1 + cz * PITCH;
        if (dx != 0) {
            int x = dx > 0 ? x0 + CELL : x0 - 1;
            for (int z = z0; z < z0 + CELL; z++) solid[x][z] = false;
        } else {
            int z = dz > 0 ? z0 + CELL : z0 - 1;
            for (int x = x0; x < x0 + CELL; x++) solid[x][z] = false;
        }
    }

    public static Result build(ServerWorld dst, BlockPos origin, Random rand) {
        final int n = Math.max(5, Math.min(11, EchoConfig.INSTANCE.mazeSize));
        final int size = n * PITCH + 1;
        final int y0 = origin.getY();
        final int floorY = y0 - 1;
        final int ceilY = y0 + HEIGHT;

        boolean[][] solid = new boolean[size][size];
        for (boolean[] row : solid) Arrays.fill(row, true);

        // --- laberinto por backtracking ---
        boolean[][] seen = new boolean[n][n];
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        ArrayDeque<int[]> stack = new ArrayDeque<>();
        seen[0][0] = true;
        carveCell(solid, 0, 0);
        stack.push(new int[]{0, 0});
        while (!stack.isEmpty()) {
            int[] c = stack.peek();
            List<int[]> opts = new ArrayList<>();
            for (int[] d : dirs) {
                int nx = c[0] + d[0], nz = c[1] + d[1];
                if (nx >= 0 && nz >= 0 && nx < n && nz < n && !seen[nx][nz]) opts.add(d);
            }
            if (opts.isEmpty()) { stack.pop(); continue; }
            int[] d = opts.get(rand.nextInt(opts.size()));
            int nx = c[0] + d[0], nz = c[1] + d[1];
            seen[nx][nz] = true;
            carveCell(solid, nx, nz);
            carvePassage(solid, c[0], c[1], d[0], d[1]);
            stack.push(new int[]{nx, nz});
        }
        // bucles extra para que los Ecos puedan rodearte
        for (int i = 0; i < n; i++) {
            int cx = rand.nextInt(n), cz = rand.nextInt(n);
            int[] d = dirs[rand.nextInt(4)];
            int nx = cx + d[0], nz = cz + d[1];
            if (nx >= 0 && nz >= 0 && nx < n && nz < n) carvePassage(solid, cx, cz, d[0], d[1]);
        }

        // --- el Original: la casilla más lejana del inicio (BFS) ---
        int[][] dist = new int[size][size];
        for (int[] row : dist) Arrays.fill(row, -1);
        ArrayDeque<int[]> q = new ArrayDeque<>();
        dist[2][2] = 0;
        q.add(new int[]{2, 2});
        int ox = 2, oz = 2, best = 0;
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] d : dirs) {
                int nx = c[0] + d[0], nz = c[1] + d[1];
                if (nx < 0 || nz < 0 || nx >= size || nz >= size) continue;
                if (solid[nx][nz] || dist[nx][nz] >= 0) continue;
                dist[nx][nz] = dist[c[0]][c[1]] + 1;
                if (dist[nx][nz] > best) { best = dist[nx][nz]; ox = nx; oz = nz; }
                q.add(new int[]{nx, nz});
            }
        }

        // --- escribir bloques ---
        BlockState air = Blocks.AIR.getDefaultState();
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                int wx = origin.getX() + x, wz = origin.getZ() + z;
                dst.setBlockState(new BlockPos(wx, floorY, wz), floor(rand), FLAGS);
                dst.setBlockState(new BlockPos(wx, ceilY, wz), Blocks.DEEPSLATE.getDefaultState(), FLAGS);
                for (int y = y0; y < ceilY; y++) {
                    BlockState out = solid[x][z] ? wall(rand) : air;
                    dst.setBlockState(new BlockPos(wx, y, wz), out, FLAGS);
                }
            }
        }

        BlockPos start = new BlockPos(origin.getX() + 2, y0, origin.getZ() + 2);
        BlockPos original = new BlockPos(origin.getX() + ox, y0, origin.getZ() + oz);
        dst.setBlockState(original, ModBlocks.ECHO_ORIGINAL.getDefaultState(), FLAGS);

        // --- celdas y callejones sin salida (trampas de susto) ---
        List<BlockPos> cells = new ArrayList<>();
        List<BlockPos> deadEnds = new ArrayList<>();
        for (int cx = 0; cx < n; cx++) {
            for (int cz = 0; cz < n; cz++) {
                if (cx == 0 && cz == 0) continue;
                int x0 = 1 + cx * PITCH, z0 = 1 + cz * PITCH;
                BlockPos cp = new BlockPos(origin.getX() + x0 + 1, y0, origin.getZ() + z0 + 1);
                if (cp.equals(original)) continue;
                cells.add(cp);
                int open = 0;
                if (!solid[x0 + CELL][z0 + 1]) open++;
                if (!solid[x0 - 1][z0 + 1]) open++;
                if (!solid[x0 + 1][z0 + CELL]) open++;
                if (!solid[x0 + 1][z0 - 1]) open++;
                if (open == 1) deadEnds.add(cp);
            }
        }
        Collections.shuffle(deadEnds, rand);
        List<BlockPos> traps = new ArrayList<>(deadEnds.subList(0, Math.min(6, deadEnds.size())));
        List<BlockPos> shuffled = new ArrayList<>(cells);
        Collections.shuffle(shuffled, rand);
        for (BlockPos c : shuffled) {
            if (traps.size() >= 5) break;
            if (!traps.contains(c)) traps.add(c);
        }
        decorate(dst, origin, solid, n, rand, original);
        return new Result(start, original, traps, cells);
    }

    private static void decorate(ServerWorld w, BlockPos origin, boolean[][] solid, int n, Random rand, BlockPos original) {
        final int y0 = origin.getY();
        final int floorY = y0 - 1;
        final int top = y0 + HEIGHT - 1;
        for (int cx = 0; cx < n; cx++) {
            for (int cz = 0; cz < n; cz++) {
                int x0 = 1 + cx * PITCH, z0 = 1 + cz * PITCH;
                int ax = origin.getX() + x0, az = origin.getZ() + z0;
                boolean openE = !solid[x0 + CELL][z0 + 1], openW = !solid[x0 - 1][z0 + 1];
                boolean openS = !solid[x0 + 1][z0 + CELL], openN = !solid[x0 + 1][z0 - 1];
                int open = (openE ? 1 : 0) + (openW ? 1 : 0) + (openS ? 1 : 0) + (openN ? 1 : 0);
                boolean deadEnd = open == 1;

                // Antorchas en los muros para que se vea (el fondo de un callejón es del espejo)
                if ((cx == 0 && cz == 0) || rand.nextInt(100) < 80) {
                    List<Direction> faces = new ArrayList<>(); // dirección en la que MIRA la antorcha
                    if (!openW && !(deadEnd && openE)) faces.add(Direction.EAST);
                    if (!openE && !(deadEnd && openW)) faces.add(Direction.WEST);
                    if (!openN && !(deadEnd && openS)) faces.add(Direction.SOUTH);
                    if (!openS && !(deadEnd && openN)) faces.add(Direction.NORTH);
                    if (!faces.isEmpty()) {
                        Direction f = faces.get(rand.nextInt(faces.size()));
                        BlockPos tp = switch (f) {
                            case EAST -> new BlockPos(ax, y0 + 1, az + 1);
                            case WEST -> new BlockPos(ax + 2, y0 + 1, az + 1);
                            case SOUTH -> new BlockPos(ax + 1, y0 + 1, az);
                            default -> new BlockPos(ax + 1, y0 + 1, az + 2);
                        };
                        w.setBlockState(tp, Blocks.WALL_TORCH.getDefaultState().with(WallTorchBlock.FACING, f), FLAGS);
                    }
                }
                if (cx == 0 && cz == 0) continue; // la zona de inicio queda limpia

                if (deadEnd) { // callejón sin salida: un espejo contra el muro del fondo y velas
                    Direction face = openE ? Direction.EAST : openW ? Direction.WEST : openS ? Direction.SOUTH : Direction.NORTH;
                    BlockPos mp = openE ? new BlockPos(ax, y0, az + 1)
                            : openW ? new BlockPos(ax + 2, y0, az + 1)
                            : openS ? new BlockPos(ax + 1, y0, az)
                            : new BlockPos(ax + 1, y0, az + 2);
                    BlockState mirror = ModBlocks.ECHO_MIRROR.getDefaultState().with(HorizontalFacingBlock.FACING, face);
                    w.setBlockState(mp, mirror, FLAGS);
                    w.setBlockState(mp.up(), mirror, FLAGS);
                    EchoManager.registerMirror(w, mp);
                    w.setBlockState(new BlockPos(ax + 1, y0, az + 1),
                            Blocks.CANDLE.getDefaultState().with(CandleBlock.CANDLES, 1 + rand.nextInt(4)), FLAGS);
                    continue;
                }

                int r = rand.nextInt(100);
                int tx = ax + rand.nextInt(CELL), tz = az + rand.nextInt(CELL);
                if (r < 10) { // huesos con una calavera
                    w.setBlockState(new BlockPos(tx, y0, tz), Blocks.BONE_BLOCK.getDefaultState(), FLAGS);
                    w.setBlockState(new BlockPos(tx, y0 + 1, tz), Blocks.SKELETON_SKULL.getDefaultState(), FLAGS);
                } else if (r < 20) { // velas apagadas
                    w.setBlockState(new BlockPos(tx, y0, tz),
                            Blocks.CANDLE.getDefaultState().with(CandleBlock.CANDLES, 1 + rand.nextInt(4)), FLAGS);
                } else if (r < 30) { // cadenas colgando
                    w.setBlockState(new BlockPos(tx, top, tz), Blocks.CHAIN.getDefaultState(), FLAGS);
                    w.setBlockState(new BlockPos(tx, top - 1, tz), Blocks.CHAIN.getDefaultState(), FLAGS);
                } else if (r < 42) { // el suelo se vuelve sculk
                    for (int i = 0; i < 4; i++) {
                        w.setBlockState(new BlockPos(ax + rand.nextInt(CELL), floorY, az + rand.nextInt(CELL)), Blocks.SCULK.getDefaultState(), FLAGS);
                    }
                } else if (r < 52) { // musgo y telarañas
                    for (int i = 0; i < 3; i++) {
                        w.setBlockState(new BlockPos(ax + rand.nextInt(CELL), y0, az + rand.nextInt(CELL)), Blocks.MOSS_CARPET.getDefaultState(), FLAGS);
                    }
                    w.setBlockState(new BlockPos(tx, top, tz), Blocks.COBWEB.getDefaultState(), FLAGS);
                }
            }
        }
        // El Original late entre velas
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos p = original.add(dx, 0, dz);
            if ((dx != 0 || dz != 0) && w.getBlockState(p).isAir() && rand.nextInt(2) == 0) {
                w.setBlockState(p, Blocks.CANDLE.getDefaultState().with(CandleBlock.CANDLES, 1 + rand.nextInt(4)).with(CandleBlock.LIT, true), FLAGS);
            }
        }
    }
}
