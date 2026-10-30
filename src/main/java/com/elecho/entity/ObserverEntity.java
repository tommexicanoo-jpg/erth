package com.elecho.entity;

import com.elecho.EchoManager;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/**
 * El Observador de la Bruma. Días 1-4: acosador (se queda a distancia, solo se mueve cuando
 * no lo miras, huye si te acercas). Día 5: persecución activa.
 */
public class ObserverEntity extends HostileEntity {
    private int teleportCooldown = 0;
    private int attackCooldown = 0;

    public ObserverEntity(EntityType<? extends ObserverEntity> type, World world) {
        super(type, world);
        setPersistent();
    }

    public static DefaultAttributeContainer.Builder createObserverAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 80.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 110.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 7.0)
                .add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.0);
    }

    /** Desde el punto de vista del jugador: ¿está mirando hacia esta entidad? */
    public boolean isWatchedBy(PlayerEntity p) {
        Vec3d toMe = getEyePos().subtract(p.getEyePos()).normalize();
        return p.getRotationVec(1.0f).dotProduct(toMe) > 0.93 && p.canSee(this);
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (!(getWorld() instanceof ServerWorld sw)) return;
        if (teleportCooldown > 0) teleportCooldown--;
        if (attackCooldown > 0) attackCooldown--;

        PlayerEntity p = sw.getClosestPlayer(this, 140.0);
        if (p == null || p.isSpectator() || !p.isAlive()) { getNavigation().stop(); return; }
        double dist = distanceTo(p);
        double par = EchoManager.paranoiaOf(p);
        boolean apoc = EchoManager.isApocalypse(sw);
        getLookControl().lookAt(p, 40.0f, 40.0f);

        if (apoc) {                           // ---------- NOCHE 5: persecución ----------
            setTarget(p);
            if (dist > 75.0 && teleportCooldown == 0) { relocate(sw, p, 38.0); return; }
            getNavigation().startMovingTo(p, 1.45);
            if (dist < 2.4 && attackCooldown == 0) {
                if (tryAttack(p)) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 160, 0));
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1));
                }
                attackCooldown = 20;
            }
            return;
        }
        if (par < 8.0) { discard(); return; } // sin paranoia, la bruma no te ve
        setTarget(null);                      // ---------- acosador: más cerca cuanta más paranoia ----------
        double want = Math.max(24.0, 64.0 - par * 0.42);
        boolean watched = isWatchedBy(p);
        if (dist < Math.max(14.0, want * 0.4) && teleportCooldown == 0) {
            relocate(sw, p, want + 6.0);       // se aleja si te acercas demasiado
        } else if (watched) {
            getNavigation().stop();             // te mira, se queda quieto (ángel llorón)
        } else if (dist > want + 6.0) {
            getNavigation().startMovingTo(p, 0.9);
        } else if (dist < want - 6.0) {
            Vec3d away = getPos().subtract(p.getPos()).normalize().multiply(8.0).add(getPos());
            getNavigation().startMovingTo(away.x, getY(), away.z, 0.8);
        } else {
            getNavigation().stop();
        }
    }

    /** Se desvanece y reaparece a 'distance' bloques del jugador, detrás de su campo de visión. */
    public void relocate(ServerWorld sw, PlayerEntity p, double distance) {
        for (int i = 0; i < 12; i++) {
            double ang = Math.toRadians(p.getYaw() + 180.0 + (random.nextDouble() - 0.5) * 140.0);
            double x = p.getX() - Math.sin(ang) * distance;
            double z = p.getZ() + Math.cos(ang) * distance;
            BlockPos col = BlockPos.ofFloored(x, 0, z);
            if (!sw.isChunkLoaded(col)) continue;
            int y = sw.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, col.getX(), col.getZ());
            if (!sw.getBlockState(new BlockPos(col.getX(), y - 1, col.getZ())).isSolid()) continue;
            refreshPositionAndAngles(x, y, z, getYaw(), 0.0f);
            getNavigation().stop();
            teleportCooldown = 200;
            return;
        }
        teleportCooldown = 40;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.damage(source, amount);
        if (!getWorld().isClient && source.getAttacker() instanceof PlayerEntity p && !EchoManager.isApocalypse((ServerWorld) getWorld())) {
            relocate((ServerWorld) getWorld(), p, 60.0);
        }
        return false; // inmortal: solo se le puede evitar
    }

    @Override public boolean isFireImmune() { return true; }
    @Override public boolean canImmediatelyDespawn(double distanceSquared) { return false; }
    @Override public void checkDespawn() { /* persistente */ }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { /* silencioso */ }
    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override public boolean isPushable() { return false; }
}
