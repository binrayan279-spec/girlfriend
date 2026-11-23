package com.girlfriend;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.Identifier;

public class GirlfriendRenderer extends MobEntityRenderer<GirlfriendEntity, PlayerEntityRenderState, PlayerEntityModel> {
    public GirlfriendRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, makeModel(ctx), 0.5f);
    }

    private static PlayerEntityModel makeModel(EntityRendererFactory.Context ctx) {
        boolean slim = GirlfriendConfig.get().slimArms;
        return new PlayerEntityModel(ctx.getPart(slim ? EntityModelLayers.PLAYER_SLIM : EntityModelLayers.PLAYER), slim);
    }

    @Override
    public PlayerEntityRenderState createRenderState() {
        return new PlayerEntityRenderState();
    }

    @Override
    public void updateRenderState(GirlfriendEntity entity, PlayerEntityRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        // show the skin's second layer (hat, jacket, sleeves, pants)
        state.hatVisible = true;
        state.jacketVisible = true;
        state.leftSleeveVisible = true;
        state.rightSleeveVisible = true;
        state.leftPantsLegVisible = true;
        state.rightPantsLegVisible = true;
    }

    @Override
    public Identifier getTexture(PlayerEntityRenderState state) {
        return GirlfriendSkin.texture();
    }
}
