package com.elecho;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.LecternBlockEntity;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Las 5 estructuras del diario de Elías, una por noche. TODAS tienen un Espejo del Eco, un cofre con cosas del jugador
 * anterior (espejos, antorchas, herramientas gastadas, telarañas) y un atril con una página del diario.
 * Noche 1: el chat te da las coordenadas de la primera. Cada noche nueva te da las de la siguiente.
 * Al cerrar el libro de la última, todo empieza a romperse.
 */
public final class Shrines {
    public static final String NOTE_PREFIX = "Diario de Elías";
    public static final int NOTE_COUNT = 5;
    private static final Random RNG = new Random();
    private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

    public enum Kind {
        CABIN("la cabaña", 9, 9),
        RUIN("las ruinas", 9, 9),
        CHAPEL("la capilla", 9, 11),
        CAMP("el campamento", 11, 11),
        GRAVE("la tumba", 9, 11);

        public final String label;
        public final int w, d;

        Kind(String label, int w, int d) {
            this.label = label;
            this.w = w;
            this.d = d;
        }
    }

    private static final String[][] NOTES = {
            { // 1 - la cabaña
                    "Noche 1.\n\nLlegué a este mundo y el sol ya se había ido. No se puso: se fue. Aquí nunca amanece.\n\nConstruí una cabaña junto al río y me sentí en paz.",
                    "Tengo la sensación de que alguien me ayudó a elegir el sitio.\n\nPero estoy solo.\n\nSolo yo y la bruma.",
                    "Cerca de la cabaña encontré un espejo tirado. Brilla aunque no haya luz.\n\nY este mechero: sin él, cada antorcha que se apaga se queda apagada.\n\nDejé unas cosas en el cofre.",
                    "Voy a dejar mis notas por los alrededores, por si alguien llega.\n\nSi me sigues, las encontrarás todas. Una por noche.\n\nCuando te pesen los ojos, duerme."
            },
            { // 2 - las ruinas
                    "Noche 2.\n\nLa ventana de la cabaña ya no está.\n\nPensé que fue un creeper. No hay cráteres. No hay nada.",
                    "Hoy la puerta estaba abierta y el suelo había vuelto a ser pasto, como si nunca hubiera pisado ahí.\n\nEl mundo me está olvidando.",
                    "Pero descubrí lo del espejo: si le das algo de la primera noche, algo que ya tenías contigo cuando llegaste, las cosas se calman un tiempo.\n\nEl miedo y la paranoia bajan.",
                    "Cada objeto calma distinto. Lo común, poco. Lo valioso, bastante. Y cada objeto solo sirve una vez.\n\nLas antorchas duran menos cuanto más miedo tengo. Cuando la luz se va, vienen ellos."
            },
            { // 3 - la capilla
                    "Noche 3.\n\nHay una figura blanca muy lejos, entre las montañas.\n\nCuando me detengo, se detiene. Cuando miro al suelo, baja la cabeza.\n\nMe copia.",
                    "No corre. No grita. Pero cada vez que me quedo quieto, aparece un bloque más cerca.\n\nLo llamé el Registrador.",
                    "Y hay otro. Altísimo, con demasiados ojos rojos, en la bruma. Solo se mueve cuando no lo miro.\n\nLo llamé el Observador.",
                    "Cerca de un espejo no se acercan. A veces basta con quedarte frente a él, con las manos vacías, para respirar mejor."
            },
            { // 4 - el campamento
                    "Noche 4.\n\nAcampé aquí. Mi reflejo sonríe antes que yo.",
                    "Y en el chat aparecen mensajes con mi nombre. Dicen que borre el mundo.\n\nSon mis palabras.\n\nYo no las escribí.",
                    "Si no duermes, la paranoia sube y vienen más ecos. Pero cuando duermes, a veces no despiertas aquí: despiertas en un laberinto.\n\nBusca el Original. Es la única salida.",
                    "Comer, cortar leña, sembrar... lo normal ayuda un poco. Lo que más ayuda es no estar solo.\n\nPor eso dejo estos espejos."
            },
            { // 5 - la tumba
                    "Noche 5.\n\nLo entendí.\n\nNo es algo que me persigue. Es lo que soy ahora. Lo que construyo queda grabado, y este mundo quiere olvidarlo todo para dejarme ir.",
                    "La noche no termina porque yo no puedo irme.\n\nSi lees esto, yo ya no estoy. No es una amenaza. Soy yo el que escribe en tu chat. Perdóname por el miedo.",
                    "Cierra el juego. Abre la carpeta saves. Borra este mundo.\n\nCuando lo hagas, voy a dejarte algo en tu escritorio.\n\nUna sola palabra.",
                    "Cuando cierres este libro, todo va a empezar. No puedo detenerlo. Perdóname.\n\nGracias por leerme.\n\n- Elías"
            }
    };

