package com.elecho;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.Random;

/**
 * Paranoia auditiva y sueño.
 * - Sonidos "inversos" (pasos a tu espalda, un cofre que se abre detrás de ti, tus propias pisadas...).
 *   Cuanto MÁS PARANOIA, más sonidos a la vez, más seguidos y de más tipos.
 * - Aviso de sueño: en la noche eterna no hay sol que te avise, solo tu cuerpo.
 */
public final class Paranoia {
    private static final Random RNG = new Random();

    private Paranoia() {}

    /** 0..1: cuánto llevas sin dormir (40 min de juego sin cama = 1). */
    public static double insomnia(ServerPlayerEntity p) {
        return Math.max(0.0, Math.min(1.0, restTicks(p) / 48000.0));
    }

    public static int restTicks(ServerPlayerEntity p) {
        return p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.TIME_SINCE_REST));
    }

    public static void resetRest(ServerPlayerEntity p) {
        p.getStatHandler().setStat(p, Stats.CUSTOM.getOrCreateStat(Stats.TIME_SINCE_REST), 0);
    }

    public static void playTo(ServerPlayerEntity p, SoundEvent s, SoundCategory c, Vec3d pos, float vol, float pitch) {
        if (p.isRemoved() || p.networkHandler == null) return;
        p.networkHandler.sendPacket(new PlaySoundS2CPacket(Registries.SOUND_EVENT.getEntry(s), c,
                pos.x, pos.y, pos.z, vol, pitch, p.getRandom().nextLong()));
    }

    public static Vec3d back(ServerPlayerEntity p) {
        Vec3d look = p.getRotationVec(1.0f);
        Vec3d b = new Vec3d(-look.x, 0, -look.z);
        return b.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : b.normalize();
    }

    public static Vec3d ring(Vec3d base, double dist) {
        double a = RNG.nextDouble() * Math.PI * 2;
        return base.add(Math.cos(a) * dist, 0, Math.sin(a) * dist);
    }

    // ------------------------------------------------------------------ sueño

    public static int sleepStage(ServerPlayerEntity p) {
        EchoConfig c = EchoConfig.INSTANCE;
        int rest = restTicks(p);
        if (rest >= c.sleepWarn3Ticks) return 3;
        if (rest >= c.sleepWarn2Ticks) return 2;
        if (rest >= c.sleepWarn1Ticks) return 1;
        return 0;
    }

    /** Una vez por segundo: avisa de que tienes que dormir y empeora los síntomas si no lo haces. */
    public static void sleepTick(MinecraftServer server, ServerPlayerEntity p, EchoState.PlayerData d, int day, long sec) {
        int stage = sleepStage(p);
        if (stage > d.sleepStage) {
            sleepAnnounce(server, p, stage, day);
        } else if (stage >= 2 && sec % 60 == 0) {
            sleepAnnounce(server, p, stage, day);
        } else if (stage == 1 && sec % 120 == 0) {
            p.sendMessage(Text.literal("Tus ojos pesan... deberías dormir.").formatted(Formatting.GRAY, Formatting.ITALIC), true);
        }
        d.sleepStage = stage;
        if (stage >= 2 && sec % 15 == 0) { // bostezos y mareos
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 90, 0, false, false, false));
        }
        if (stage >= 3 && sec % 10 == 0 && RNG.nextInt(3) == 0) { // microsueños
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 60, 0, false, false, false));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1, false, false, false));
        }
    }

    private static void sleepAnnounce(MinecraftServer server, ServerPlayerEntity p, int stage, int day) {
        switch (stage) {
            case 1 -> p.sendMessage(Text.literal("Tus ojos pesan... aquí nunca amanece. Deberías dormir.")
                    .formatted(Formatting.GRAY, Formatting.ITALIC), true);
            case 2 -> p.sendMessage(Text.literal("Apenas te mantienes en pie. Busca una cama y duerme.")
                    .formatted(Formatting.RED, Formatting.ITALIC), true);
            default -> p.sendMessage(Text.literal("TE ESTÁS DURMIENDO DE PIE. DUERME YA.")
                    .formatted(Formatting.DARK_RED, Formatting.BOLD), true);
        }
        playTo(p, SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.AMBIENT, p.getPos(), 0.6f + 0.2f * stage, 0.7f);
        ChatHaunt.sleepNudge(server, p, stage, day);
    }

    // ------------------------------------------------------------------ sonidos paranoicos

    /** Una vez por segundo. */
    public static void tick(MinecraftServer server, ServerPlayerEntity p, ServerWorld w, EchoState.PlayerData d, int day) {
        EchoConfig cfg = EchoConfig.INSTANCE;
        if (day < cfg.audioStartDay) return;
        double ins = insomnia(p);
        double par = d.paranoia;
        double chance = Math.min(0.65, (0.02 + par * 0.0045 + ins * 0.07 + day * 0.004) * cfg.soundScale * cfg.eventIntensity);
        if (RNG.nextDouble() > chance) return;

        // cuanta más paranoia, más sonidos seguidos
        int n = 1 + (par > 40 ? 1 : 0) + (par > 70 ? 1 : 0) + (par > 90 ? 1 : 0);
        for (int i = 0; i < n; i++) {
            final int k = i;
            Sched.at(server, i == 0 ? 0 : 8 + RNG.nextInt(30) * k, () -> {
                if (p.isRemoved()) return;
                oneSound(server, p, w, d, day, ins);
            });
        }
        d.paranoia = Math.min(100, d.paranoia + 0.5);
        EchoManager.markDirty(server);
    }

    private static void oneSound(MinecraftServer server, ServerPlayerEntity p, ServerWorld w, EchoState.PlayerData d, int day, double ins) {
        EchoConfig cfg = EchoConfig.INSTANCE;
        boolean underground = !w.isSkyVisible(p.getBlockPos()) && p.getY() < 55;
        int max = 6;
        if (d.paranoia > 20 || day >= 1) max = 8;
        if (d.paranoia > 40 || day >= cfg.fakeBlockDay) max = 10;
        if (d.paranoia > 60 || d.dread > 60 || ins > 0.7) max = 13;
        int roll = RNG.nextInt(max);
        if (underground && RNG.nextInt(3) == 0) roll = 0;
        Vec3d back = back(p);
        Vec3d base = p.getPos();

        switch (roll) {
            case 0 -> { // pasos justo detrás de ti
                SoundEvent step = w.getBlockState(p.getBlockPos().down()).getSoundGroup().getStepSound();
                for (int i = 0; i < 6; i++) {
                    final int k = i;
                    Sched.at(server, i * 10, () -> playTo(p, step, SoundCategory.PLAYERS,
                            base.add(back.multiply(6.0 - k * 0.9)), 0.8f, 0.9f + RNG.nextFloat() * 0.2f));
                }
            }
            case 1 -> { // un cofre abriéndose a tu espalda
                Vec3d pos = base.add(back.multiply(4));
                playTo(p, SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, pos, 0.7f, 0.95f);
                Sched.at(server, 50, () -> playTo(p, SoundEvents.BLOCK_CHEST_CLOSE, SoundCategory.BLOCKS, pos, 0.7f, 0.95f));
            }
            case 2 -> { // una puerta
                Vec3d pos = ring(base, 6);
                playTo(p, SoundEvents.BLOCK_WOODEN_DOOR_OPEN, SoundCategory.BLOCKS, pos, 0.8f, 0.9f);
                Sched.at(server, 40, () -> playTo(p, SoundEvents.BLOCK_WOODEN_DOOR_CLOSE, SoundCategory.BLOCKS, pos, 0.8f, 0.9f));
            }
            case 3 -> playTo(p, EchoManager.recordedSound(p), SoundCategory.PLAYERS, base.add(back.multiply(4)), 0.7f, 0.6f); // tus pisadas de hace un rato
            case 4 -> playTo(p, SoundEvents.ENTITY_CREEPER_PRIMED, SoundCategory.HOSTILE, base.add(back.multiply(3)), 1.0f, 1.0f);
            case 5 -> playTo(p, RNG.nextBoolean() ? SoundEvents.ENTITY_GHAST_SCREAM : SoundEvents.ENTITY_ENDERMAN_SCREAM,
                    SoundCategory.HOSTILE, ring(base, 28), 0.6f, 0.6f + RNG.nextFloat() * 0.2f);
            case 6 -> playTo(p, SoundEvents.ENTITY_WARDEN_NEARBY_CLOSE, SoundCategory.AMBIENT, base.add(back.multiply(2)), 0.7f, 0.5f);
            case 7 -> playTo(p, SoundEvents.ENTITY_WARDEN_AMBIENT, SoundCategory.HOSTILE, ring(base, 8), 0.8f, 0.7f);
            case 8 -> EchoManager.glimpse(p, w, d, day);
            case 9 -> fakeVillager(server, p, w);
            case 10 -> { // campana lejana, no hay iglesia cerca
                playTo(p, SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, ring(base, 40), 1.0f, 0.5f);
                d.paranoia = Math.min(100, d.paranoia + 0.5);
            }
            case 11 -> { // alguien golpea una puerta que no existe
                Vec3d pos = ring(base, 5);
                for (int i = 0; i < 3; i++) {
                    Sched.at(server, i * 12, () -> playTo(p, SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, SoundCategory.HOSTILE, pos, 0.9f, 0.8f));
                }
            }
            default -> { // grito del sculk + sobresalto
                playTo(p, SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.HOSTILE, base.add(back.multiply(5)), 1.0f, 0.7f);
                EchoManager.scare(p, 1);
                d.dread += 1.5;
            }
        }
    }

    // ------------------------------------------------------------------ falsos bloques (solo los ve el jugador)

    private static void fakeVillager(MinecraftServer server, ServerPlayerEntity p, ServerWorld w) {
        BlockPos spot = findSpot(w, p, 7, 14);
        if (spot == null) return;
        Vec3d center = Vec3d.ofCenter(spot);
        BlockState fake = switch (RNG.nextInt(3)) {
            case 0 -> Blocks.WHITE_WOOL.getDefaultState();
            case 1 -> Blocks.BROWN_WOOL.getDefaultState();
            default -> Blocks.MAGENTA_GLAZED_TERRACOTTA.getDefaultState();
        };
        playTo(p, SoundEvents.ENTITY_VILLAGER_AMBIENT, SoundCategory.NEUTRAL, center, 1.0f, 0.75f);
        Sched.at(server, 30, () -> {
            if (p.isRemoved()) return;
            p.networkHandler.sendPacket(new BlockUpdateS2CPacket(spot, fake));
            p.networkHandler.sendPacket(new BlockUpdateS2CPacket(spot.up(), fake));
            playTo(p, SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.NEUTRAL, center, 1.0f, 0.7f);
            watchFake(server, p, w, spot, 0);
        });
    }

    private static void watchFake(MinecraftServer server, ServerPlayerEntity p, ServerWorld w, BlockPos spot, int age) {
        Sched.at(server, 10, () -> {
            if (p.isRemoved()) return;
            boolean close = p.squaredDistanceTo(Vec3d.ofCenter(spot)) < 3.5 * 3.5;
            if (close || age > 60 || p.getServerWorld() != w) {
                p.networkHandler.sendPacket(new BlockUpdateS2CPacket(spot, w.getBlockState(spot)));
                p.networkHandler.sendPacket(new BlockUpdateS2CPacket(spot.up(), w.getBlockState(spot.up())));
                if (close) {
                    playTo(p, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, Vec3d.ofCenter(spot), 1.0f, 0.5f);
                    playTo(p, SoundEvents.ENTITY_WARDEN_NEARBY_CLOSER, SoundCategory.HOSTILE, p.getPos(), 0.5f, 0.6f);
                    EchoManager.scare(p, 1);
                }
            } else {
                watchFake(server, p, w, spot, age + 1);
            }
        });
    }

    private static BlockPos findSpot(ServerWorld w, ServerPlayerEntity p, int min, int max) {
        for (int i = 0; i < 14; i++) {
            double ang = RNG.nextDouble() * Math.PI * 2;
            double dist = min + RNG.nextDouble() * (max - min);
            int x = (int) Math.floor(p.getX() + Math.cos(ang) * dist);
            int z = (int) Math.floor(p.getZ() + Math.sin(ang) * dist);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            BlockPos pos = new BlockPos(x, w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (Math.abs(pos.getY() - p.getY()) > 6) continue;
            if (!w.getBlockState(pos.down()).isSolidBlock(w, pos.down())) continue;
            if (!w.getBlockState(pos).isAir() || !w.getBlockState(pos.up()).isAir()) continue;
            return pos;
        }
        return null;
    }
}
