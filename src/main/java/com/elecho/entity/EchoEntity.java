package com.elecho.entity;

import com.elecho.EchoManager;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.UUID;

/**
 * El Eco: un doble tuyo. Nunca ataca ni hace daño; según su {@link Mode} y su variante (skin)
 * mira, camina, corre hacia ti y desaparece, grita, vuela, copia tus gestos (El Registrador)...
 * No tiene nametag y nunca se guarda en el mundo.
 */
public class EchoEntity extends PathAwareEntity {

    public enum Mode {
        WATCH,      // se queda quieto mirándote fijamente
        WALK,       // camina lentamente hacia ti
        STALK,      // (laberinto / apocalipsis) solo se mueve cuando no lo miras; si te toca, te alcanza
        GLIMPSE,    // aparece de reojo y desaparece cuando lo miras
        JUMP,       // susto: aparece frente a ti un instante
        RUSH,       // corre hacia ti y desaparece justo antes de tocarte
        SCREAM,     // aparece y grita
        FLY_WATCH,  // flota en el aire observándote desde arriba
        FLY_SWOOP,  // flota, y de pronto se lanza hacia ti
        REGISTRAR,  // El Registrador: copia tus gestos, se acerca un bloque si te quedas quieto
        LURKER      // el Mirón: te mira de lejos; si lo miras más de 5 s, corre hacia ti
    }

    public static final int V_NORMAL = 0; // skin del jugador
    public static final int V_SHADOW = 1; // skin negra con ojos blancos
    public static final int V_PALE = 2;   // blanca translúcida (El Registrador)

    private static final TrackedData<Optional<UUID>> OWNER =
            DataTracker.registerData(EchoEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Integer> VARIANT =
            DataTracker.registerData(EchoEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private Mode mode = Mode.WATCH;
    private int life = 0;
    private int maxLife = 20 * 100;
    private int seenTicks = 0;
    private int gaze = 0;
    private boolean charging = false;
    private int stillTicks = 0;
    private Vec3d lastOwnerPos = null;
    private double orbit = Math.random() * Math.PI * 2;

    public EchoEntity(EntityType<? extends EchoEntity> type, World world) {
        super(type, world);
        setSilent(true); // sus sonidos los pone el mod
    }

    public static DefaultAttributeContainer.Builder createEchoAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 60.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(OWNER, Optional.empty());
        builder.add(VARIANT, V_NORMAL);
    }

    public UUID getOwnerUuid() { return dataTracker.get(OWNER).orElse(null); }
    public UUID getSkinUuid() { return getOwnerUuid(); }
    public int getVariant() { return dataTracker.get(VARIANT); }
    public Mode getMode() { return mode; }

    public void init(ServerPlayerEntity player, int variant, Mode mode) {
        dataTracker.set(OWNER, Optional.of(player.getUuid()));
        dataTracker.set(VARIANT, variant);
        setCustomNameVisible(false);
        if (variant == V_NORMAL) copyAppearance(player);
        setMode(mode);
    }

    private void copyAppearance(ServerPlayerEntity p) {
        for (EquipmentSlot s : EquipmentSlot.values()) {
            equipStack(s, p.getEquippedStack(s).copy());
            setEquipmentDropChance(s, 0.0f);
        }
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        switch (mode) {
            case STALK -> maxLife = Integer.MAX_VALUE;
            case GLIMPSE -> maxLife = 20 * 6;
            case JUMP -> maxLife = 40;
            case WATCH -> maxLife = 20 * (40 + random.nextInt(50));
            case WALK -> maxLife = 20 * 70;
            case RUSH -> {
                maxLife = 20 * 14;
                getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(0.40);
            }
            case SCREAM -> maxLife = 20 * 4;
            case FLY_WATCH -> { maxLife = 20 * 45; setNoGravity(true); }
            case FLY_SWOOP -> { maxLife = 20 * 12; setNoGravity(true); }
            case REGISTRAR -> maxLife = 20 * 240;
            case LURKER -> maxLife = 20 * 120;
        }
    }

    private ServerPlayerEntity getOwnerPlayer() {
        UUID id = getOwnerUuid();
        if (id == null || !(getWorld() instanceof ServerWorld sw)) return null;
        return sw.getServer().getPlayerManager().getPlayer(id);
    }

    public void vanish() {
        if (getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 1.0, getZ(), 25, 0.3, 0.6, 0.3, 0.05);
            sw.spawnParticles(ParticleTypes.ASH, getX(), getY() + 1.0, getZ(), 30, 0.3, 0.8, 0.3, 0.0);
        }
        discard();
    }