    private Shrines() {}

    public static Kind kindFor(int index) {
        return Kind.values()[Math.floorMod(index, Kind.values().length)];
    }

    // ------------------------------------------------------------------ un lugar por noche

    /** Cada ~2 s por jugador (Overworld). */
    public static void tick(MinecraftServer server, ServerWorld w, ServerPlayerEntity p, EchoState.PlayerData d,
                            EchoState st, int day) {
        if (!w.getRegistryKey().equals(World.OVERWORLD)) return;
        if (st.visited >= NOTE_COUNT && st.revealed >= NOTE_COUNT) return;

        // ¿Hay una estructura mostrada y aún sin visitar?
        if (st.revealed > st.visited) {
            if (st.visited >= st.sites.size()) return;
            BlockPos c = st.sites.get(st.visited);
            double dx = c.getX() + 0.5 - p.getX(), dz = c.getZ() + 0.5 - p.getZ();
            if (dx * dx + dz * dz > 100.0 || Math.abs(p.getY() - c.getY()) > 16) return;
            int idx = st.visited;
            st.visited++;
            st.markDirty();
            boolean tomorrow = idx + 1 < NOTE_COUNT && day < st.visited;
            Sched.at(server, 30 + RNG.nextInt(30), () -> ChatHaunt.arrival(p, idx, tomorrow));
            return;
        }
        if (st.visited >= NOTE_COUNT || day < st.visited) return; // aún no es la noche de la siguiente

        int idx = st.visited;
        BlockPos center = idx < st.sites.size() ? st.sites.get(idx) : placeNext(w, st, idx);
        if (center == null) return; // sin terreno bueno esta vez: se reintenta en unos segundos
        st.revealed = idx + 1;
        st.markDirty();
        Sched.at(server, 40 + RNG.nextInt(40), () -> ChatHaunt.reveal(p, idx, center.getX(), center.getY(), center.getZ()));
    }

    public static BlockPos placeNext(ServerWorld w, EchoState st, int idx) {
        EchoConfig cfg = EchoConfig.INSTANCE;
        Kind k = kindFor(idx);
        BlockPos origin = idx == 0 || idx - 1 >= st.sites.size() ? w.getSpawnPos() : st.sites.get(idx - 1);
        for (int i = 0; i < 6; i++) {
            double ang;
            double dist;
            if (idx == 0) {
                ang = RNG.nextDouble() * Math.PI * 2;
                dist = cfg.shrineStartMin + RNG.nextDouble() * Math.max(1, cfg.shrineStartMax - cfg.shrineStartMin);
            } else {
                ang = st.siteHeading + (RNG.nextDouble() - 0.5) * (i < 3 ? 1.2 : Math.PI * 2);
                dist = cfg.shrineLinkMin + RNG.nextDouble() * Math.max(1, cfg.shrineLinkMax - cfg.shrineLinkMin);
            }
            int x = (int) (origin.getX() + Math.cos(ang) * dist) - k.w / 2;
            int z = (int) (origin.getZ() + Math.sin(ang) * dist) - k.d / 2;
            BlockPos c = tryPlace(w, x, z, idx);
            if (c != null) {
                st.sites.add(c);
                st.siteHeading = ang;
                st.markDirty();
                return c;
            }
        }
        return null;
    }

