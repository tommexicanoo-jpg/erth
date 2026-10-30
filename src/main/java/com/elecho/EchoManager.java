package com.elecho;

import com.elecho.block.ExtinguishedTorchBlock;
import com.elecho.block.ExtinguishedWallTorchBlock;
import com.elecho.entity.EchoEntity;
import com.elecho.entity.ObserverEntity;
import com.elecho.net.DreadPayload;
import com.elecho.net.TimerPayload;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.block.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.screen.LecternScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

import java.nio.file.Path;
import java.util.*;

/** El director de EL ECHO: la paranoia manda sobre Ecos, sonidos y antorchas; dormir es necesario... y peligroso. */
public final class EchoManager {

    public static final RegistryKey<World> REALM = RegistryKey.of(RegistryKeys.WORLD, ElEcho.id("mirror_realm"));
    public static final String HORDE_TAG = "elecho_horde";
    private static final EntityType<?>[] HORDE = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.ZOMBIE};
    private static final Random RNG = new Random();

    private static final Set<UUID> APOC_SEEN = new HashSet<>();
    private static final Map<UUID, Integer> RANDOM_UNTIL = new HashMap<>();
    private static final Map<UUID, Integer> ECHO_LAST_SPAWN = new HashMap<>();
    private static final Map<UUID, Integer> LAST_HURT = new HashMap<>();
    private static final Map<UUID, List<EchoEntity>> ACTIVE = new HashMap<>();
    private static final Map<UUID, EchoEntity> REGISTRAR = new HashMap<>();
    private static final Map<UUID, Integer> NEXT_ECHO = new HashMap<>();
    private static final Map<UUID, Integer> NEXT_REGISTRAR = new HashMap<>();
    private static final Map<UUID, Boolean> ACTIVE_FLAG = new HashMap<>();
    private static final Map<UUID, ArrayDeque<Rec>> RECORDINGS = new HashMap<>();
    private static final Map<UUID, Integer> REALM_TICKS = new HashMap<>();
    private static final Map<UUID, BlockPos> PENDING_HIJACK = new HashMap<>();
    private static final Map<UUID, List<BlockPos>> TRAPS = new HashMap<>();
    private static final Map<UUID, List<BlockPos>> REALM_CELLS = new HashMap<>();
    private static final Map<UUID, Integer> LAST_FOOD = new HashMap<>();
    private static final Map<UUID, Long> LAST_GAZE = new HashMap<>();
    private static final Map<UUID, int[]> LAST_HUD = new HashMap<>();
    private static final Set<UUID> READING_FINAL = new HashSet<>();

    private record Rec(long time, SoundEvent sound) {}
    private enum Kind { STARE, GLIMPSE, WALK, RUSH, SCREAM, FLY_WATCH, FLY_SWOOP, LURKER }

    private EchoManager() {}

    // ------------------------------------------------------------------ registro de eventos

    public static void register() {
        TerrainMemory.register();
        ChatHaunt.register();
        ServerTickEvents.END_SERVER_TICK.register(EchoManager::tick);

        // Noche eterna: respaldo por código por si otro mod pisa el dimension_type
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.getRegistryKey().equals(World.OVERWORLD)) {
                world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
                world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
                if (world.getTimeOfDay() % 24000L != 18000L) world.setTimeOfDay(18000L);
            }
        });

        PlayerBlockBreakEvents.AFTER.register(EchoManager::onBlockBroken);
        // En el laberinto no se puede romper nada (salvo en creativo)
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) ->
                !world.getRegistryKey().equals(REALM) || player.isCreative());
        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) ->
                world.getRegistryKey().equals(REALM) && !player.isCreative() ? ActionResult.FAIL : ActionResult.PASS);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity p) {
                record(p, SoundEvents.ENTITY_PLAYER_HURT);
                if (amount > 0.0f) shock(p, EchoConfig.INSTANCE.damageFear);
            }
            return true;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            // Matar a un mob no hostil asusta
            if (entity instanceof MobEntity && !(entity instanceof HostileEntity) && !(entity instanceof EchoEntity)
                    && source.getAttacker() instanceof ServerPlayerEntity killer) {
                shock(killer, EchoConfig.INSTANCE.killFear);
            }
        });
        EntitySleepEvents.STOP_SLEEPING.register(EchoManager::onWake);
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity sp && hand == Hand.MAIN_HAND) {
                BlockState bs = world.getBlockState(hit.getBlockPos());
                if (bs.isOf(Blocks.CHEST) || bs.isOf(Blocks.TRAPPED_CHEST) || bs.isOf(Blocks.ENDER_CHEST)) {
                    record(sp, SoundEvents.BLOCK_CHEST_OPEN);
                } else if (bs.isIn(BlockTags.DOORS)) {
                    record(sp, SoundEvents.BLOCK_WOODEN_DOOR_OPEN);
                }
            }
            return ActionResult.PASS;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(server, handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid();
            dismissAll(id);
            ACTIVE_FLAG.remove(id);
            REALM_TICKS.remove(id);
            PENDING_HIJACK.remove(id);
            TRAPS.remove(id);
            REALM_CELLS.remove(id);
            NEXT_ECHO.remove(id);
            NEXT_REGISTRAR.remove(id);
            LAST_FOOD.remove(id);
            LAST_GAZE.remove(id);
            LAST_HUD.remove(id);
            READING_FINAL.remove(id);
            ChatHaunt.forget(id);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ACTIVE.clear(); REGISTRAR.clear(); ACTIVE_FLAG.clear(); RECORDINGS.clear();
            REALM_TICKS.clear(); PENDING_HIJACK.clear(); TRAPS.clear(); REALM_CELLS.clear();
            NEXT_ECHO.clear(); NEXT_REGISTRAR.clear(); READING_FINAL.clear(); LAST_FOOD.clear();
            LAST_GAZE.clear(); LAST_HUD.clear(); APOC_SEEN.clear();
            ChatHaunt.clearAll();
            EchoState.clearCache();
            Sched.clear();
        });
    }

    // ------------------------------------------------------------------ noches

    /** Noches (de dayLengthTicks) transcurridas desde que el mod vio este mundo por primera vez. */
    public static int globalDay(MinecraftServer server, EchoState st) {
        if (st.startTick < 0) return 0;
        long len = Math.max(200, EchoConfig.INSTANCE.dayLengthTicks);
        return (int) Math.max(0, (server.getOverworld().getTime() - st.startTick) / len);
    }

    /** La noche del Eco (0 = Noche 1). Las ofrendas la reducen, pero nunca por debajo de la historia ya contada. */
    public static int effectiveDay(MinecraftServer server, EchoState st) {
        int g = globalDay(server, st);
        int floor = Math.min(g, Math.max(0, st.revealed - 1));
        return Math.max(floor, Math.max(0, g - st.relief));
    }

    public static void markDirty(MinecraftServer server) {
        EchoState.get(server).markDirty();
    }

    /** 0..100: lo que manda sobre Ecos, sonidos y antorchas. Casi siempre es la paranoia. */
    public static double intensity(EchoState.PlayerData d) {
        return Math.max(d.paranoia, d.dread * 0.8);
    }

    public static double paranoiaOf(PlayerEntity p) {
        MinecraftServer s = p.getServer();
        if (s == null) return 0;
        return EchoState.get(s).data(p.getUuid()).paranoia;
    }

    public static boolean isApocalypse(ServerWorld w) {
        return EchoConfig.INSTANCE.corruptionEnding && EchoState.get(w.getServer()).apocTriggered;
    }

    // ------------------------------------------------------------------ tick principal

    private static void tick(MinecraftServer server) {
        Sched.run(server);
        int t = server.getTicks();
        EchoState state = EchoState.get(server);
        if (state.startTick < 0) {
            state.startTick = server.getOverworld().getTime();
            state.markDirty();
        }
        for (ServerPlayerEntity p : new ArrayList<>(server.getPlayerManager().getPlayerList())) {
            BlockPos hij = PENDING_HIJACK.remove(p.getUuid());
            try {
                if (hij != null) hijack(server, state, p, hij);
                tickPlayer(server, state, p, t);
            } catch (RuntimeException ex) {
                ElEcho.LOGGER.error("Error en el tick del Eco para " + p.getName().getString(), ex);
            }
        }
        if (t % 3600 == 3599) {
            try { echoWatchdog(server, state, t); } catch (RuntimeException ex) { ElEcho.LOGGER.error("Vigilante de Ecos", ex); }
        }
    }

    /** Cada 3 min: si hay paranoia suficiente y no ha aparecido ningún Eco, fuerza uno. */
    private static void echoWatchdog(MinecraftServer server, EchoState st, int now) {
        EchoConfig cfg = EchoConfig.INSTANCE;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            UUID id = p.getUuid();
            Integer last = ECHO_LAST_SPAWN.get(id);
            if (last == null) { ECHO_LAST_SPAWN.put(id, now); continue; }
            if (now - last < 3600) continue;
            EchoState.PlayerData d = st.data(id);
            ServerWorld w = p.getServerWorld();
            boolean should = !p.isSpectator() && !d.doomed && !d.inRealm && w.getRegistryKey().equals(World.OVERWORLD)
                    && ACTIVE_FLAG.getOrDefault(id, false) && intensity(d) >= cfg.echoParanoiaThreshold;
            if (!should) { ECHO_LAST_SPAWN.put(id, now); continue; }
            NEXT_ECHO.remove(id);
            RANDOM_UNTIL.remove(id);
            dismissAll(id);
            int day = effectiveDay(server, st);
            if (!spawnKind(w, p, d, day, Kind.STARE)) spawnKind(w, p, d, day, Kind.GLIMPSE);
            ECHO_LAST_SPAWN.put(id, now);
        }
    }

    /** +miedo y +paranoia de golpe (con un pequeño enfriamiento). */
    private static void shock(ServerPlayerEntity p, double amount) {
        if (p.isSpectator() || p.getServer() == null) return;
        int now = p.getServer().getTicks();
        Integer last = LAST_HURT.get(p.getUuid());
        if (last != null && now - last < 25) return;
        LAST_HURT.put(p.getUuid(), now);
        EchoState st = EchoState.get(p.getServer());
        EchoState.PlayerData d = st.data(p.getUuid());
        if (d.doomed) return;
        d.dread = Math.min(120, d.dread + amount);
        d.paranoia = Math.min(100, d.paranoia + amount);
        st.markDirty();
    }

    private static void tickPlayer(MinecraftServer server, EchoState state, ServerPlayerEntity p, int t) {
        EchoState.PlayerData d = state.data(p.getUuid());
        ServerWorld w = p.getServerWorld();
        ServerWorld ow = server.getOverworld();
        long gameTime = ow.getTime();
        int day = effectiveDay(server, state);
        boolean inRealm = w.getRegistryKey().equals(REALM);
        int phase = (t + p.getId()) % 20;

        // Salió del laberinto por otra vía (muerte, comando...)
        if (d.inRealm && !inRealm) {
            d.inRealm = false;
            d.dread = Math.min(d.dread, 30);
            d.banishedUntil = gameTime + EchoConfig.INSTANCE.dayLengthTicks;
            REALM_TICKS.remove(p.getUuid());
            state.markDirty();
        }
        if (inRealm) {
            if (d.inRealm) tickRealm(server, state, p, w, d, day);
            return;
        }
        if (p.isSpectator()) return;

        // En la noche eterna dormir dura 5 s y te despierta tú mismo (la noche no se salta)
        if (p.isSleeping() && p.getSleepTimer() >= 100) p.wakeUp(false, true);

        if (state.visited >= Shrines.NOTE_COUNT && !state.apocTriggered) tickFinalBook(state, p);

        // Foto del inventario (para las ofrendas): lo de hoy no vale todavía
        if (t % 100 == 0) {
            int gd = globalDay(server, state);
            if (d.todayDay != gd) {
                d.day1Items.addAll(d.todayItems);
                d.todayItems.clear();
                d.todayDay = gd;
            }
            for (int i = 0; i < p.getInventory().size(); i++) {
                ItemStack s = p.getInventory().getStack(i);
                if (!s.isEmpty()) d.todayItems.add(Registries.ITEM.getId(s.getItem()).toString());
            }
            state.markDirty();
        }

        if (phase == 0) secondTick(server, state, p, w, d, day, t);
    }

    private static void tickFinalBook(EchoState st, ServerPlayerEntity p) {
        if (st.sites.size() < Shrines.NOTE_COUNT) return;
        BlockPos last = st.sites.get(Shrines.NOTE_COUNT - 1);
        double dx = last.getX() + 0.5 - p.getX(), dz = last.getZ() + 0.5 - p.getZ();
        boolean near = dx * dx + dz * dz < 14.0 * 14.0;
        boolean reading = p.currentScreenHandler instanceof LecternScreenHandler;
        if (reading && near) {
            READING_FINAL.add(p.getUuid());
        } else if (!reading && READING_FINAL.remove(p.getUuid())) {
            triggerApocalypse(p);
        }
    }

    /** Empieza la Noche 5 de verdad (también para /echo apocalipsis). */
    public static void triggerApocalypse(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        EchoState st = EchoState.get(server);
        if (st.apocTriggered) return;
        st.apocTriggered = true;
        st.markDirty();
        for (ServerPlayerEntity o : server.getPlayerManager().getPlayerList()) {
            scare(o, 2);
            Paranoia.playTo(o, SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, o.getPos(), 1.0f, 0.5f);
        }
    }

    /** 0 normal, 1 tensión, 2 corrupción, 3 apocalipsis. */
    private static int stageOf(MinecraftServer server, EchoState.PlayerData d, int day) {
        EchoConfig cfg = EchoConfig.INSTANCE;
        if (EchoState.get(server).apocTriggered && cfg.corruptionEnding) return 3;
        if (day >= cfg.corruptionDay) return 2;
        return (day >= 2 || d.paranoia > 45) ? 1 : 0;
    }

    private static void secondTick(MinecraftServer server, EchoState state, ServerPlayerEntity p, ServerWorld w,
                                   EchoState.PlayerData d, int day, int t) {
        EchoConfig cfg = EchoConfig.INSTANCE;
        long sec = t / 20;
        UUID id = p.getUuid();
        boolean overworld = w.getRegistryKey().equals(World.OVERWORLD);
        int stage = stageOf(server, d, day);

        checkDayTitle(state, p, d, day);

        boolean apoc = stage >= 3;
        boolean active = overworld && (apoc || isActive(server, w, p.getBlockPos(), d));
        ACTIVE_FLAG.put(id, active);
        boolean prot = overworld && isProtected(w, p.getBlockPos());

        if (!overworld) {
            sendHud(p, 0, 0, 0, 0, 0, 0, sec);
            dismissAll(id);
            return;
        }

        if (sec % 2 == 0) Shrines.tick(server, w, p, d, state, day);

        // --- miedo, paranoia y sueño ---
        double ins = Paranoia.insomnia(p);
        Paranoia.sleepTick(server, p, d, day, sec);
        if (active) {
            d.dread = Math.min(120, d.dread + cfg.dreadPerMinute / 60.0 * (1.0 + d.dread / 60.0 + day * 0.15));
            if (sec % 20 == 0) d.dread = Math.min(120, d.dread + 1.0);
            if (w.getLightLevel(p.getBlockPos()) < 5) d.dread = Math.min(120, d.dread + 0.12);
            double sleepPush = d.sleepStage >= 2 ? 0.10 : 0.0; // sin dormir, la paranoia sube más rápido
            d.paranoia = Math.min(100, d.paranoia + 0.05 + d.dread * 0.0012 + ins * 0.12 + sleepPush + (d.dread > 60 ? 0.03 : 0.0));
        } else if (prot) {
            d.dread = Math.max(0, d.dread - 0.2);
            d.paranoia = Math.max(0, d.paranoia - 0.15);
        }
        tickCalm(p, d);
        state.markDirty();
        sendHud(p, 1, (int) Math.min(100, d.dread), (int) Math.min(100, d.paranoia), day + 1, stage, d.sleepStage, sec);

        if (!active) {
            dismissAll(id);
            return;
        }

        if (stage >= 3 && !d.doomed) tickApocalypse(server, state, p, w, d, sec);

        // --- apariciones (todas dependen de la PARANOIA) ---
        director(server, w, p, d, day, stage);
        registrarDirector(server, w, p, d, day, stage);
        if (stage < 3 && RNG.nextDouble() < (1 + day) * cfg.eventIntensity / 1200.0) randomEcho(server, w, p, d, day);
        if (d.paranoia > 55 && RNG.nextInt(100) == 0) scare(p, 1);
        observerDirector(w, p, d, stage);
        eyes(w, p, d);

        // --- chat, sonidos, falsos eventos ---
        ChatHaunt.tick(server, p, d, day);
        Paranoia.tick(server, p, w, d, day);
        double in = intensity(d);
        if (in >= cfg.echoParanoiaThreshold && RNG.nextDouble() < Math.min(0.08, 0.008 + in * 0.0006)) glimpse(p, w, d, day);
        if (in > 70 && RNG.nextDouble() < 0.02) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 100, 0, false, false, false));
        }

        // --- antorchas (se apagan más rápido con la paranoia) ---
        torchTick(w, p, d);

        // --- terreno ---
        if (sec % 5 == 0) TerrainMemory.tick(server, w, p, day);
    }

    private static void checkDayTitle(EchoState st, ServerPlayerEntity p, EchoState.PlayerData d, int day) {
        int shown = day + 1;
        if (d.lastDayShown == shown) return;
        boolean up = shown > d.lastDayShown;
        d.lastDayShown = shown;
        st.markDirty();
        if (up && EchoConfig.INSTANCE.dayTitle && !d.doomed) showTitle(p, shown);
    }

    public static void showTitle(ServerPlayerEntity p, int night) {
        p.networkHandler.sendPacket(new TitleFadeS2CPacket(20, 90, 40));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable("elecho.night.sub." + Math.min(5, Math.max(1, night)))));
        p.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable("elecho.night.title", night)));
        Paranoia.playTo(p, SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.AMBIENT, p.getPos(), 1.0f, 0.6f);
    }

    private static void tickCalm(ServerPlayerEntity p, EchoState.PlayerData d) {
        int food = p.getHungerManager().getFoodLevel();
        Integer last = LAST_FOOD.put(p.getUuid(), food);
        if (last != null && food > last) calm(d, EchoConfig.INSTANCE.eatDreadRelief, EchoConfig.INSTANCE.eatParanoiaRelief);
    }

    private static void calm(EchoState.PlayerData d, double dread, double paranoia) {
        d.dread = Math.max(0, d.dread - dread);
        d.paranoia = Math.max(0, d.paranoia - paranoia);
    }

    private static boolean isActive(MinecraftServer server, ServerWorld w, BlockPos pos, EchoState.PlayerData d) {
        if (!w.getRegistryKey().equals(World.OVERWORLD)) return false;
        return server.getOverworld().getTime() >= d.banishedUntil && !isProtected(w, pos);
    }

    // ------------------------------------------------------------------ HUD / sustos

    private static void send(ServerPlayerEntity p, DreadPayload payload) {
        if (ServerPlayNetworking.canSend(p, DreadPayload.ID)) ServerPlayNetworking.send(p, payload);
    }

    private static void sendHud(ServerPlayerEntity p, int hud, int dread, int paranoia, int day, int stage, int sleep, long sec) {
        int[] cur = {hud, dread, paranoia, day, stage, sleep};
        int[] prev = LAST_HUD.get(p.getUuid());
        if (prev != null && Arrays.equals(prev, cur) && sec % 10 != 0) return;
        LAST_HUD.put(p.getUuid(), cur);
        send(p, new DreadPayload(hud, dread, paranoia, day, stage, sleep * 10));
    }

    public static void scare(ServerPlayerEntity p, int level) {
        send(p, DreadPayload.scare(level));
    }

    public static void addDread(ServerPlayerEntity p, double amount) {
        EchoState st = EchoState.get(p.getServer());
        EchoState.PlayerData d = st.data(p.getUuid());
        d.dread = Math.min(120, d.dread + amount);
        st.markDirty();
    }

    // ------------------------------------------------------------------ antorchas, Observador, ojos

    /**
     * Antorchas colocadas cerca y la de la mano: se apagan (o parpadean) más rápido cuanta más paranoia tienes.
     * Vida media = torchBaseSeconds * torchMinFactor^(paranoia/100): ~240 s con 0, ~50 s con 50, ~10 s con 100.
     */
    private static void torchTick(ServerWorld w, ServerPlayerEntity p, EchoState.PlayerData d) {
        EchoConfig cfg = EchoConfig.INSTANCE;
        double life = Math.max(5.0, cfg.torchBaseSeconds * Math.pow(cfg.torchMinFactor, Math.min(100.0, d.paranoia) / 100.0));
        double pOut = 1.0 / life;
        double pFlicker = pOut * 2.5;
        int r = cfg.torchScanRadius;

        BlockPos.Mutable m = new BlockPos.Mutable();
        BlockPos c = p.getBlockPos();
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) for (int dy = -5; dy <= 6; dy++) {
            m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
            BlockState s = w.getBlockState(m);
            boolean standing = s.isOf(Blocks.TORCH);
            boolean wall = s.isOf(Blocks.WALL_TORCH);
            if (!standing && !wall) continue;
            double roll = RNG.nextDouble();
            if (roll >= pOut + pFlicker) continue;
            boolean flicker = roll < pFlicker;
            BlockPos pos = m.toImmutable();
            Block block = standing ? ModBlocks.EXTINGUISHED_TORCH : ModBlocks.EXTINGUISHED_WALL_TORCH;
            BlockState ns = block.getDefaultState().with(ExtinguishedTorchBlock.FLICKER, flicker);
            if (wall) ns = ns.with(ExtinguishedWallTorchBlock.FACING, s.get(WallTorchBlock.FACING));
            w.setBlockState(pos, ns);
            if (flicker) w.scheduleBlockTick(pos, block, 3 + RNG.nextInt(14));
            w.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.3f, 1.3f);
            w.spawnParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 4, 0.05, 0.1, 0.05, 0.01);
        }

        // antorcha en la mano
        for (Hand hand : Hand.values()) {
            ItemStack st = p.getStackInHand(hand);
            if (!st.isOf(net.minecraft.item.Items.TORCH)) continue;
            double roll = RNG.nextDouble();
            if (roll >= (pOut + pFlicker) * 1.2) continue;
            w.playSound(null, p.getX(), p.getY() + 1, p.getZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.35f, 1.4f);
            w.spawnParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1.2, p.getZ(), 6, 0.15, 0.1, 0.15, 0.01);
            if (roll < pFlicker * 1.2) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 14, 0, false, false, false));
            } else {
                ItemStack out = new ItemStack(ModItems.EXTINGUISHED_TORCH);
                if (st.getCount() == 1) p.setStackInHand(hand, out);
                else { st.decrement(1); p.getInventory().offerOrDrop(out); }
            }
            break;
        }
    }

    private static void observerDirector(ServerWorld w, ServerPlayerEntity p, EchoState.PlayerData d, int stage) {
        EchoConfig cfg = EchoConfig.INSTANCE;
        if (stage < 3 && d.paranoia < cfg.observerParanoia) return;
        if (!w.getEntitiesByType(ModEntities.OBSERVER, p.getBoundingBox().expand(140), e -> true).isEmpty()) return;
        if (stage < 3 && RNG.nextInt(8) != 0) return;
        double par = Math.max(d.paranoia, stage >= 3 ? 100 : 0);
        double dist = Math.max(30.0, 66.0 - par * 0.36);
        for (int i = 0; i < 16; i++) {
            double ang = RNG.nextDouble() * Math.PI * 2;
            double x = p.getX() + Math.cos(ang) * dist;
            double z = p.getZ() + Math.sin(ang) * dist;
            BlockPos col = BlockPos.ofFloored(x, 0, z);
            if (!w.isChunkLoaded(col)) continue;
            int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, col.getX(), col.getZ());
            BlockPos below = new BlockPos(col.getX(), y - 1, col.getZ());
            if (!w.getBlockState(below).isSolid() || !w.getFluidState(below).isEmpty()) continue;
            ObserverEntity o = ModEntities.OBSERVER.create(w);
            if (o == null) return;
            o.refreshPositionAndAngles(x, y, z, RNG.nextFloat() * 360f, 0f);
            w.spawnEntity(o);
            return;
        }
    }

    public static boolean spawnObserverNear(ServerWorld w, ServerPlayerEntity p) {
        double dist = 40.0;
        for (int i = 0; i < 16; i++) {
            double ang = RNG.nextDouble() * Math.PI * 2;
            double x = p.getX() + Math.cos(ang) * dist;
            double z = p.getZ() + Math.sin(ang) * dist;
            BlockPos col = BlockPos.ofFloored(x, 0, z);
            if (!w.isChunkLoaded(col)) continue;
            int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, col.getX(), col.getZ());
            ObserverEntity o = ModEntities.OBSERVER.create(w);
            if (o == null) return false;
            o.refreshPositionAndAngles(x, y, z, RNG.nextFloat() * 360f, 0f);
            w.spawnEntity(o);
            return true;
        }
        return false;
    }

    /** Ojos rojos en hojas, cristales y superficies oscuras: cuanta más paranoia, más ojos. */
    private static void eyes(ServerWorld w, ServerPlayerEntity p, EchoState.PlayerData d) {
        if (RNG.nextDouble() > 0.10 + d.paranoia * 0.006) return;
        int n = 1 + (int) (d.paranoia / 30);
        for (int i = 0; i < n; i++) {
            double ang = RNG.nextDouble() * Math.PI * 2;
            double r = 7 + RNG.nextDouble() * 17;
            BlockPos pos = BlockPos.ofFloored(p.getX() + Math.cos(ang) * r, p.getY() + RNG.nextInt(12) - 3, p.getZ() + Math.sin(ang) * r);
            if (!w.isChunkLoaded(pos)) continue;
            BlockState s = w.getBlockState(pos);
            boolean ok = s.isIn(BlockTags.LEAVES) || s.getBlock() instanceof PaneBlock
                    || (s.isOpaqueFullCube(w, pos) && w.getLightLevel(LightType.BLOCK, pos.up()) <= 2 && RNG.nextInt(3) == 0);
            if (!ok) continue;
            Direction face = Direction.Type.HORIZONTAL.random(w.getRandom());
            BlockPos nb = pos.offset(face);
            if (!w.getBlockState(nb).isAir() || w.getLightLevel(LightType.BLOCK, nb) > 4) continue;
            Vec3d toPlayer = p.getPos().subtract(Vec3d.ofCenter(pos));
            if (toPlayer.x * face.getOffsetX() + toPlayer.z * face.getOffsetZ() < 0) continue;
            w.spawnParticles(p, ModParticles.EYES, true, pos.getX() + 0.5 + face.getOffsetX() * 0.52, pos.getY() + 0.5,
                    pos.getZ() + 0.5 + face.getOffsetZ() * 0.52, 1, 0, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------ el apocalipsis de la Noche 5

    private static void tickApocalypse(MinecraftServer server, EchoState st, ServerPlayerEntity p, ServerWorld w,
                                       EchoState.PlayerData d, long sec) {
        EchoConfig cfg = EchoConfig.INSTANCE;
        ServerWorld ow = server.getOverworld();
        long now = ow.getTime();
        if (st.apocStart < 0) {
            st.apocStart = now;
            st.markDirty();
        }
        if (APOC_SEEN.add(p.getUuid())) {
            scare(p, 2);
            Paranoia.playTo(p, SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, p.getPos(), 1.0f, 0.5f);
            p.sendMessage(Text.literal("NOCHE 5").formatted(Formatting.DARK_RED, Formatting.BOLD), true);
            spawnWarden(w, p);
        }
        if (sec % 20 == 0) spawnWarden(w, p);
        d.dread = 100;
        d.paranoia = 100;
        int day = effectiveDay(server, st);
        if (isLeader(server, ow, p)) ChatHaunt.burst(server, p, 4, day);
        if (RNG.nextInt(10) < 7) cosmeticLightning(w, p);
        if (sec % 2 == 0) scare(p, RNG.nextInt(4) == 0 ? 2 : 1);
        if (sec % 8 == 0) p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 50, 0, false, false, false));
        if (RNG.nextInt(25) == 0) p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 12, 0, false, false, false));

        List<EchoEntity> list = activeList(p.getUuid());
        long stalkers = list.stream().filter(e -> e.getMode() == EchoEntity.Mode.STALK).count();
        long rushers = list.stream().filter(e -> e.getMode() == EchoEntity.Mode.RUSH).count();
        for (int i = 0; i < 3 && stalkers + i < 12; i++) {
            BlockPos s = findSurfaceSpot(w, p, 12, 26, -1);
            if (s != null) spawn(w, p, s, EchoEntity.Mode.STALK, RNG.nextInt(10) < 8 ? EchoEntity.V_SHADOW : EchoEntity.V_NORMAL);
        }
        for (int i = 0; i < 2 && rushers + i < 5; i++) {
            BlockPos s = findSurfaceSpot(w, p, 16, 28, -1);
            if (s != null) spawn(w, p, s, EchoEntity.Mode.RUSH, RNG.nextInt(10) < 6 ? EchoEntity.V_SHADOW : EchoEntity.V_NORMAL);
        }
        spawnHorde(w, p);
        Paranoia.playTo(p, SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, p.getPos(), 1.0f, 0.8f);

        int remaining = (int) Math.max(0, cfg.apocalypseSeconds - (now - st.apocStart) / 20L);
        if (ServerPlayNetworking.canSend(p, TimerPayload.ID)) ServerPlayNetworking.send(p, new TimerPayload(remaining));
        if (now - st.apocStart >= cfg.apocalypseSeconds * 20L) beginEnding(server, st, p, d);
    }

    private static boolean isLeader(MinecraftServer server, ServerWorld ow, ServerPlayerEntity p) {
        for (ServerPlayerEntity o : server.getPlayerManager().getPlayerList()) {
            if (o.getServerWorld() == ow && !o.isSpectator()) return o == p;
        }
        return false;
    }

    private static void cosmeticLightning(ServerWorld w, ServerPlayerEntity p) {
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(w);
        if (bolt == null) return;
        double ang = RNG.nextDouble() * Math.PI * 2;
        double dist = 10 + RNG.nextDouble() * 18;
        bolt.refreshPositionAfterTeleport(p.getX() + Math.cos(ang) * dist, p.getY(), p.getZ() + Math.sin(ang) * dist);
        bolt.setCosmetic(true);
        w.spawnEntity(bolt);
    }

    private static void spawnWarden(ServerWorld w, ServerPlayerEntity p) {
        List<WardenEntity> near = w.getEntitiesByClass(WardenEntity.class, p.getBoundingBox().expand(64),
                x -> x.isAlive() && x.getCommandTags().contains(HORDE_TAG));
        if (near.size() >= 2) return;
        BlockPos s = findSurfaceSpot(w, p, 14, 24, -1);
        if (s == null) return;
        WardenEntity wd = EntityType.WARDEN.create(w);
        if (wd == null) return;
        wd.refreshPositionAndAngles(s.getX() + 0.5, s.getY(), s.getZ() + 0.5, RNG.nextFloat() * 360f, 0f);
        wd.addCommandTag(HORDE_TAG);
        wd.setPersistent();
        w.spawnEntity(wd);
        for (int i = 0; i < 4; i++) wd.increaseAngerAt(p);
    }

    private static void spawnHorde(ServerWorld w, ServerPlayerEntity p) {
        EchoConfig cfg = EchoConfig.INSTANCE;
        List<MobEntity> near = w.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(48),
                m -> m.isAlive() && m.getCommandTags().contains(HORDE_TAG));
        int room = cfg.hordeMobCap - near.size();
        for (int i = 0; i < Math.min(4, room); i++) {
            BlockPos s = findSurfaceSpot(w, p, 9, 22, -1);
            if (s == null) continue;
            Entity ent = HORDE[RNG.nextInt(HORDE.length)].create(w);
            if (!(ent instanceof MobEntity m)) continue;
            m.refreshPositionAndAngles(s.getX() + 0.5, s.getY(), s.getZ() + 0.5, RNG.nextFloat() * 360f, 0f);
            m.initialize(w, w.getLocalDifficulty(s), SpawnReason.EVENT, null);
            m.addCommandTag(HORDE_TAG);
            m.setPersistent();
            m.setTarget(p);
            w.spawnEntity(m);
        }
    }

    private static Path worldRoot(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
    }

    /** El final: el mundo te echa y debes borrarlo tú desde la carpeta saves. (El mod no borra nada.) */
    private static void beginEnding(MinecraftServer server, EchoState st, ServerPlayerEntity p, EchoState.PlayerData d) {
        d.doomed = true;
        st.markDirty();
        dismissAll(p.getUuid());
        ThanksWatcher.arm(worldRoot(server));
        String n = p.getName().getString();
        String world = server.getSaveProperties().getLevelName();
        Sched.at(server, 0, () -> p.sendMessage(Text.literal("<" + n + "> BORRA EL MUNDO"), false));
        Sched.at(server, 30, () -> p.sendMessage(Text.literal("<" + n + "> saves/" + world), false));
        Sched.at(server, 60, () -> p.sendMessage(Text.literal("El mundo ya no te recuerda.").formatted(Formatting.DARK_RED, Formatting.ITALIC), false));
        Sched.at(server, 100, () -> kickEnding(p));
    }

    private static void kickEnding(ServerPlayerEntity p) {
        if (p.isRemoved() || p.networkHandler == null) return;
        String world = p.getServer().getSaveProperties().getLevelName();
        p.networkHandler.disconnect(Text.literal("El mundo ya no te recuerda.\n\nBórralo tú mismo:\nsaves/" + world).formatted(Formatting.GRAY));
    }

    private static void onJoin(MinecraftServer server, ServerPlayerEntity p) {
        EchoState st = EchoState.get(server);
        EchoState.PlayerData d = st.data(p.getUuid());
        LAST_HUD.remove(p.getUuid());
        if (!st.multi && st.players.size() >= 2 && globalDay(server, st) <= 1) {
            st.multi = true;
            st.markDirty();
        }
        if (st.apocTriggered && st.apocStart >= 0 && EchoConfig.INSTANCE.corruptionEnding
                && server.getOverworld().getTime() - st.apocStart >= EchoConfig.INSTANCE.apocalypseSeconds * 20L) {
            d.doomed = true;
            st.markDirty();
        }
        if (d.doomed && EchoConfig.INSTANCE.corruptionEnding) {
            ThanksWatcher.arm(worldRoot(server));
            Sched.at(server, 15, () -> p.sendMessage(Text.literal("Esto no debería seguir existiendo.").formatted(Formatting.DARK_RED, Formatting.ITALIC), false));
            Sched.at(server, 50, () -> {
                EchoState.PlayerData dd = EchoState.get(server).data(p.getUuid());
                if (dd.doomed && EchoConfig.INSTANCE.corruptionEnding) kickEnding(p);
            });
        }
    }

    public static void intro(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        showTitle(p, effectiveDay(server, EchoState.get(server)) + 1);
    }

    // ------------------------------------------------------------------ director de Ecos

    private static List<EchoEntity> activeList(UUID id) {
        List<EchoEntity> l = ACTIVE.computeIfAbsent(id, k -> new ArrayList<>());
        l.removeIf(e -> e.isRemoved() || !e.isAlive());
        return l;
    }

    public static void dismissAll(UUID id) {
        List<EchoEntity> l = ACTIVE.remove(id);
        if (l != null) for (EchoEntity e : l) if (e.isAlive()) e.vanish();
        EchoEntity r = REGISTRAR.remove(id);
        if (r != null && r.isAlive()) r.discard();
    }

    /** Cuántos Ecos puede haber a la vez: 0 por debajo del umbral, y uno más cada echoParanoiaStep de paranoia. */
    private static int echoCap(EchoState.PlayerData d, boolean apoc) {
        EchoConfig cfg = EchoConfig.INSTANCE;
        if (apoc) return 12;
        double in = intensity(d);
        if (in < cfg.echoParanoiaThreshold) return 0;
        return Math.min(cfg.maxEchoes, 1 + (int) ((in - cfg.echoParanoiaThreshold) / cfg.echoParanoiaStep));
    }

    private static void director(MinecraftServer server, ServerWorld w, ServerPlayerEntity p, EchoState.PlayerData d,
                                 int day, int stage) {
        EchoConfig cfg = EchoConfig.INSTANCE;
        UUID id = p.getUuid();
        boolean apoc = stage >= 3;
        int cap = echoCap(d, apoc);
        if (cap > 0 && !apoc) cap = Math.min(cfg.maxEchoes + 4, cap + day / 2);
        List<EchoEntity> list = activeList(id);
        if (cap == 0) {
            Integer ru = RANDOM_UNTIL.get(id);
            if (ru != null && server.getTicks() < ru) return;
            if (!list.isEmpty() && intensity(d) < cfg.echoParanoiaThreshold - 5) {
                for (EchoEntity e : list) if (e.isAlive()) e.vanish();
                list.clear();
            }
            return;
        }
        if (list.size() >= cap) return;
        int now = server.getTicks();
        Integer next = NEXT_ECHO.get(id);
        if (next != null && now < next) return;

        double in = intensity(d);
        double chance = Math.min(0.95, 0.5 + (in - cfg.echoParanoiaThreshold) * 0.01 + day * 0.03) * cfg.eventIntensity;
        if (w.isRaining()) chance *= 1.3;
        if (stage >= 2) chance *= 1.3;
        if (apoc) chance = 0.9;
        if (RNG.nextDouble() > Math.min(0.95, chance)) return;

        Kind kind = pickKind(in);
        if (spawnKind(w, p, d, day, kind)) {
            // cuanta más paranoia, menos espera entre Ecos
            double gapSeconds = apoc ? 2 : Math.max(2.0, 13.0 - in * 0.12 - day * 0.8);
            if (!apoc && in >= 40 && activeList(id).size() < cap) spawnKind(w, p, d, day, pickKind(in));
            if (!apoc && in >= 75 && activeList(id).size() < cap) spawnKind(w, p, d, day, pickKind(in));
            NEXT_ECHO.put(id, now + (int) (gapSeconds * (0.7 + RNG.nextDouble() * 0.6) * 20.0));
        }
    }

    private static void randomEcho(MinecraftServer server, ServerWorld w, ServerPlayerEntity p, EchoState.PlayerData d, int day) {
        List<Kind> pool = new ArrayList<>(List.of(Kind.STARE, Kind.GLIMPSE));
        if (day >= 1) { pool.add(Kind.WALK); pool.add(Kind.LURKER); }
        if (day >= 2) pool.add(Kind.RUSH);
        if (day >= 3) pool.add(Kind.SCREAM);
        if (day >= 4) pool.add(Kind.FLY_WATCH);
        if (spawnKind(w, p, d, day, pool.get(RNG.nextInt(pool.size())))) {
            RANDOM_UNTIL.put(p.getUuid(), server.getTicks() + 20 * 90);
        }
    }

    private static Kind pickKind(double in) {
        EchoConfig cfg = EchoConfig.INSTANCE;
        List<Kind> pool = new ArrayList<>();
        add(pool, Kind.STARE, 4);
        add(pool, Kind.GLIMPSE, 3);
        if (in >= cfg.echoParanoiaThreshold) add(pool, Kind.LURKER, 1);
        if (in >= cfg.walkParanoia) add(pool, Kind.WALK, 2);
        if (in >= cfg.rushParanoia) add(pool, Kind.RUSH, 6);
        if (in >= cfg.screamParanoia) add(pool, Kind.SCREAM, 2);
        if (in >= cfg.flyParanoia) { add(pool, Kind.FLY_WATCH, 2); add(pool, Kind.FLY_SWOOP, 2); }
        return pool.get(RNG.nextInt(pool.size()));
    }

    private static void add(List<Kind> pool, Kind k, int weight) {
        for (int i = 0; i < weight; i++) pool.add(k);
    }

    private static int pickVariant(int day, double in) {
        EchoConfig cfg = EchoConfig.INSTANCE;
        if (in < cfg.shadowParanoia) return EchoEntity.V_NORMAL;
        double shadow = Math.min(0.8, 0.25 + (in - cfg.shadowParanoia) * 0.01 + day * 0.05);
        return RNG.nextDouble() < shadow ? EchoEntity.V_SHADOW : EchoEntity.V_NORMAL;
    }

    private static boolean spawnKind(ServerWorld w, ServerPlayerEntity p, EchoState.PlayerData d, int day, Kind kind) {
        boolean cave = !w.isSkyVisible(p.getBlockPos()) && p.getY() < 55;
        int variant = pickVariant(day, intensity(d));
        BlockPos s;
        switch (kind) {
            case STARE -> {
                s = cave ? findCavePos(w, p, 14, 24) : findSurfaceSpot(w, p, 26, 40, -1);
                return s != null && spawn(w, p, s, EchoEntity.Mode.WATCH, variant) != null;
            }
            case GLIMPSE -> { return glimpse(p, w, d, day); }
            case WALK -> {
                s = cave ? findCavePos(w, p, 16, 26) : findSurfaceSpot(w, p, 26, 40, -1);
                return s != null && spawn(w, p, s, EchoEntity.Mode.WALK, variant) != null;
            }
            case LURKER -> {
                s = cave ? findCavePos(w, p, 18, 26) : findSurfaceSpot(w, p, 28, 38, 70);
                if (s == null) return false;
                EchoEntity lk = spawn(w, p, s, EchoEntity.Mode.LURKER, RNG.nextInt(10) < 6 ? EchoEntity.V_SHADOW : EchoEntity.V_NORMAL);
                if (lk != null) RANDOM_UNTIL.put(p.getUuid(), w.getServer().getTicks() + 20 * 120);
                return lk != null;
            }
            case RUSH -> {
                s = cave ? findCavePos(w, p, 14, 24) : findSurfaceSpot(w, p, 15, 26, 45);
                return s != null && spawn(w, p, s, EchoEntity.Mode.RUSH, variant) != null;
            }
            case SCREAM -> {
                s = cave ? findCavePos(w, p, 8, 14) : findSurfaceSpot(w, p, 9, 15, 60);
                return s != null && spawn(w, p, s, EchoEntity.Mode.SCREAM, RNG.nextBoolean() ? EchoEntity.V_SHADOW : variant) != null;
            }
            case FLY_WATCH, FLY_SWOOP -> {
                if (cave) return false;
                boolean swoop = kind == Kind.FLY_SWOOP;
                s = swoop ? findSurfaceSpot(w, p, 24, 34, 70) : findSurfaceSpot(w, p, 14, 24, -1);
                if (s == null) return false;
                int h = swoop ? 11 : 8;
                while (h > 3 && !columnFree(w, s, h)) h--;
                if (h <= 3) return false;
                return spawn(w, p, s.up(h), swoop ? EchoEntity.Mode.FLY_SWOOP : EchoEntity.Mode.FLY_WATCH, variant) != null;
            }
        }
        return false;
    }

    private static boolean columnFree(ServerWorld w, BlockPos base, int h) {
        for (int i = 1; i <= h + 1; i++) if (!w.getBlockState(base.up(i)).isAir()) return false;
        return true;
    }

    private static EchoEntity spawn(ServerWorld w, ServerPlayerEntity p, BlockPos pos, EchoEntity.Mode mode, int variant) {
        EchoEntity e = ModEntities.ECHO.create(w);
        if (e == null) return null;
        e.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RNG.nextFloat() * 360f, 0f);
        e.init(p, variant, mode);
        w.spawnEntity(e);
        if (!w.getRegistryKey().equals(REALM)) {
            activeList(p.getUuid()).add(e);
            ECHO_LAST_SPAWN.put(p.getUuid(), w.getServer().getTicks());
        }
        return e;
    }

    /** cone > 0: dentro de ese ángulo (grados) a cada lado de hacia donde miras. cone <= 0: cualquier dirección. */
    private static BlockPos findSurfaceSpot(ServerWorld w, ServerPlayerEntity p, int min, int max, double cone) {
        Vec3d look = p.getRotationVec(1.0f);
        double base = Math.atan2(look.z, look.x);
        for (int i = 0; i < 14; i++) {
            double ang = cone > 0 ? base + Math.toRadians((RNG.nextDouble() * 2 - 1) * cone) : RNG.nextDouble() * Math.PI * 2;
            double dist = min + RNG.nextDouble() * (max - min);
            BlockPos pos = surfaceAt(w, p, (int) Math.floor(p.getX() + Math.cos(ang) * dist),
                    (int) Math.floor(p.getZ() + Math.sin(ang) * dist), 12);
            if (pos != null) return pos;
        }
        return null;
    }

    private static BlockPos surfaceAt(ServerWorld w, ServerPlayerEntity p, int x, int z, int maxDy) {
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return null;
        int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (Math.abs(y - p.getY()) > maxDy) return null;
        BlockPos pos = new BlockPos(x, y, z);
        if (!w.getBlockState(pos.down()).isSolidBlock(w, pos.down())) return null;
        if (!w.getBlockState(pos).isAir() || !w.getBlockState(pos.up()).isAir()) return null;
        return pos;
    }

    private static BlockPos findCavePos(ServerWorld w, ServerPlayerEntity p, int min, int max) {
        for (int attempt = 0; attempt < 16; attempt++) {
            double ang = RNG.nextDouble() * Math.PI * 2;
            double cx = Math.cos(ang), cz = Math.sin(ang);
            BlockPos last = null;
            int lastDist = 0;
            for (int d = 2; d <= max; d++) {
                BlockPos feet = BlockPos.ofFloored(p.getX() + cx * d, p.getY(), p.getZ() + cz * d);
                if (!w.getBlockState(feet).isAir() || !w.getBlockState(feet.up()).isAir()
                        || !w.getBlockState(feet.down()).isSolidBlock(w, feet.down())) break;
                last = feet;
                lastDist = d;
            }
            if (last != null && lastDist >= min) return last;
        }
        return null;
    }

    /** De reojo: aparece fuera del centro de tu visión y desaparece en cuanto lo miras. */
    public static boolean glimpse(ServerPlayerEntity p, ServerWorld w, EchoState.PlayerData d, int day) {
        if (activeList(p.getUuid()).size() >= Math.max(1, echoCap(d, false))) return false;
        Vec3d look = p.getRotationVec(1.0f);
        double base = Math.atan2(look.z, look.x);
        for (int i = 0; i < 10; i++) {
            double off = Math.toRadians(55 + RNG.nextDouble() * 35) * (RNG.nextBoolean() ? 1 : -1);
            double dist = 7 + RNG.nextDouble() * 9;
            BlockPos pos = surfaceAt(w, p, (int) Math.floor(p.getX() + Math.cos(base + off) * dist),
                    (int) Math.floor(p.getZ() + Math.sin(base + off) * dist), 5);
            if (pos != null) return spawn(w, p, pos, EchoEntity.Mode.GLIMPSE, pickVariant(day, intensity(d))) != null;
        }
        return false;
    }

    // ------------------------------------------------------------------ El Registrador

    private static void registrarDirector(MinecraftServer server, ServerWorld w, ServerPlayerEntity p,
                                          EchoState.PlayerData d, int day, int stage) {
        EchoConfig cfg = EchoConfig.INSTANCE;
        if (day < cfg.registrarDay && d.paranoia < 50) return;
        UUID id = p.getUuid();
        EchoEntity cur = REGISTRAR.get(id);
        if (cur != null) {
            if (cur.isAlive() && !cur.isRemoved()) return;
            REGISTRAR.remove(id);
        }
        int now = server.getTicks();
        Integer next = NEXT_REGISTRAR.get(id);
        if (next != null && now < next) return;
        double chance = Math.min(0.35, 0.06 + day * 0.04 + d.paranoia * 0.002) * cfg.eventIntensity;
        if (stage >= 2) chance *= 2;
        if (RNG.nextDouble() > chance) return;

        boolean cave = !w.isSkyVisible(p.getBlockPos()) && p.getY() < 55;
        BlockPos s = cave ? findCavePos(w, p, 18, 34) : findPeripheralFar(w, p, 28, 46);
        if (s == null) return;
        EchoEntity e = spawn(w, p, s, EchoEntity.Mode.REGISTRAR, EchoEntity.V_PALE);
        if (e != null) {
            REGISTRAR.put(id, e);
            NEXT_REGISTRAR.put(id, now + 20 * (60 + RNG.nextInt(60)));
        }
    }

    private static BlockPos findPeripheralFar(ServerWorld w, ServerPlayerEntity p, int min, int max) {
        Vec3d look = p.getRotationVec(1.0f);
        double base = Math.atan2(look.z, look.x);
        for (int i = 0; i < 18; i++) {
            double off = Math.toRadians(40 + RNG.nextDouble() * 45) * (RNG.nextBoolean() ? 1 : -1);
            double dist = min + RNG.nextDouble() * (max - min);
            BlockPos pos = surfaceAt(w, p, (int) Math.floor(p.getX() + Math.cos(base + off) * dist),
                    (int) Math.floor(p.getZ() + Math.sin(base + off) * dist), 25);
            if (pos != null) return pos;
        }
        return null;
    }

    public static void registrarGone(ServerPlayerEntity p) {
        REGISTRAR.remove(p.getUuid());
        NEXT_REGISTRAR.put(p.getUuid(), p.getServer().getTicks() + 20 * (45 + RNG.nextInt(60)));
        EchoState st = EchoState.get(p.getServer());
        EchoState.PlayerData d = st.data(p.getUuid());
        d.paranoia = Math.min(100, d.paranoia + 1.0);
        st.markDirty();
    }

    // ------------------------------------------------------------------ sustos de los Ecos

    public static void rushEnd(ServerPlayerEntity p, EchoEntity e) {
        Paranoia.playTo(p, SoundEvents.ENTITY_GHAST_SCREAM, SoundCategory.HOSTILE, e.getPos(), 1.6f, 0.45f);
        Paranoia.playTo(p, SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, p.getPos(), 0.7f, 0.6f);
        scare(p, 2);
        addDread(p, 5.0);
        e.vanish();
    }

    public static void screamAt(ServerPlayerEntity p, EchoEntity e) {
        SoundEvent[] pool = {SoundEvents.ENTITY_GHAST_SCREAM, SoundEvents.ENTITY_ENDERMAN_SCREAM, SoundEvents.ENTITY_WARDEN_ROAR};
        Paranoia.playTo(p, pool[RNG.nextInt(pool.length)], SoundCategory.HOSTILE, e.getPos(), 1.4f, 0.5f + RNG.nextFloat() * 0.3f);
        scare(p, 1);
        addDread(p, 3.0);
    }

    // ------------------------------------------------------------------ grabación de tus acciones

    private static void record(ServerPlayerEntity p, SoundEvent sound) {
        ArrayDeque<Rec> q = RECORDINGS.computeIfAbsent(p.getUuid(), k -> new ArrayDeque<>());
        q.addLast(new Rec(p.getServer().getOverworld().getTime(), sound));
        while (q.size() > 16) q.removeFirst();
    }

    public static SoundEvent recordedSound(ServerPlayerEntity p) {
        ArrayDeque<Rec> q = RECORDINGS.get(p.getUuid());
        long now = p.getServer().getOverworld().getTime();
        List<Rec> recent = new ArrayList<>();
        if (q != null) for (Rec r : q) if (now - r.time() < 6000) recent.add(r);
        if (!recent.isEmpty()) return recent.get(RNG.nextInt(recent.size())).sound();
        return p.getServerWorld().getBlockState(p.getBlockPos().down()).getSoundGroup().getStepSound();
    }

    private static void onBlockBroken(World world, PlayerEntity player, BlockPos pos, BlockState state, net.minecraft.block.entity.BlockEntity be) {
        if (!(world instanceof ServerWorld w) || !(player instanceof ServerPlayerEntity sp)) return;
        record(sp, state.getSoundGroup().getBreakSound());
        EchoEntity reg = REGISTRAR.get(sp.getUuid());
        if (reg != null && reg.isAlive()) reg.swingHand(Hand.MAIN_HAND);
        boolean log = state.isIn(BlockTags.LOGS);
        boolean crop = state.getBlock() instanceof CropBlock cb && cb.isMature(state);
        if (!log && !crop) return;
        MinecraftServer server = w.getServer();
        EchoState st = EchoState.get(server);
        EchoState.PlayerData d = st.data(sp.getUuid());
        EchoConfig cfg = EchoConfig.INSTANCE;
        if (log) calm(d, cfg.chopDreadRelief, cfg.chopParanoiaRelief);
        else calm(d, cfg.chopDreadRelief * 0.6, cfg.chopParanoiaRelief * 0.6);
        st.markDirty();
    }

    // ------------------------------------------------------------------ dormir (noche eterna)

    private static void onWake(LivingEntity entity, BlockPos sleepingPos) {
        if (!(entity instanceof ServerPlayerEntity p)) return;
        ServerWorld w = p.getServerWorld();
        if (!w.getRegistryKey().equals(World.OVERWORLD)) return;
        if (p.getSleepTimer() < 90) return; // te levantaste enseguida: no cuenta como dormir

        MinecraftServer server = w.getServer();
        EchoConfig cfg = EchoConfig.INSTANCE;
        EchoState st = EchoState.get(server);
        EchoState.PlayerData d = st.data(p.getUuid());
        int day = effectiveDay(server, st);

        // Dormir calma la paranoia y descansa el cuerpo
        d.paranoia *= 0.4;
        d.dread *= 0.85;
        d.sleepStage = 0;
        Paranoia.resetRest(p);
        st.markDirty();
        p.sendMessage(Text.literal("Has descansado... por ahora.").formatted(Formatting.GRAY, Formatting.ITALIC), true);

        // Las ofrendas calman: mientras dure la calma no hay laberinto
        if (server.getOverworld().getTime() < d.banishedUntil) return;
        double chance = hijackChance(Math.max(0, day - 1), d.paranoia + 0.0);
        if (cfg.enableRealm && !d.inRealm && RNG.nextDouble() < chance) {
            PENDING_HIJACK.put(p.getUuid(), sleepingPos.toImmutable());
            ElEcho.LOGGER.info("{} despierta en el laberinto (prob. {} %).", p.getName().getString(), (int) (chance * 100));
            return;
        }
        if (day >= cfg.fakeBlockDay && RNG.nextDouble() < 0.7) leaveTraces(w, sleepingPos);
    }

    public static double hijackChance(int day, double paranoia) {
        EchoConfig cfg = EchoConfig.INSTANCE;
        return Math.max(0.0, Math.min(1.0, cfg.hijackChanceBase + day * cfg.hijackChancePerDay + paranoia * cfg.hijackParanoiaBonus));
    }

    /** Mientras dormías algo estuvo aquí: huellas en la hierba y puertas abiertas. */
    private static void leaveTraces(ServerWorld w, BlockPos bed) {
        double ang = RNG.nextDouble() * Math.PI * 2;
        double cos = Math.cos(ang), sin = Math.sin(ang);
        double px = -sin, pz = cos;
        for (int i = 0; i < 10; i++) {
            double dist = 12 - i * 1.1;
            double side = (i % 2 == 0) ? 0.45 : -0.45;
            int x = (int) Math.floor(bed.getX() + 0.5 + cos * dist + px * side);
            int z = (int) Math.floor(bed.getZ() + 0.5 + sin * dist + pz * side);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BlockPos g = new BlockPos(x, y, z);
            BlockState gs = w.getBlockState(g);
            if ((gs.isOf(Blocks.GRASS_BLOCK) || gs.isOf(Blocks.DIRT)) && w.getBlockState(g.up()).isAir()) {
                w.setBlockState(g, Blocks.COARSE_DIRT.getDefaultState());
            }
        }
        for (BlockPos pos : BlockPos.iterate(bed.add(-8, -2, -8), bed.add(8, 4, 8))) {
            BlockState s = w.getBlockState(pos);
            if (s.isIn(BlockTags.DOORS) && s.contains(DoorBlock.HALF) && s.get(DoorBlock.HALF) == net.minecraft.block.enums.DoubleBlockHalf.LOWER && RNG.nextInt(3) == 0) {
                ((DoorBlock) s.getBlock()).setOpen(null, w, s, pos.toImmutable(), !s.get(DoorBlock.OPEN));
            }
        }
    }

    // ------------------------------------------------------------------ espejo y ofrendas

    public static void registerMirror(World world, BlockPos pos) {
        if (!(world instanceof ServerWorld w)) return;
        EchoState st = EchoState.get(w.getServer());
        String dim = w.getRegistryKey().getValue().toString();
        for (EchoState.MirrorPos m : st.mirrors) if (m.dim().equals(dim) && m.pos().equals(pos)) return;
        st.mirrors.add(new EchoState.MirrorPos(dim, pos.toImmutable()));
        st.markDirty();
    }

    public static boolean isProtected(ServerWorld w, BlockPos pos) {
        EchoState st = EchoState.get(w.getServer());
        if (st.mirrors.isEmpty()) return false;
        String dim = w.getRegistryKey().getValue().toString();
        long r = EchoConfig.INSTANCE.mirrorRadius;
        boolean dirty = false;
        boolean result = false;
        Iterator<EchoState.MirrorPos> it = st.mirrors.iterator();
        while (it.hasNext()) {
            EchoState.MirrorPos m = it.next();
            if (!dim.equals(m.dim())) continue;
            if (pos.getSquaredDistance(m.pos()) > r * r) continue;
            if (w.isChunkLoaded(m.pos().getX() >> 4, m.pos().getZ() >> 4) && !w.getBlockState(m.pos()).isOf(ModBlocks.ECHO_MIRROR)) {
                it.remove();
                dirty = true;
                continue;
            }
            result = true;
            break;
        }
        if (dirty) st.markDirty();
        return result;
    }

    public static void offer(ServerPlayerEntity p, ItemStack stack, BlockPos mirrorPos) {
        ServerWorld w = p.getServerWorld();
        MinecraftServer server = w.getServer();
        EchoConfig cfg = EchoConfig.INSTANCE;
        EchoState st = EchoState.get(server);
        EchoState.PlayerData d = st.data(p.getUuid());
        String id = Registries.ITEM.getId(stack.getItem()).toString();

        if (!d.day1Items.contains(id)) {
            p.sendMessage(Text.literal("El espejo no reconoce esto. No viene de una noche anterior.").formatted(Formatting.GRAY), true);
            return;
        }
        if (d.offered.contains(id)) {
            p.sendMessage(Text.literal("Ya le diste esto. Quiere algo distinto.").formatted(Formatting.GRAY), true);
            return;
        }
        double value = offeringValue(stack);
        stack.decrementUnlessCreative(1, p);
        d.offered.add(id);
        long now = server.getOverworld().getTime();
        long base = Math.max(d.banishedUntil, now) + (long) (cfg.offeringBanishDays * (double) cfg.dayLengthTicks * (value / 30.0));
        d.banishedUntil = Math.min(base, now + (long) cfg.maxBanishDays * cfg.dayLengthTicks);
        d.dread = Math.max(0, d.dread - Math.min(cfg.offeringDreadReduction, value));
        d.paranoia = Math.max(0, d.paranoia - Math.min(cfg.offeringParanoiaReduction, value * 0.9));
        if (value >= 18) st.relief = Math.min(st.relief + cfg.offeringDayRelief, globalDay(server, st));
        st.markDirty();
        dismissAll(p.getUuid());
        int day = effectiveDay(server, st);
        LAST_HUD.remove(p.getUuid());
        send(p, new DreadPayload(1, (int) Math.min(100, d.dread), (int) Math.min(100, d.paranoia), day + 1, stageOf(server, d, day), d.sleepStage * 10));

        w.spawnParticles(ParticleTypes.SCULK_SOUL, mirrorPos.getX() + 0.5, mirrorPos.getY() + 0.8, mirrorPos.getZ() + 0.5, 30, 0.3, 0.5, 0.3, 0.1);
        w.playSound(null, mirrorPos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0f, 0.7f);
        String effect = value < 8 ? "El reflejo se estremece apenas." : value < 16 ? "El reflejo retrocede un poco."
                : "El reflejo retrocede bastante... pero no se va.";
        p.sendMessage(Text.literal(effect).formatted(Formatting.AQUA, Formatting.ITALIC), true);
    }

    public static double offeringValue(ItemStack st) {
        String path = Registries.ITEM.getId(st.getItem()).getPath();
        if (path.contains("netherite")) return 30;
        if (path.contains("diamond")) return 26;
        if (path.equals("golden_apple") || path.equals("enchanted_golden_apple")) return 22;
        if (path.contains("emerald")) return 20;
        if (path.contains("gold")) return 16;
        if (path.contains("iron")) return 12;
        if (path.equals("book") || path.contains("bed") || path.equals("map") || path.equals("compass") || path.equals("lighter")) return 10;
        if (st.contains(DataComponentTypes.FOOD)) return 9;
        if (path.contains("torch") || path.contains("lantern")) return 7;
        if (path.contains("log") || path.contains("planks")) return 5;
        if (path.contains("stone") || path.equals("dirt")) return 4;
        if (path.equals("stick")) return 3;
        return 6;
    }

    public static Text mirrorMessage(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        EchoConfig cfg = EchoConfig.INSTANCE;
        EchoState st = EchoState.get(server);
        EchoState.PlayerData d = st.data(p.getUuid());
        long now = server.getOverworld().getTime();
        Long last = LAST_GAZE.get(p.getUuid());
        boolean ready = last == null || now - last >= cfg.mirrorGazeCooldownSeconds * 20L;
        String s;
        if (ready && (d.dread > 0 || d.paranoia > 0)) {
            LAST_GAZE.put(p.getUuid(), now);
            calm(d, cfg.mirrorGazeRelief, cfg.mirrorGazeRelief);
            st.markDirty();
            LAST_HUD.remove(p.getUuid());
            s = "Tu reflejo te sostiene la mirada. Respiras un poco mejor.";
        } else if (d.paranoia < 30) s = "Tu reflejo te devuelve la mirada... un instante tarde.";
        else if (d.paranoia < 70) s = "Tu reflejo sonríe un momento antes que tú.";
        else s = "No eres tú quien mira. Ofrécele algo de tus primeras noches.";
        return Text.literal(s).formatted(Formatting.GRAY, Formatting.ITALIC);
    }

    // ------------------------------------------------------------------ el laberinto del reino espejo

    private static void hijack(MinecraftServer server, EchoState st, ServerPlayerEntity p, BlockPos sleepPos) {
        try {
            hijackInner(server, st, p, sleepPos);
        } catch (RuntimeException ex) {
            ElEcho.LOGGER.error("No se pudo llevar a " + p.getName().getString() + " al laberinto", ex);
            st.data(p.getUuid()).inRealm = false;
            st.markDirty();
            p.sendMessage(Text.literal("[El Echo] Error al crear el laberinto (mira latest.log).").formatted(Formatting.RED), false);
        }
    }

    private static void hijackInner(MinecraftServer server, EchoState st, ServerPlayerEntity p, BlockPos sleepPos) {
        ServerWorld realm = server.getWorld(REALM);
        if (realm == null) {
            p.sendMessage(Text.literal("[El Echo] La dimensión elecho:mirror_realm no está cargada. Prueba en un mundo nuevo.")
                    .formatted(Formatting.RED), false);
            return;
        }
        ServerWorld src = p.getServerWorld();
        EchoState.PlayerData d = st.data(p.getUuid());
        if (d.slot < 0) d.slot = st.nextSlot++;
        BlockPos dstOrigin = new BlockPos(d.slot * 160, 20, 0);

        d.returnPos = sleepPos;
        d.returnDim = src.getRegistryKey().getValue().toString();
        RealmBuilder.Result res = RealmBuilder.build(realm, dstOrigin, RNG);
        d.realmBase = res.start();
        d.realmOriginal = res.original();
        TRAPS.put(p.getUuid(), new ArrayList<>(res.traps()));
        REALM_CELLS.put(p.getUuid(), res.cells());
        d.inRealm = true;
        d.offered.clear();
        st.markDirty();

        REALM_TICKS.put(p.getUuid(), 0);
        dismissAll(p.getUuid());
        BlockPos st0 = res.start();
        p.teleport(realm, st0.getX() + 0.5, st0.getY(), st0.getZ() + 0.5, p.getYaw(), p.getPitch());
        scare(p, 1);
        for (int i = 0; i < 3; i++) {
            BlockPos c = pickCell(res.cells(), st0, 14);
            if (c != null) spawn(realm, p, c, EchoEntity.Mode.STALK, i == 0 ? EchoEntity.V_SHADOW : EchoEntity.V_NORMAL);
        }
        p.sendMessage(Text.literal("Despiertas. Esto no es tu cama.").formatted(Formatting.DARK_RED, Formatting.ITALIC), true);
    }

    public static void forceHijack(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        hijack(server, EchoState.get(server), p, p.getBlockPos());
    }

    private static BlockPos pickCell(List<BlockPos> cells, BlockPos from, int minDist) {
        if (cells == null || cells.isEmpty()) return null;
        for (int i = 0; i < 30; i++) {
            BlockPos c = cells.get(RNG.nextInt(cells.size()));
            if (c.getSquaredDistance(from) >= (double) minDist * minDist) return c;
        }
        return null;
    }

    private static void tickRealm(MinecraftServer server, EchoState st, ServerPlayerEntity p, ServerWorld w,
                                  EchoState.PlayerData d, int day) {
        int rt = REALM_TICKS.merge(p.getUuid(), 1, Integer::sum);
        if (rt % 20 == 0) send(p, new DreadPayload(1, 100, 100, day + 1, 2, 0));
        if (rt % 700 == 0) p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 70, 0, false, false, false));
        if (d.realmBase != null && p.getY() < d.realmBase.getY() - 6) {
            p.teleport(w, d.realmBase.getX() + 0.5, d.realmBase.getY(), d.realmBase.getZ() + 0.5, p.getYaw(), p.getPitch());
        }
        if (rt % 5 == 0) {
            List<BlockPos> traps = TRAPS.get(p.getUuid());
            if (traps != null) {
                for (Iterator<BlockPos> it = traps.iterator(); it.hasNext(); ) {
                    BlockPos tp = it.next();
                    double dx = p.getX() - (tp.getX() + 0.5), dz = p.getZ() - (tp.getZ() + 0.5);
                    if (dx * dx + dz * dz < 2.5 * 2.5 && Math.abs(p.getY() - tp.getY()) < 3) {
                        it.remove();
                        jumpscare(p, w);
                        break;
                    }
                }
            }
        }
        if (rt % 240 == 0) {
            List<BlockPos> cells = REALM_CELLS.get(p.getUuid());
            int want = Math.min(5, 3 + rt / 2400);
            int have = w.getEntitiesByClass(EchoEntity.class, p.getBoundingBox().expand(150),
                    e -> p.getUuid().equals(e.getOwnerUuid())).size();
            for (int i = have; i < want; i++) {
                BlockPos c = pickCell(cells, p.getBlockPos(), 14);
                if (c != null) spawn(w, p, c, EchoEntity.Mode.STALK, RNG.nextInt(3) == 0 ? EchoEntity.V_SHADOW : EchoEntity.V_NORMAL);
            }
        }
        if (rt % 160 == 0 && RNG.nextInt(2) == 0) realmAmbient(server, p);
        if (rt > 2400 && rt % 80 == 0 && d.realmOriginal != null) {
            BlockPos o = d.realmOriginal;
            w.spawnParticles(p, ParticleTypes.SOUL_FIRE_FLAME, true, o.getX() + 0.5, o.getY() + 1.0, o.getZ() + 0.5, 10, 0.15, 1.5, 0.15, 0.01);
        }
    }

    private static void realmAmbient(MinecraftServer server, ServerPlayerEntity p) {
        Vec3d back = Paranoia.back(p);
        Vec3d base = p.getPos();
        SoundEvent[] pool = {SoundEvents.ENTITY_WARDEN_AMBIENT, SoundEvents.ENTITY_WARDEN_NEARBY_CLOSE,
                SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundEvents.ENTITY_ENDERMAN_STARE, SoundEvents.ENTITY_PHANTOM_AMBIENT};
        if (RNG.nextInt(3) == 0) {
            SoundEvent step = SoundEvents.BLOCK_DEEPSLATE_STEP;
            for (int i = 0; i < 5; i++) {
                final int k = i;
                Sched.at(server, i * 8, () -> Paranoia.playTo(p, step, SoundCategory.PLAYERS, base.add(back.multiply(5.0 - k * 0.8)), 0.9f, 0.8f));
            }
        } else {
            Paranoia.playTo(p, pool[RNG.nextInt(pool.length)], SoundCategory.AMBIENT, Paranoia.ring(base, 6 + RNG.nextInt(8)), 0.8f, 0.5f + RNG.nextFloat() * 0.3f);
        }
    }

    private static void jumpscare(ServerPlayerEntity p, ServerWorld w) {
        scare(p, 2);
        w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GHAST_SCREAM, SoundCategory.HOSTILE, 1.0f, 0.6f);
        w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, 1.0f, 0.9f);
        Vec3d look = p.getRotationVec(1.0f);
        Vec3d h = new Vec3d(look.x, 0, look.z);
        h = h.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : h.normalize();
        for (double dist : new double[]{2.6, 2.0, 1.5}) {
            BlockPos spot = BlockPos.ofFloored(p.getX() + h.x * dist, p.getY(), p.getZ() + h.z * dist);
            if (w.getBlockState(spot).isAir() && w.getBlockState(spot.up()).isAir() && w.getBlockState(spot.down()).isSolidBlock(w, spot.down())) {
                spawn(w, p, spot, EchoEntity.Mode.JUMP, RNG.nextBoolean() ? EchoEntity.V_SHADOW : EchoEntity.V_NORMAL);
                break;
            }
        }
    }

    /** Un Eco te alcanzó: susto y vuelves al inicio del laberinto (o te hiere de verdad en el apocalipsis). */
    public static void caught(ServerPlayerEntity p, EchoEntity e) {
        EchoState st = EchoState.get(p.getServer());
        EchoState.PlayerData d = st.data(p.getUuid());
        ServerWorld w = p.getServerWorld();
        e.vanish();
        scare(p, 2);
        w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GHAST_SCREAM, SoundCategory.HOSTILE, 1.0f, 0.5f);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 0, false, false, false));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 30, 0, false, false, false));
        if (w.getRegistryKey().equals(REALM)) {
            BlockPos b = d.realmBase;
            if (b != null) p.teleport(w, b.getX() + 0.5, b.getY(), b.getZ() + 0.5, p.getYaw(), p.getPitch());
        } else if (p.getHealth() > 7.0f) {
            p.damage(w.getDamageSources().magic(), 6.0f);
        }
    }

    public static void leaveRealm(ServerPlayerEntity p, boolean found) {
        MinecraftServer server = p.getServer();
        EchoState st = EchoState.get(server);
        EchoState.PlayerData d = st.data(p.getUuid());

        ServerWorld dest = server.getOverworld();
        if (d.returnDim != null) {
            net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(d.returnDim);
            if (id != null) {
                ServerWorld w = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
                if (w != null) dest = w;
            }
        }
        BlockPos r = d.returnPos != null ? d.returnPos : dest.getSpawnPos();
        p.removeStatusEffect(StatusEffects.DARKNESS);
        p.teleport(dest, r.getX() + 0.5, r.getY() + 0.6, r.getZ() + 0.5, p.getYaw(), p.getPitch());

        d.inRealm = false;
        REALM_TICKS.remove(p.getUuid());
        TRAPS.remove(p.getUuid());
        REALM_CELLS.remove(p.getUuid());
        if (found) {
            d.dread = Math.min(d.dread, 25);
            d.paranoia = Math.min(d.paranoia, 25);
            d.banishedUntil = server.getOverworld().getTime() + 2L * EchoConfig.INSTANCE.dayLengthTicks;
        }
        st.markDirty();
        p.sendMessage(Text.literal(found ? "Encontraste el original." : "Estás de vuelta.").formatted(Formatting.GRAY, Formatting.ITALIC), true);
    }

    // ------------------------------------------------------------------ utilidades para comandos

    public static String status(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        EchoState st = EchoState.get(server);
        EchoState.PlayerData d = st.data(p.getUuid());
        int day = effectiveDay(server, st);
        long calm = Math.max(0, d.banishedUntil - server.getOverworld().getTime());
        return String.format("Noche %d (mundo: %d, alivio: %d) | miedo %.1f | paranoia %.1f | sueño: etapa %d (%d ticks sin dormir) | " +
                        "calma %.1f noches | laberinto: %s | bloques por deshacer: %d | etapa: %d | protegido: %s | prob. laberinto al dormir: %.0f%% | Ecos máx. ahora: %d",
                day + 1, globalDay(server, st), st.relief, d.dread, d.paranoia, Paranoia.sleepStage(p), Paranoia.restTicks(p),
                calm / (double) EchoConfig.INSTANCE.dayLengthTicks, d.inRealm, TerrainMemory.pending(server), stageOf(server, d, day),
                isProtected(p.getServerWorld(), p.getBlockPos()), hijackChance(Math.max(0, day - 1), d.paranoia) * 100.0, echoCap(d, false));
    }

    public static void setDread(ServerPlayerEntity p, double value) {
        EchoState st = EchoState.get(p.getServer());
        st.data(p.getUuid()).dread = value;
        st.markDirty();
    }

    public static void setParanoia(ServerPlayerEntity p, double value) {
        EchoState st = EchoState.get(p.getServer());
        st.data(p.getUuid()).paranoia = value;
        st.markDirty();
    }

    /** Fija la noche del Eco (1 = primera). */
    public static void setDay(ServerPlayerEntity p, int echoDay) {
        MinecraftServer server = p.getServer();
        EchoState st = EchoState.get(server);
        st.relief = Math.max(0, globalDay(server, st) - Math.max(0, echoDay - 1));
        st.markDirty();
    }

    public static void forceChat(ServerPlayerEntity p, int tier) {
        MinecraftServer server = p.getServer();
        EchoState st = EchoState.get(server);
        ChatHaunt.force(server, p, tier, effectiveDay(server, st));
    }

    public static boolean debugSpawn(ServerPlayerEntity p, String kind) {
        ServerWorld w = p.getServerWorld();
        MinecraftServer server = p.getServer();
        EchoState st = EchoState.get(server);
        EchoState.PlayerData d = st.data(p.getUuid());
        int day = Math.max(effectiveDay(server, st), 8);
        EchoEntity.Mode mode;
        int variant = EchoEntity.V_NORMAL;
        switch (kind) {
            case "registrador" -> {
                BlockPos s = findPeripheralFar(w, p, 22, 34);
                if (s == null) return false;
                EchoEntity e = spawn(w, p, s, EchoEntity.Mode.REGISTRAR, EchoEntity.V_PALE);
                if (e != null) REGISTRAR.put(p.getUuid(), e);
                return e != null;
            }
            case "miron" -> { return spawnKind(w, p, d, day, Kind.LURKER); }
            case "observador" -> { return spawnObserverNear(w, p); }
            case "sombra" -> { mode = EchoEntity.Mode.WATCH; variant = EchoEntity.V_SHADOW; }
            case "corre" -> mode = EchoEntity.Mode.RUSH;
            case "grita" -> mode = EchoEntity.Mode.SCREAM;
            case "vuela" -> mode = EchoEntity.Mode.FLY_WATCH;
            case "lanza" -> mode = EchoEntity.Mode.FLY_SWOOP;
            case "camina" -> mode = EchoEntity.Mode.WALK;
            case "reojo" -> { return glimpse(p, w, d, day); }
            default -> mode = EchoEntity.Mode.WATCH;
        }
        BlockPos s = findSurfaceSpot(w, p, 14, 22, 60);
        if (s != null && (mode == EchoEntity.Mode.FLY_WATCH || mode == EchoEntity.Mode.FLY_SWOOP)) {
            int h = 9;
            while (h > 3 && !columnFree(w, s, h)) h--;
            s = s.up(h);
        }
        return s != null && spawn(w, p, s, mode, variant) != null;
    }

    public static boolean forceShrine(ServerPlayerEntity p, Integer which) {
        ServerWorld w = p.getServerWorld();
        EchoState st = EchoState.get(p.getServer());
        boolean chain = which == null && st.sites.size() < Shrines.NOTE_COUNT;
        int idx = which != null ? which - 1 : chain ? st.sites.size() : RNG.nextInt(Shrines.NOTE_COUNT);
        Shrines.Kind k = Shrines.kindFor(idx);
        for (int i = 0; i < 20; i++) {
            double ang = RNG.nextDouble() * Math.PI * 2;
            double dist = 14 + RNG.nextDouble() * 18;
            BlockPos c = Shrines.tryPlace(w, (int) (p.getX() + Math.cos(ang) * dist) - k.w / 2,
                    (int) (p.getZ() + Math.sin(ang) * dist) - k.d / 2, idx);
            if (c != null) {
                if (chain) {
                    st.sites.add(c);
                    st.siteHeading = ang;
                    st.markDirty();
                }
                p.sendMessage(Text.literal("Estructura " + (idx + 1) + " (" + k.label + ") en " + c.getX() + " " + c.getY() + " " + c.getZ()
                        + (chain ? " [añadida a la cadena]" : "")), false);
                return true;
            }
        }
        return false;
    }

    public static void listSites(ServerPlayerEntity p) {
        EchoState st = EchoState.get(p.getServer());
        if (st.sites.isEmpty()) p.sendMessage(Text.literal("Aún no hay estructuras."), false);
        for (int i = 0; i < st.sites.size(); i++) {
            BlockPos c = st.sites.get(i);
            p.sendMessage(Text.literal((i + 1) + ". " + Shrines.kindFor(i).label + ": " + c.getX() + " " + c.getY() + " " + c.getZ()), false);
        }
    }

    public static void forceDecay(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        EchoState st = EchoState.get(server);
        TerrainMemory.tick(server, p.getServerWorld(), p, Math.max(10, effectiveDay(server, st)));
    }

    public static void reset(ServerPlayerEntity p) {
        EchoState st = EchoState.get(p.getServer());
        st.reset(p.getUuid());
        st.apocTriggered = false;
        st.apocStart = -1;
        APOC_SEEN.clear();
        st.markDirty();
        dismissAll(p.getUuid());
    }
}
