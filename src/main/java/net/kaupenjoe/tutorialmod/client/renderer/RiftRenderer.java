
package net.kaupenjoe.tutorialmod.client.renderer;

import net.kaupenjoe.tutorialmod.entity.custom.RiftEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

public class RiftRenderer extends EntityRenderer<RiftEntity> {

    public RiftRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(
            RiftEntity entity,
            float entityYaw,
            float partialTick,
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            net.minecraft.client.renderer.MultiBufferSource buffer,
            int packedLight
    ) {
        // The rift is rendered after translucent blocks instead.
    }

    @Override
    public ResourceLocation getTextureLocation(RiftEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