    /** Intenta construir la estructura idx con su esquina mínima en (x0, z0). Devuelve el centro o null. */
    public static BlockPos tryPlace(ServerWorld w, int x0, int z0, int idx) {
        Kind k = kindFor(idx);
        int[] gx = {0, k.w / 2, k.w - 1};
        int[] gz = {0, k.d / 2, k.d - 1};
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int ix : gx) {
            for (int iz : gz) {
                int x = x0 + ix, z = z0 + iz;
                w.getChunk(x >> 4, z >> 4); // carga/genera el chunk
                int h = w.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
                BlockState below = w.getBlockState(new BlockPos(x, h - 1, z));
                if (!below.getFluidState().isEmpty() || below.isIn(BlockTags.LEAVES) || below.isIn(BlockTags.LOGS)
                        || below.isOf(Blocks.ICE) || below.isOf(Blocks.PACKED_ICE) || below.isAir()) return null;
                min = Math.min(min, h);
                max = Math.max(max, h);
            }
        }
        if (max - min > 3) return null;
        EchoState st = EchoState.get(w.getServer());
        if (st.hasPlayerBlocksInChunk(x0 >> 4, z0 >> 4)) return null; // no construir encima de tu base
        build(w, x0, max, z0, idx, st.multi);
        return new BlockPos(x0 + k.w / 2, max, z0 + k.d / 2);
    }

    // ------------------------------------------------------------------ construcción

    private static final class B {
        final ServerWorld w;
        final int x0, y0, z0;
        B(ServerWorld w, int x0, int y0, int z0) { this.w = w; this.x0 = x0; this.y0 = y0; this.z0 = z0; }

        BlockPos at(int dx, int dy, int dz) { return new BlockPos(x0 + dx, y0 + dy, z0 + dz); }
        void set(int dx, int dy, int dz, BlockState s) { w.setBlockState(at(dx, dy, dz), s, FLAGS); }
        void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
            for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++) set(x, y, z, s);
        }
    }

    private static BlockState s(Block b) { return b.getDefaultState(); }

    private static void build(ServerWorld w, int x0, int y0, int z0, int idx, boolean multi) {
        Kind k = kindFor(idx);
        B b = new B(w, x0, y0, z0);
        // limpiar el volumen y asentar los cimientos
        b.fill(-1, 0, -1, k.w, 9, k.d, s(Blocks.AIR));
        for (int x = -1; x <= k.w; x++) {
            for (int z = -1; z <= k.d; z++) {
                for (int dy = -1; dy >= -6; dy--) {
                    BlockPos p = b.at(x, dy, z);
                    BlockState cur = w.getBlockState(p);
                    if (cur.isSolidBlock(w, p) && !cur.isIn(BlockTags.LEAVES) && !cur.isIn(BlockTags.LOGS)) break;
                    b.set(x, dy, z, s(dy == -1 ? Blocks.DIRT : Blocks.COBBLESTONE));
                }
                b.set(x, -1, z, s(RNG.nextInt(4) == 0 ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK));
            }
        }
        switch (k) {
            case CABIN -> buildHouse(b, k, s(Blocks.SPRUCE_PLANKS), s(Blocks.SPRUCE_LOG), 4, true, 0.0);
            case RUIN -> buildHouse(b, k, s(Blocks.MOSSY_COBBLESTONE), s(Blocks.COBBLESTONE), 4, false, 0.35);
            case CHAPEL -> buildHouse(b, k, s(Blocks.STONE_BRICKS), s(Blocks.DEEPSLATE_BRICKS), 6, true, 0.08);
            case CAMP -> buildCamp(b, k);
            case GRAVE -> buildGrave(b, k);
        }
        furnish(b, k, idx, multi);
    }

    /** Casa de planta rectangular: suelo, muros con esquinas, puerta al sur (z = 0), ventanas y techo opcional. */
    private static void buildHouse(B b, Kind k, BlockState wall, BlockState corner, int h, boolean roof, double ruin) {
        int w = k.w, d = k.d;
        b.fill(0, -1, 0, w - 1, -1, d - 1, s(Blocks.SPRUCE_PLANKS));
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < d; z++) {
                    boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
                    if (!edge) continue;
                    boolean isCorner = (x == 0 || x == w - 1) && (z == 0 || z == d - 1);
                    if (RNG.nextDouble() < ruin * (0.4 + y * 0.3)) continue; // ruina: faltan bloques, más arriba
                    b.set(x, y, z, isCorner ? corner : wall);
                }
            }
        }
        // ventanas
        for (int z = 2; z < d - 2; z += 3) {
            if (RNG.nextDouble() < ruin) continue;
            b.set(0, 1, z, s(Blocks.GLASS_PANE));
            b.set(w - 1, 1, z, s(Blocks.GLASS_PANE));
        }
        // puerta (hueco; en las ruinas queda abierta)
        int dx = w / 2;
        b.set(dx, 0, 0, s(Blocks.AIR));
        b.set(dx, 1, 0, s(Blocks.AIR));
        if (ruin == 0.0) {
            BlockState door = s(Blocks.SPRUCE_DOOR).with(DoorBlock.FACING, Direction.NORTH);
            b.set(dx, 0, 0, door.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
            b.set(dx, 1, 0, door.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        }
        if (roof) {
            b.fill(-1, h, -1, w, h, d, s(Blocks.SPRUCE_SLAB));
            b.fill(0, h, 0, w - 1, h, d - 1, s(Blocks.SPRUCE_PLANKS));
            if (ruin > 0) { // el techo de la capilla está medio caído
                for (int i = 0; i < w * d / 4; i++) b.set(RNG.nextInt(w), h, RNG.nextInt(d), s(Blocks.AIR));
            }
        }
        // antorchas fuera (se apagan con la paranoia: ¡lleva un mechero!)
        for (int sx : new int[]{dx - 2, dx + 2}) {
            b.set(sx, 0, -1, s(Blocks.COBBLESTONE_WALL));
            b.set(sx, 1, -1, s(Blocks.TORCH));
        }
        b.set(1, 2, d / 2, s(Blocks.WALL_TORCH).with(WallTorchBlock.FACING, Direction.EAST));
        b.set(w - 2, 2, d / 2, s(Blocks.WALL_TORCH).with(WallTorchBlock.FACING, Direction.WEST));
        // telarañas
        if (ruin > 0) for (int i = 0; i < 6; i++) b.set(1 + RNG.nextInt(w - 2), RNG.nextInt(3), 1 + RNG.nextInt(d - 2), s(Blocks.COBWEB));
    }

    private static void buildCamp(B b, Kind k) {
        int cx = k.w / 2, cz = k.d / 2;
        // fogata apagada y troncos alrededor
        b.set(cx, 0, cz, s(Blocks.CAMPFIRE).with(CampfireBlock.LIT, false));
        b.set(cx - 2, 0, cz, s(Blocks.OAK_LOG).with(PillarBlock.AXIS, Direction.Axis.Z));
        b.set(cx + 2, 0, cz, s(Blocks.OAK_LOG).with(PillarBlock.AXIS, Direction.Axis.Z));
        b.set(cx, 0, cz - 2, s(Blocks.OAK_LOG).with(PillarBlock.AXIS, Direction.Axis.X));
        // una tienda de lana rota (techo a dos aguas)
        for (int z = 1; z <= 3; z++) {
            b.set(1, 0, z, s(Blocks.GRAY_WOOL));
            b.set(3, 0, z, s(Blocks.GRAY_WOOL));
            b.set(1, 1, z, s(Blocks.GRAY_WOOL));
            b.set(3, 1, z, s(Blocks.GRAY_WOOL));
            b.set(2, 2, z, s(Blocks.GRAY_WOOL));
            b.set(2, 1, z, RNG.nextInt(3) == 0 ? s(Blocks.AIR) : s(Blocks.GRAY_WOOL));
        }
        b.set(2, 0, 2, s(Blocks.AIR));
        // vallas con antorchas
        for (int[] c : new int[][]{{0, 0}, {k.w - 1, 0}, {0, k.d - 1}, {k.w - 1, k.d - 1}}) {
            b.set(c[0], 0, c[1], s(Blocks.OAK_FENCE));
            b.set(c[0], 1, c[1], s(Blocks.TORCH));
        }
    }

    private static void buildGrave(B b, Kind k) {
        int w = k.w, d = k.d;
        b.fill(0, -1, 0, w - 1, -1, d - 1, s(Blocks.COARSE_DIRT));
        // vallas de hierro alrededor
        for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) {
            if ((x == 0 || z == 0 || x == w - 1 || z == d - 1) && !(x == w / 2 && z == 0)) {
                if (RNG.nextInt(6) != 0) b.set(x, 0, z, s(Blocks.IRON_BARS));
            }
        }
        // tumbas: montículo de tierra + lápida
        for (int i = 0; i < 4; i++) {
            int gx = 1 + (i % 2) * 4 + RNG.nextInt(2), gz = 3 + (i / 2) * 4;
            b.set(gx, 0, gz, s(Blocks.COARSE_DIRT));
            b.set(gx, 0, gz + 1, s(Blocks.COARSE_DIRT));
            b.set(gx, 1, gz - 1, s(Blocks.STONE_BRICK_WALL));
            b.set(gx, 0, gz - 1, s(Blocks.STONE_BRICK_WALL));
        }
        for (int[] c : new int[][]{{1, 1}, {w - 2, 1}}) {
            b.set(c[0], 0, c[1], s(Blocks.DEEPSLATE_BRICK_WALL));
            b.set(c[0], 1, c[1], s(Blocks.SOUL_LANTERN));
        }
        for (int i = 0; i < 5; i++) b.set(1 + RNG.nextInt(w - 2), 0, 1 + RNG.nextInt(d - 2), s(Blocks.COBWEB));
    }

    /** Espejo, cofre con reliquias, atril con el diario y (en la cabaña) una cama. */
    private static void furnish(B b, Kind k, int idx, boolean multi) {
        int w = k.w, d = k.d;
        // Espejo contra el fondo (z = d-1): el panel queda pegado al muro y mira hacia el norte
        int mx = w - 2, mz = d - 2;
        if (k == Kind.CAMP || k == Kind.GRAVE || k == Kind.RUIN) b.fill(mx, 0, d - 1, mx, 2, d - 1, s(Blocks.STONE_BRICKS));
        BlockState mirror = ModBlocks.ECHO_MIRROR.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH);
        b.set(mx, 0, mz, mirror);
        b.set(mx, 1, mz, mirror);
        EchoManager.registerMirror(b.w, b.at(mx, 0, mz));

        // Atril con el diario
        int lx = w / 2, lz = d - 3;
        BlockPos lp = b.at(lx, 0, lz);
        b.set(lx, 0, lz, s(Blocks.LECTERN).with(LecternBlock.FACING, Direction.NORTH).with(LecternBlock.HAS_BOOK, true));
        BlockEntity be = b.w.getBlockEntity(lp);
        if (be instanceof LecternBlockEntity lec) lec.setBook(book(idx));

        // Cofre con cosas de Elías
        BlockPos cp = b.at(1, 0, d - 2);
        b.set(1, 0, d - 2, s(Blocks.CHEST).with(ChestBlock.FACING, Direction.EAST));
        if (b.w.getBlockEntity(cp) instanceof ChestBlockEntity chest) fillChest(chest, idx, multi);

        // La cama de la cabaña (por si necesitas dormir)
        if (k == Kind.CABIN) {
            b.set(w - 2, 0, 2, s(Blocks.RED_BED).with(BedBlock.FACING, Direction.SOUTH).with(BedBlock.PART, BedPart.FOOT));
            b.set(w - 2, 0, 3, s(Blocks.RED_BED).with(BedBlock.FACING, Direction.SOUTH).with(BedBlock.PART, BedPart.HEAD));
        }
        if (k == Kind.CAMP) {
            b.set(3, 0, 2, s(Blocks.RED_BED).with(BedBlock.FACING, Direction.SOUTH).with(BedBlock.PART, BedPart.FOOT));
            b.set(3, 0, 3, s(Blocks.RED_BED).with(BedBlock.FACING, Direction.SOUTH).with(BedBlock.PART, BedPart.HEAD));
        }
    }

    private static void fillChest(ChestBlockEntity chest, int idx, boolean multi) {
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(ModItems.ECHO_MIRROR, 2 + idx / 2 + (multi ? 2 : 0)));
        loot.add(new ItemStack(Items.TORCH, 8 + RNG.nextInt(8)));
        loot.add(new ItemStack(Items.COBWEB, 3 + RNG.nextInt(4)));
        loot.add(new ItemStack(Items.BREAD, 2 + RNG.nextInt(3)));
        if (idx == 0 || idx == 3) loot.add(new ItemStack(ModItems.LIGHTER)); // el mechero de Elías
        ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
        pick.setDamage(120 + RNG.nextInt(60));
        loot.add(pick);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamage(100 + RNG.nextInt(80));
        loot.add(sword);
        loot.add(new ItemStack(Items.LEATHER_CHESTPLATE));
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < 27; i++) slots.add(i);
        java.util.Collections.shuffle(slots, RNG);
        for (int i = 0; i < loot.size(); i++) chest.setStack(slots.get(i), loot.get(i));
        chest.markDirty();
    }

    private static ItemStack book(int idx) {
        String[] pages = NOTES[Math.floorMod(idx, NOTES.length)];
        List<RawFilteredPair<Text>> list = new ArrayList<>();
        for (String p : pages) list.add(RawFilteredPair.of(Text.literal(p)));
        ItemStack st = new ItemStack(Items.WRITTEN_BOOK);
        st.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, new WrittenBookContentComponent(
                RawFilteredPair.of(NOTE_PREFIX + " " + (idx + 1)), "Elías", 0, list, true));
        return st;
    }
}
