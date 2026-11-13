
package net.kaupenjoe.tutorialmod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.kaupenjoe.tutorialmod.TutorialMod;
import net.kaupenjoe.tutorialmod.entity.custom.RiftEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(
        modid = TutorialMod.MOD_ID,
        bus = EventBusSubscriber.Bus.GAME,
        value = Dist.CLIENT
)
public final class RiftAfterWaterRenderer {

    private static final int SEGMENTS = 22;
    private static final int SIDES = 12;

    private static final Map<UUID, RiftGeometry> GEOMETRY_CACHE =
            new HashMap<>();

    private static ClientLevel cachedLevel;

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

        if (cachedLevel != minecraft.level) {
            GEOMETRY_CACHE.clear();
            cachedLevel = minecraft.level;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();

        float partialTick =
                event.getPartialTick().getGameTimeDeltaPartialTick(false);

        RenderType renderType = RenderType.endPortal();

        MultiBufferSource.BufferSource buffers =
                minecraft.renderBuffers().bufferSource();

        VertexConsumer vertices = buffers.getBuffer(renderType);

        Set<UUID> activeRifts = new HashSet<>();

        for (var entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof RiftEntity rift) || !rift.isAlive()) {
                continue;
            }

            UUID uuid = rift.getUUID();
            activeRifts.add(uuid);

            if (rift.distanceToSqr(
                    cameraPos.x,
                    cameraPos.y,
                    cameraPos.z
            ) > 256.0 * 256.0) {
                continue;
            }

            RiftGeometry geometry = GEOMETRY_CACHE.computeIfAbsent(
                    uuid,
                    id -> generateGeometry(id, rift.getBbWidth())
            );

            double x = Mth.lerp(partialTick, rift.xo, rift.getX());
            double y = Mth.lerp(partialTick, rift.yo, rift.getY());
            double z = Mth.lerp(partialTick, rift.zo, rift.getZ());

            poseStack.pushPose();

            poseStack.translate(
                    x - cameraPos.x,
                    y - cameraPos.y,
                    z - cameraPos.z
            );

            poseStack.translate(
                    0.0D,
                    rift.getBbHeight() / 2.0D,
                    0.0D
            );

            float age = rift.tickCount + partialTick;

            float pulse =
                    1.0F + Mth.sin(age * 0.08F) * 0.035F;

            float height = rift.getBbHeight() * 0.92F;

            float rotation =
                    age * 0.28F + geometry.rotationOffset;

            poseStack.mulPose(
                    Axis.YP.rotationDegrees(rotation)
            );

            drawShell(
                    poseStack,
                    vertices,
                    geometry,
                    height,
                    pulse,
                    age,
                    1.0F
            );

            poseStack.popPose();
        }

        GEOMETRY_CACHE.keySet().retainAll(activeRifts);

        buffers.endBatch(renderType);
    }

    private static RiftGeometry generateGeometry(
            UUID uuid,
            float entityWidth
    ) {
        long seed =
                uuid.getMostSignificantBits()
                        ^ Long.rotateLeft(uuid.getLeastSignificantBits(), 23);

        Random random = new Random(seed);

        RiftGeometry geometry = new RiftGeometry();

        float driftX = 0.0F;
        float driftZ = 0.0F;

        float phase = random.nextFloat() * Mth.TWO_PI;

        float baseRadius = entityWidth * 0.33F;

        for (int i = 0; i < SEGMENTS; i++) {
            float t = i / (float) (SEGMENTS - 1);

            float envelope = Mth.sin(t * Mth.PI);

            if (i > 0) {
                driftX += (float) random.nextGaussian() * 0.13F;
                driftZ += (float) random.nextGaussian() * 0.13F;

                driftX = Mth.clamp(driftX, -0.55F, 0.55F);
                driftZ = Mth.clamp(driftZ, -0.55F, 0.55F);
            }

            float waveX =
                    Mth.sin(t * Mth.PI * 2.0F + phase) * 0.10F;

            float waveZ =
                    Mth.cos(t * Mth.PI * 2.0F + phase) * 0.10F;

            geometry.centerX[i] =
                    (driftX + waveX) * envelope;

            geometry.centerZ[i] =
                    (driftZ + waveZ) * envelope;

            float widthVariation =
                    0.82F + random.nextFloat() * 0.36F;

            geometry.radius[i] =
                    baseRadius
                            * (float) Math.pow(
                            Math.max(0.0F, envelope),
                            0.82F
                    )
                            * widthVariation;
        }

        geometry.rotationOffset = random.nextFloat() * 360.0F;
        geometry.phase = phase;

        return geometry;
    }

    private static void drawShell(
            PoseStack poseStack,
            VertexConsumer vertices,
            RiftGeometry geometry,
            float height,
            float pulse,
            float age,
            float radiusScale
    ) {
        PoseStack.Pose pose = poseStack.last();

        for (int ring = 0; ring < SEGMENTS - 1; ring++) {
            for (int side = 0; side < SIDES; side++) {

                Point a = getPoint(
                        geometry, ring, side,
                        height, pulse, age, radiusScale
                );

                Point b = getPoint(
                        geometry, ring, side + 1,
                        height, pulse, age, radiusScale
                );

                Point c = getPoint(
                        geometry, ring + 1, side + 1,
                        height, pulse, age, radiusScale
                );

                Point d = getPoint(
                        geometry, ring + 1, side,
                        height, pulse, age, radiusScale
                );

                // Передняя сторона
                emitQuad(vertices, pose, a, b, c, d);

                // Обратная сторона
                emitQuad(vertices, pose, d, c, b, a);
            }
        }
    }

    private static Point getPoint(
            RiftGeometry geometry,
            int ring,
            int side,
            float height,
            float pulse,
            float age,
            float radiusScale
    ) {
        float t = ring / (float) (SEGMENTS - 1);
        float sideFraction = side / (float) SIDES;

        float angle =
                sideFraction * Mth.TWO_PI
                        + ring * 0.16F
                        + age * 0.004F
                        + geometry.phase;

        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);

        float radius =
                geometry.radius[ring]
                        * radiusScale
                        * pulse;

        float x =
                geometry.centerX[ring] * pulse
                        + cos * radius;

        float y =
                (t - 0.5F) * height * pulse;

        float z =
                geometry.centerZ[ring] * pulse
                        + sin * radius;

        return new Point(x, y, z);
    }

    private static void emitQuad(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Point a,
            Point b,
            Point c,
            Point d
    ) {
        // End Portal RenderType использует только координаты вершин.
        vertices.addVertex(pose, a.x, a.y, a.z);
        vertices.addVertex(pose, b.x, b.y, b.z);
        vertices.addVertex(pose, c.x, c.y, c.z);
        vertices.addVertex(pose, d.x, d.y, d.z);
    }

    private static final class RiftGeometry {
        private final float[] centerX = new float[SEGMENTS];
        private final float[] centerZ = new float[SEGMENTS];
        private final float[] radius = new float[SEGMENTS];

        private float rotationOffset;
        private float phase;
    }

    private static final class Point {
        private final float x;
        private final float y;
        private final float z;

        private Point(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