    private boolean isBeingLookedAt(ServerPlayerEntity p, double minDot) {
        Vec3d look = p.getRotationVec(1.0f).normalize();
        Vec3d to = getEyePos().subtract(p.getEyePos()).normalize();
        return look.dotProduct(to) > minDot && p.canSee(this);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        ServerWorld world = (ServerWorld) getWorld();

        life++;
        if (life > maxLife) {
            if (mode == Mode.REGISTRAR) discard(); else vanish();
            return;
        }
        ServerPlayerEntity owner = getOwnerPlayer();
        if (owner == null || owner.isRemoved() || !owner.isAlive() || owner.isSpectator() || owner.getServerWorld() != world) {
            discard();
            return;
        }
        if (mode != Mode.STALK && life % 20 == 0 && EchoManager.isProtected(world, getBlockPos())) {
            vanish();
            return;
        }

        double d2 = squaredDistanceTo(owner);

        switch (mode) {
            case WATCH -> {
                getNavigation().stop();
                getLookControl().lookAt(owner, 30.0f, 30.0f);
                if (d2 < 8.0 * 8.0) { vanish(); return; }
                tickSeen(owner, d2, 120);
            }
            case WALK -> {
                getLookControl().lookAt(owner, 30.0f, 30.0f);
                if (life % 10 == 0) getNavigation().startMovingTo(owner, 0.55);
                if (d2 < 7.0 * 7.0) { vanish(); return; }
                tickSeen(owner, d2, 100);
            }
            case STALK -> {
                getLookControl().lookAt(owner, 30.0f, 30.0f);
                boolean watched = isBeingLookedAt(owner, 0.55);
                if (d2 < 1.9 * 1.9) { EchoManager.caught(owner, this); return; }
                if (d2 < 14.0 * 14.0 && life % 40 == 0) {
                    world.playSound(null, getBlockPos(), SoundEvents.ENTITY_WARDEN_NEARBY_CLOSE, SoundCategory.HOSTILE, 0.8f, 0.8f);
                }
                if (watched) getNavigation().stop();
                else if (life % 10 == 0) getNavigation().startMovingTo(owner, 1.15);
            }
            case GLIMPSE -> {
                getNavigation().stop();
                getLookControl().lookAt(owner, 30.0f, 30.0f);
                if (d2 < 4.0 * 4.0) { vanish(); return; }
                if (life > 4 && isBeingLookedAt(owner, 0.82)) { vanish(); return; }
            }
            case JUMP -> {
                getNavigation().stop();
                getLookControl().lookAt(owner, 60.0f, 60.0f);
            }
            case RUSH -> {
                getLookControl().lookAt(owner, 60.0f, 60.0f);
                if (life % 8 == 0) getNavigation().startMovingTo(owner, 1.5);
                if (d2 < 1.9 * 1.9) { EchoManager.rushEnd(owner, this); return; }
            }
            case SCREAM -> {
                getNavigation().stop();
                getLookControl().lookAt(owner, 60.0f, 60.0f);
                if (life == 6) EchoManager.screamAt(owner, this);
                if (life > 55) { vanish(); return; }
            }
            case FLY_WATCH -> tickFlyWatch(owner, d2);
            case FLY_SWOOP -> tickFlySwoop(owner);
            case REGISTRAR -> tickRegistrar(owner, world, d2);
            case LURKER -> {
                getLookControl().lookAt(owner, 60.0f, 60.0f);
                if (!charging) {
                    getNavigation().stop();
                    if (d2 < 10.0 * 10.0) { vanish(); return; }
                    if (d2 < 64.0 * 64.0 && isBeingLookedAt(owner, 0.98)) {
                        gaze++;
                        if (gaze > 100) { // más de 5 s mirándolo: ¡corre!
                            charging = true;
                            maxLife = life + 20 * 20;
                            getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(0.45);
                            world.playSound(null, getBlockPos(), SoundEvents.ENTITY_GHAST_SCREAM, SoundCategory.HOSTILE, 1.5f, 0.5f);
                        }
                    } else if (gaze > 0) {
                        gaze = Math.max(0, gaze - 2);
                    }
                } else {
                    if (life % 4 == 0) getNavigation().startMovingTo(owner, 1.9);
                    if (d2 < 1.9 * 1.9) { EchoManager.rushEnd(owner, this); return; }
                }
            }
        }
    }

