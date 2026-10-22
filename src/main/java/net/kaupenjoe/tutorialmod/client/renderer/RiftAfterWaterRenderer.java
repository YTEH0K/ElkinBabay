
package net.kaupenjoe.tutorialmod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.kaupenjoe.tutorialmod.TutorialMod;
import net.kaupenjoe.tutorialmod.entity.custom.RiftEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.NeoForgeRenderTypes;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(
        modid = TutorialMod.MOD_ID,
        bus = EventBusSubscriber.Bus.GAME,
        value = Dist.CLIENT
)
public final class RiftAfterWaterRenderer {

    private static final ResourceLocation PORTAL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    "minecraft",
                    "block/nether_portal"
            );

    private RiftAfterWaterRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null || event.getPoseStack() == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();

        float partialTick =
                event.getPartialTick().getGameTimeDeltaPartialTick(false);

        TextureAtlasSprite sprite = minecraft.getTextureAtlas(
                TextureAtlas.LOCATION_BLOCKS
        ).apply(PORTAL_TEXTURE);

        float u0 = sprite.getU(0.01F);
        float u1 = sprite.getU(0.99F);
        float v0 = sprite.getV(0.01F);
        float v1 = sprite.getV(0.99F);

        RenderType renderType =
                NeoForgeRenderTypes.getTranslucentParticlesTarget(
                        TextureAtlas.LOCATION_BLOCKS
                );

        MultiBufferSource.BufferSource buffers =
                minecraft.renderBuffers().bufferSource();

        VertexConsumer vertices = buffers.getBuffer(renderType);

        for (var entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof RiftEntity rift) || !rift.isAlive()) {
                continue;
            }

            if (rift.distanceToSqr(
                    cameraPos.x,
                    cameraPos.y,
                    cameraPos.z
            ) > 256.0 * 256.0) {
                continue;
            }

            double x = Mth.lerp(partialTick, rift.xo, rift.getX());
            double y = Mth.lerp(partialTick, rift.yo, rift.getY());
            double z = Mth.lerp(partialTick, rift.zo, rift.getZ());

            poseStack.pushPose();

            poseStack.translate(
                    x - cameraPos.x,
                    y - cameraPos.y,
                    z - cameraPos.z
            );

            poseStack.translate(0.0D, rift.getBbHeight() / 2.0D, 0.0D);

            float age = rift.tickCount + partialTick;
            float pulse = 1.0F + Mth.sin(age * 0.12F) * 0.04F;
            float width = rift.getBbWidth() * pulse;
            float height = rift.getBbHeight() * pulse;
            float rotation = age * 1.5F;

            int[][] colors = {
                    {255, 225, 255, 235},
                    {190, 85, 255, 220},
                    {100, 205, 255, 210}
            };

            for (int i = 0; i < colors.length; i++) {
                poseStack.pushPose();

                poseStack.mulPose(
                        Axis.YP.rotationDegrees(rotation + i * 60.0F)
                );

                drawPlane(
                        poseStack,
                        vertices,
                        width,
                        height,
                        u0,
                        u1,
                        v0,
                        v1,
                        colors[i]
                );

                poseStack.popPose();
            }

            poseStack.popPose();
        }

        buffers.endBatch(renderType);
    }

    private static void drawPlane(
            PoseStack poseStack,
            VertexConsumer vertices,
            float width,
            float height,
            float u0,
            float u1,
            float v0,
            float v1,
            int[] color
    ) {
        PoseStack.Pose pose = poseStack.last();

        float halfWidth = width / 2.0F;
        float halfHeight = height / 2.0F;

        vertex(vertices, pose, -halfWidth, -halfHeight, 0, u0, v1, 1, color);
        vertex(vertices, pose, -halfWidth, halfHeight, 0, u0, v0, 1, color);
        vertex(vertices, pose, halfWidth, halfHeight, 0, u1, v0, 1, color);
        vertex(vertices, pose, halfWidth, -halfHeight, 0, u1, v1, 1, color);

        vertex(vertices, pose, halfWidth, -halfHeight, 0, u1, v1, -1, color);
        vertex(vertices, pose, halfWidth, halfHeight, 0, u1, v0, -1, color);
        vertex(vertices, pose, -halfWidth, halfHeight, 0, u0, v0, -1, color);
        vertex(vertices, pose, -halfWidth, -halfHeight, 0, u0, v1, -1, color);
    }

    private static void vertex(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            float x,
            float y,
            float z,
            float u,
            float v,
            float normalZ,
            int[] color
    ) {
        vertices.addVertex(pose, x, y, z)
                .setColor(color[0], color[1], color[2], color[3])
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, normalZ);
    }
}
