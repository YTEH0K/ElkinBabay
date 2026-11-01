package net.kaupenjoe.tutorialmod.client.renderer;

import net.kaupenjoe.tutorialmod.entity.custom.AggressiveRiftEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

public class AggressiveRiftRenderer extends EntityRenderer<AggressiveRiftEntity> {

    public AggressiveRiftRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(
            AggressiveRiftEntity entity,
            float entityYaw,
            float partialTick,
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            net.minecraft.client.renderer.MultiBufferSource buffer,
            int packedLight
    ) {
    }

    @Override
    public ResourceLocation getTextureLocation(AggressiveRiftEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}