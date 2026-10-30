package com.elecho.client;

import com.elecho.entity.ObserverEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EndermanEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.util.Identifier;

/** Silueta alta y negra con decenas de ojos rojos emisivos. */
public class ObserverRenderer extends MobEntityRenderer<ObserverEntity, EndermanEntityModel<ObserverEntity>> {
    private static final Identifier TEX = Identifier.of("elecho", "textures/entity/observer.png");
    private static final Identifier EYES = Identifier.of("elecho", "textures/entity/observer_eyes.png");

    public ObserverRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new EndermanEntityModel<>(ctx.getPart(EntityModelLayers.ENDERMAN)), 0.0f);
        this.addFeature(new Eyes(this));
    }

    @Override
    public Identifier getTexture(ObserverEntity entity) { return TEX; }

    private static class Eyes extends EyesFeatureRenderer<ObserverEntity, EndermanEntityModel<ObserverEntity>> {
        Eyes(FeatureRendererContext<ObserverEntity, EndermanEntityModel<ObserverEntity>> ctx) { super(ctx); }

        @Override
        public RenderLayer getEyesTexture() { return RenderLayer.getEyes(EYES); }
    }
}
