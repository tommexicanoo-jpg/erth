package com.elecho.client;

import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteProvider;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;

/** Ojos rojos brillantes: se quedan pegados a la superficie, se apagan al acercarte. */
public class EyesParticle extends SpriteBillboardParticle {
    private final int fadeIn = 20;

    EyesParticle(ClientWorld world, double x, double y, double z, FabricSpriteProvider sprites) {
        super(world, x, y, z);
        this.setSprite(sprites.getSprite(this.random));
        this.velocityX = 0; this.velocityY = 0; this.velocityZ = 0;
        this.gravityStrength = 0f;
        this.collidesWithWorld = false;
        this.scale = 0.28f + this.random.nextFloat() * 0.22f;
        this.maxAge = 100 + this.random.nextInt(160);
        this.alpha = 0f;
    }

    @Override
    public void tick() {
        this.prevPosX = x; this.prevPosY = y; this.prevPosZ = z;
        if (this.age++ >= this.maxAge) { this.markDead(); return; }
        var p = MinecraftClient.getInstance().player;
        double near = 1.0;
        if (p != null) {
            double d = p.squaredDistanceTo(x, y, z);
            if (d < 36.0) { near = 0.0; this.markDead(); }          // a menos de 6 bloques desaparecen
            else if (d < 100.0) near = (Math.sqrt(d) - 6.0) / 4.0;
        }
        float in = Math.min(1f, age / (float) fadeIn);
        float out = Math.min(1f, (maxAge - age) / 20f);
        // parpadeo ocasional
        float blink = (age % 70 > 62) ? 0.0f : 1.0f;
        this.alpha = (float) (in * out * near * blink);
    }

    @Override
    public ParticleTextureSheet getType() { return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT; }

    @Override
    public int getBrightness(float tint) { return 0xF000F0; }   // brillo máximo (emisivo)

    public static class Factory implements ParticleFactory<SimpleParticleType> {
        private final FabricSpriteProvider sprites;
        public Factory(FabricSpriteProvider sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientWorld world, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new EyesParticle(world, x, y, z, sprites);
        }
    }
}