    private void tickSeen(ServerPlayerEntity owner, double d2, int limit) {
        if (d2 < 40.0 * 40.0 && isBeingLookedAt(owner, 0.97)) {
            seenTicks++;
            if (seenTicks > limit) vanish();
        } else if (seenTicks > 0) {
            seenTicks--;
        }
    }

    private void faceOwner(ServerPlayerEntity owner, boolean pitchToo) {
        double dx = owner.getX() - getX(), dz = owner.getZ() - getZ();
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx))) - 90f;
        setYaw(yaw);
        setBodyYaw(yaw);
        setHeadYaw(yaw);
        if (pitchToo) {
            double dy = owner.getEyeY() - getEyeY();
            setPitch((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        }
    }

    private void tickFlyWatch(ServerPlayerEntity owner, double d2) {
        faceOwner(owner, true);
        fallDistance = 0;
        orbit += 0.012;
        double r = 15.0;
        Vec3d target = new Vec3d(owner.getX() + Math.cos(orbit) * r, owner.getY() + 9.0, owner.getZ() + Math.sin(orbit) * r);
        Vec3d to = target.subtract(getPos());
        double len = to.length();
        setVelocity(len < 0.4 ? Vec3d.ZERO : to.multiply(1.0 / len).multiply(Math.min(0.22, len * 0.1)));
        tickSeen(owner, d2, 140);
        if (d2 < 6.0 * 6.0) vanish();
    }

    private void tickFlySwoop(ServerPlayerEntity owner) {
        faceOwner(owner, true);
        fallDistance = 0;
        if (life < 45) { // primero flota mirándote
            setVelocity(0, Math.sin(life * 0.2) * 0.02, 0);
            return;
        }
        Vec3d to = owner.getEyePos().subtract(getPos());
        double len = to.length();
        if (len < 2.8) { EchoManager.rushEnd(owner, this); return; }
        double speed = Math.min(0.85, 0.2 + (life - 45) * 0.02);
        setVelocity(to.multiply(1.0 / len).multiply(speed));
    }

    /** El Registrador: copia tu postura y tu cabeza. Si te quedas quieto, aparece un bloque más cerca. */
    private void tickRegistrar(ServerPlayerEntity owner, ServerWorld world, double d2) {
        faceOwner(owner, false);
        setPitch(owner.getPitch());
        setSneaking(owner.isSneaking());
        getNavigation().stop();

        Vec3d cur = owner.getPos();
        if (lastOwnerPos != null && cur.squaredDistanceTo(lastOwnerPos) < 0.01) stillTicks++;
        else stillTicks = 0;
        lastOwnerPos = cur;

        if (d2 < 9.0 * 9.0) { // demasiado cerca: se esfuma
            EchoManager.registrarGone(owner);
            discard();
            return;
        }
        if (stillTicks > 60 && stillTicks % 60 == 0 && !isBeingLookedAt(owner, 0.6)) {
            Vec3d to = owner.getPos().subtract(getPos()).normalize();
            double nx = getX() + to.x, nz = getZ() + to.z;
            refreshPositionAndAngles(nx, getY(), nz, getYaw(), getPitch());
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (!getWorld().isClient && source.getAttacker() instanceof PlayerEntity && mode != Mode.STALK) vanish();
        return false;
    }

    @Override public boolean shouldSave() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canImmediatelyDespawn(double d) { return false; }
}
