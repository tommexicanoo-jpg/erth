package com.elecho.client;

import com.elecho.ModBlocks;
import com.elecho.ModEntities;
import com.elecho.ModParticles;
import com.elecho.net.DreadPayload;
import com.elecho.net.TimerPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Random;

@Environment(EnvType.CLIENT)
public class ElEchoClient implements ClientModInitializer {
    private static final String SHADER_RES = "assets/elecho/shaderpack/ElEchoShaders.zip";
    private static final Random R = new Random();
    private static final String[] WHISPERS = {"NO MIRES", "ATRÁS", "TE VEO", "SIGO AQUÍ", "NO ESTÁS SOLO", "DUERME", "MÍRAME", "BÓRRALO"};

    private static boolean hud = false;
    private static float shownPar = 0f;
    private static int timer = -1;
    private static int flash = 0, flashMax = 1, flashKind = 1;
    private static int whisper = 0, whisperIdx = 0;
    private static float whisperX = 0.2f, whisperY = 0.3f;
    private static int tickCount = 0;

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.ECHO, EchoEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.OBSERVER, ObserverRenderer::new);
        ParticleFactoryRegistry.getInstance().register(ModParticles.EYES, EyesParticle.Factory::new);
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.ECHO_MIRROR, RenderLayer.getTranslucent());
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(), ModBlocks.EXTINGUISHED_TORCH, ModBlocks.EXTINGUISHED_WALL_TORCH);

        ClientPlayNetworking.registerGlobalReceiver(TimerPayload.ID, (payload, ctx) -> timer = payload.seconds());
        ClientPlayNetworking.registerGlobalReceiver(DreadPayload.ID, (payload, ctx) -> {
            if (payload.hud() >= 0) hud = payload.hud() == 1;
            if (payload.dread() >= 0) ClientEco.dread = payload.dread();
            if (payload.paranoia() >= 0) ClientEco.paranoia = payload.paranoia();
            if (payload.day() >= 0) ClientEco.night = payload.day();
            if (payload.stage() >= 0) ClientEco.stage = payload.stage();
            if (payload.hud() >= 0 && payload.sleepStage() >= 0) ClientEco.sleepStage = payload.sleepStage();
            int s = payload.scareLevel();
            if (s > 0) {
                flashKind = s;
                flashMax = s >= 2 ? 22 : 8;
                flash = flashMax;
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientEco.reset();
            hud = false;
            timer = -1;
        });
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            ClientEco.tick();
            tickCount++;
            if (flash > 0) flash--;
            if (hud && ClientEco.paranoia > 55 && whisper <= 0 && R.nextInt(400) < 1 + ClientEco.paranoia / 25) {
                whisper = 25 + R.nextInt(20);
                whisperIdx = R.nextInt(WHISPERS.length);
                whisperX = 0.1f + R.nextFloat() * 0.7f;
                whisperY = 0.15f + R.nextFloat() * 0.6f;
            }
            if (whisper > 0) whisper--;
        });
        HudRenderCallback.EVENT.register(ElEchoClient::renderHud);

        // Shaderpack OPCIONAL: /ecoshader install copia el zip a la carpeta shaderpacks (requiere Iris/OptiFine)
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("ecoshader")
                        .then(ClientCommandManager.literal("install").executes(c -> {
                            try {
                                Path dir = FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
                                Files.createDirectories(dir);
                                Path out = dir.resolve("ElEcho-Shaders.zip");
                                var path = FabricLoader.getInstance().getModContainer("elecho").orElseThrow().findPath(SHADER_RES);
                                try (InputStream in = Files.newInputStream(path.orElseThrow())) {
                                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                                }
                                c.getSource().sendFeedback(Text.translatable("elecho.shader.copied", out.toString()));
                                return 1;
                            } catch (Exception e) {
                                c.getSource().sendFeedback(Text.translatable("elecho.shader.error", e.toString()));
                                return 0;
                            }
                        }))
                        .then(ClientCommandManager.literal("status").executes(c -> {
                            c.getSource().sendFeedback(Text.translatable("elecho.shader.status", ClientEco.paranoia));
                            return 1;
                        }))));
    }

    private static void renderHud(DrawContext ctx, net.minecraft.client.render.RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        TextRenderer tr = mc.textRenderer;

        // tinte sepia de la corrupción
        if (hud && ClientEco.stage >= 2) {
            int a = ClientEco.stage >= 3 ? 40 : 22;
            ctx.fill(0, 0, w, h, (a << 24) | 0x5A3A10);
        }
        // viñeta de paranoia
        if (hud && ClientEco.paranoia > 40) {
            int a = Math.min(70, (ClientEco.paranoia - 40));
            ctx.fill(0, 0, w, h, (a << 24) | 0x100000);
        }

        if (hud) {
            shownPar += (ClientEco.paranoia - shownPar) * 0.08f;
            int bw = 90, x = w - bw - 8, y = 8;
            String night = "N O C H E  " + ClientEco.night;
            ctx.drawTextWithShadow(tr, night, w - 8 - tr.getWidth(night), y, 0xFFB8B0A0);
            y += 12;
            ctx.drawTextWithShadow(tr, "PARANOIA", w - 8 - tr.getWidth("PARANOIA"), y, 0xFF9A8F80);
            y += 11;
            ctx.fill(x - 1, y - 1, x + bw + 1, y + 6, 0xAA000000);
            int fill = (int) (bw * Math.min(100f, shownPar) / 100f);
            int red = (int) (80 + 1.7f * shownPar);
            ctx.fill(x, y, x + fill, y + 5, 0xFF000000 | (Math.min(255, red) << 16) | (0x30 << 8) | 0x30);

            if (ClientEco.sleepStage > 0) {
                boolean blink = ClientEco.sleepStage < 3 || (tickCount / 10) % 2 == 0;
                if (blink) {
                    String z = ClientEco.sleepStage >= 3 ? "zZz  NECESITAS DORMIR YA" : "zZz  necesitas dormir";
                    int col = ClientEco.sleepStage >= 3 ? 0xFFFF4040 : ClientEco.sleepStage == 2 ? 0xFFE0A050 : 0xFF9AA0B0;
                    ctx.drawTextWithShadow(tr, z, w - 8 - tr.getWidth(z), y + 10, col);
                }
            }
        }
        if (timer >= 0) {
            String t = "EL MUNDO TE OLVIDA  0:" + (timer < 10 ? "0" : "") + timer;
            ctx.drawTextWithShadow(tr, t, (w - tr.getWidth(t)) / 2, 22, 0xFFFF3030);
        }
        if (whisper > 0 && hud) {
            int a = Math.min(200, whisper * 12);
            ctx.drawTextWithShadow(tr, WHISPERS[whisperIdx], (int) (w * whisperX), (int) (h * whisperY), (a << 24) | 0xAA1010);
        }
        if (flash > 0) {
            float f = flash / (float) flashMax;
            int a = (int) (f * (flashKind >= 2 ? 235 : 120));
            ctx.fill(0, 0, w, h, (a << 24) | (flashKind >= 2 && flash > flashMax / 2 ? 0x000000 : 0x600000));
        }
    }
}
