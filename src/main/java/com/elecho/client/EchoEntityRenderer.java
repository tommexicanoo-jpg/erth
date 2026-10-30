package com.elecho.client;

import com.elecho.ElEcho;
import com.elecho.entity.EchoEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.util.UUID;

/** Dibuja al Eco como un jugador: con tu skin, con la skin negra (Sombra) o blanca translúcida (Registrador). */
public class EchoEntityRenderer extends LivingEntityRenderer<EchoEntity, PlayerEntityModel<EchoEntity>> {
    private static final Identifier SHADOW = ElEcho.id("textures/entity/echo_shadow.png");
    private static final Identifier PALE = ElEcho.id("textures/entity/echo_pale.png");

    private final PlayerEntityModel<EchoEntity> wide;
    private final PlayerEntityModel<EchoEntity> slim;

    public EchoEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
        this.wide = this.model;
        this.slim = new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_SLIM), true);
        this.addFeature(new ArmorFeatureRenderer<>(this,
                new BipedEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_INNER_ARMOR)),
                new BipedEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()));
        this.addFeature(new HeldItemFeatureRenderer<>(this, ctx.getHeldItemRenderer()));
    }

    private static SkinTextures skinOf(EchoEntity e) {
        UUID id = e.getSkinUuid();
        if (id == null) return DefaultSkinHelper.getSkinTextures(e.getUuid());
        var nh = MinecraftClient.getInstance().getNetworkHandler();
        PlayerListEntry entry = nh == null ? null : nh.getPlayerListEntry(id);
        return entry != null ? entry.getSkinTextures() : DefaultSkinHelper.getSkinTextures(id);
    }

    @Override
    public void render(EchoEntity e, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vcp, int light) {
        boolean slimSkin = e.getVariant() == EchoEntity.V_NORMAL && skinOf(e).model() == SkinTextures.Model.SLIM;
        this.model = slimSkin ? slim : wide;
        this.model.sneaking = e.isInSneakingPose();
        this.model.leftArmPose = BipedEntityModel.ArmPose.EMPTY;
        this.model.rightArmPose = BipedEntityModel.ArmPose.EMPTY;
        this.model.setVisible(true);
        super.render(e, yaw, tickDelta, matrices, vcp, light);
    }

    @Override
    public Identifier getTexture(EchoEntity e) {
        return switch (e.getVariant()) {
            case EchoEntity.V_SHADOW -> SHADOW;
            case EchoEntity.V_PALE -> PALE;
            default -> skinOf(e).texture();
        };
    }

    @Override
    protected RenderLayer getRenderLayer(EchoEntity e, boolean showBody, boolean translucent, boolean showOutline) {
        return super.getRenderLayer(e, showBody, translucent || e.getVariant() == EchoEntity.V_PALE, showOutline);
    }

    @Override
    protected boolean hasLabel(EchoEntity e) { return false; }
}
