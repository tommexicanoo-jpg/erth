package com.elecho.mixin;

import com.elecho.client.ClientEco;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Niebla densa y color tétrico permanentes (funciona sin shaders). */
@Mixin(BackgroundRenderer.class)
public class BackgroundRendererMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private static void elecho$color(Camera camera, float tickDelta, ClientWorld world, int viewDistance,
                                    float skyDarkness, CallbackInfo ci) {
        if (!ClientEco.active || camera.getSubmersionType() != CameraSubmersionType.NONE) return;
        RenderSystem.clearColor(ClientEco.r, ClientEco.g, ClientEco.b, 0.0f);
        RenderSystem.setShaderFogColor(ClientEco.r, ClientEco.g, ClientEco.b);
    }

    @Inject(method = "applyFog", at = @At("TAIL"))
    private static void elecho$fog(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance,
                                  boolean thickFog, float tickDelta, CallbackInfo ci) {
        if (!ClientEco.active || camera.getSubmersionType() != CameraSubmersionType.NONE) return;
        if (camera.getFocusedEntity() instanceof LivingEntity le && le.hasStatusEffect(StatusEffects.BLINDNESS)) return;
        RenderSystem.setShaderFogColor(ClientEco.r, ClientEco.g, ClientEco.b);
        if (fogType == BackgroundRenderer.FogType.FOG_TERRAIN) {
            float end = Math.min(ClientEco.fogEnd, viewDistance);
            RenderSystem.setShaderFogStart(end * 0.04f);
            RenderSystem.setShaderFogEnd(end);
        } else {
            RenderSystem.setShaderFogStart(-8.0f);
            RenderSystem.setShaderFogEnd(Math.min(ClientEco.fogEnd, viewDistance) * 0.8f);
        }
    }
}
