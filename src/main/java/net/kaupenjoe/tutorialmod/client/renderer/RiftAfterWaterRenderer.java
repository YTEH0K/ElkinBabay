
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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

    private static final int SEGMENTS = 30;
    private static final int SIDES = 12;
    private static final int BRANCH_SEGMENTS = 14;
    private static final int BRANCH_SIDES = 8;

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
                    id -> generateGeometry(
                            id,
                            rift.getBbWidth(),
                            rift.getBbHeight()
                    )
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

            float rotation =
                    geometry.rotationOffset + age * 0.10F;

            poseStack.mulPose(
                    Axis.YP.rotationDegrees(rotation)
            );

            // Основное тело разлома.
            drawPath(
                    poseStack,
                    vertices,
                    geometry.mainPath,
                    SIDES,
                    pulse,
                    age
            );

            // Дополнительные случайные ответвления.
            for (RiftPath branch : geometry.branches) {
                drawPath(
                        poseStack,
                        vertices,
                        branch,
                        BRANCH_SIDES,
                        pulse,
                        age
                );
            }

            poseStack.popPose();
        }

        GEOMETRY_CACHE.keySet().retainAll(activeRifts);

        buffers.endBatch(renderType);
    }

    private static RiftGeometry generateGeometry(
            UUID uuid,
            float entityWidth,
            float entityHeight
    ) {
        long seed =
                uuid.getMostSignificantBits()
                        ^ Long.rotateLeft(
                        uuid.getLeastSignificantBits(),
                        23
                );

        Random random = new Random(seed);

        float maxX = entityWidth * 0.31F;
        float maxY = entityHeight * 0.34F;
        float maxZ = entityWidth * 0.31F;

        RiftGeometry geometry = new RiftGeometry();

        geometry.phase = random.nextFloat() * Mth.TWO_PI;
        geometry.rotationOffset = random.nextFloat() * 360.0F;

        float maxRadius = Math.min(0.44F, entityWidth * 0.145F);

        Vec3[] mainControls = generateMainControls(
                random,
                maxX,
                maxY,
                maxZ
        );

        geometry.mainPath = createPath(
                random,
                mainControls,
                SEGMENTS,
                0.13F,
                maxRadius,
                false
        );

        // Ответвления появляются не у каждого разлома.
        if (random.nextFloat() < 0.65F) {
            int branchCount = 1 + random.nextInt(2);

            for (int i = 0; i < branchCount; i++) {
                Vec3[] branchControls = generateBranchControls(
                        random,
                        geometry.mainPath,
                        maxX,
                        maxY,
                        maxZ
                );

                RiftPath branch = createPath(
                        random,
                        branchControls,
                        BRANCH_SEGMENTS,
                        0.035F,
                        0.19F,
                        true
                );

                geometry.branches.add(branch);
            }
        }

        return geometry;
    }

    private static Vec3[] generateMainControls(
            Random random,
            float maxX,
            float maxY,
            float maxZ
    ) {
        int controlCount = 6;

        Vec3[] controls = new Vec3[controlCount];

        Vec3 start = randomPoint(random, maxX, maxY, maxZ);
        Vec3 end = randomPoint(random, maxX, maxY, maxZ);

        for (int attempt = 0;
             attempt < 5 && start.distanceToSqr(end) < 0.8;
             attempt++) {

            end = randomPoint(random, maxX, maxY, maxZ);
        }

        controls[0] = start;
        controls[controlCount - 1] = end;

        for (int i = 1; i < controlCount - 1; i++) {
            double t = i / (double) (controlCount - 1);

            Vec3 base = start.lerp(end, t);

            double spread = Math.sin(t * Math.PI) * 0.65;

            Vec3 offset = new Vec3(
                    random.nextGaussian() * spread,
                    random.nextGaussian() * spread,
                    random.nextGaussian() * spread
            );

            controls[i] = clampPoint(
                    base.add(offset),
                    maxX,
                    maxY,
                    maxZ
            );
        }

        return controls;
    }

    private static Vec3[] generateBranchControls(
            Random random,
            RiftPath mainPath,
            float maxX,
            float maxY,
            float maxZ
    ) {
        int attachIndex = 5 + random.nextInt(
                mainPath.centers.length - 10
        );

        Vec3 start = mainPath.centers[attachIndex];

        Vec3 tangent = getTangent(mainPath.centers, attachIndex);

        Vec3 direction = randomDirection(random);

        direction = direction.subtract(
                tangent.scale(direction.dot(tangent))
        );

        if (direction.lengthSqr() < 0.001D) {
            direction = tangent.cross(new Vec3(0, 1, 0));

            if (direction.lengthSqr() < 0.001D) {
                direction = tangent.cross(new Vec3(1, 0, 0));
            }
        }

        direction = direction.normalize();

        if (random.nextBoolean()) {
            direction = direction.scale(-1.0D);
        }

        double length = 0.55D + random.nextDouble() * 0.35D;

        Vec3 middle = start
                .add(direction.scale(length * 0.55D))
                .add(tangent.scale(
                        (random.nextDouble() - 0.5D) * 0.4D
                ));

        Vec3 end = start
                .add(direction.scale(length))
                .add(tangent.scale(
                        (random.nextDouble() - 0.5D) * 0.6D
                ))
                .add(new Vec3(
                        random.nextGaussian() * 0.15D,
                        random.nextGaussian() * 0.15D,
                        random.nextGaussian() * 0.15D
                ));

        Vec3 firstMiddle = start.lerp(middle, 0.45D);

        return new Vec3[]{
                start,
                clampPoint(firstMiddle, maxX, maxY, maxZ),
                clampPoint(middle, maxX, maxY, maxZ),
                clampPoint(end, maxX, maxY, maxZ)
        };
    }

    private static RiftPath createPath(
            Random random,
            Vec3[] controls,
            int segments,
            float minRadius,
            float maxRadius,
            boolean branch
    ) {
        RiftPath path = new RiftPath(segments);

        float[] radiusControls = new float[controls.length];

        for (int i = 0; i < radiusControls.length; i++) {
            float radius = minRadius
                    + random.nextFloat() * (maxRadius - minRadius);

            if (i == 0 || i == radiusControls.length - 1) {
                radius *= 0.45F + random.nextFloat() * 0.2F;
            }

            radiusControls[i] = radius;
        }

        for (int i = 0; i < segments; i++) {
            float t = i / (float) (segments - 1);

            path.centers[i] = sampleCurve(controls, t);

            float radius = sampleScalarCurve(radiusControls, t);

            float taper;

            if (branch) {
                taper = 1.0F - t * 0.65F;
            } else {
                taper = 0.35F + 0.65F * Mth.sin(t * Mth.PI);
            }

            path.radii[i] = Math.max(
                    minRadius * 0.35F,
                    Math.min(maxRadius, radius * taper)
            );
        }

        path.twistOffset = random.nextFloat() * Mth.TWO_PI;

        buildFrames(path);

        return path;
    }

    private static Vec3 sampleCurve(Vec3[] controls, double t) {
        double position = t * (controls.length - 1);

        int segment = Math.min(
                (int) Math.floor(position),
                controls.length - 2
        );

        double localT = position - segment;

        Vec3 p0 = controls[Math.max(0, segment - 1)];
        Vec3 p1 = controls[segment];
        Vec3 p2 = controls[segment + 1];
        Vec3 p3 = controls[
                Math.min(controls.length - 1, segment + 2)
                ];

        return catmullRom(p0, p1, p2, p3, localT);
    }

    private static float sampleScalarCurve(float[] controls, float t) {
        float position = t * (controls.length - 1);

        int segment = Math.min(
                (int) Math.floor(position),
                controls.length - 2
        );

        float localT = position - segment;

        float p0 = controls[Math.max(0, segment - 1)];
        float p1 = controls[segment];
        float p2 = controls[segment + 1];
        float p3 = controls[
                Math.min(controls.length - 1, segment + 2)
                ];

        float t2 = localT * localT;
        float t3 = t2 * localT;

        return 0.5F * (
                2.0F * p1
                        + (-p0 + p2) * localT
                        + (2.0F * p0 - 5.0F * p1
                        + 4.0F * p2 - p3) * t2
                        + (-p0 + 3.0F * p1
                        - 3.0F * p2 + p3) * t3
        );
    }

    private static Vec3 catmullRom(
            Vec3 p0,
            Vec3 p1,
            Vec3 p2,
            Vec3 p3,
            double t
    ) {
        double t2 = t * t;
        double t3 = t2 * t;

        return p1.scale(2.0D)
                .add(p2.subtract(p0).scale(t))
                .add(
                        p0.scale(2.0D)
                                .subtract(p1.scale(5.0D))
                                .add(p2.scale(4.0D))
                                .subtract(p3)
                                .scale(t2)
                )
                .add(
                        p0.scale(-1.0D)
                                .add(p1.scale(3.0D))
                                .subtract(p2.scale(3.0D))
                                .add(p3)
                                .scale(t3)
                )
                .scale(0.5D);
    }

    private static void buildFrames(RiftPath path) {
        Vec3 previousTangent = null;
        Vec3 previousNormal = null;

        for (int i = 0; i < path.centers.length; i++) {
            Vec3 tangent = getTangent(path.centers, i);

            if (tangent.lengthSqr() < 0.000001D) {
                tangent = previousTangent != null
                        ? previousTangent
                        : new Vec3(0, 1, 0);
            }

            tangent = tangent.normalize();

            Vec3 normal;

            if (previousTangent == null) {
                Vec3 reference = Math.abs(tangent.y) < 0.85D
                        ? new Vec3(0, 1, 0)
                        : new Vec3(1, 0, 0);

                normal = tangent.cross(reference).normalize();
            } else {
                Vec3 axis = previousTangent.cross(tangent);

                double axisLength = axis.length();
                double dot = Mth.clamp(
                        (float) previousTangent.dot(tangent),
                        -1.0F,
                        1.0F
                );

                normal = previousNormal;

                if (axisLength > 0.000001D) {
                    axis = axis.scale(1.0D / axisLength);

                    double angle = Math.atan2(axisLength, dot);

                    normal = rotateAroundAxis(
                            normal,
                            axis,
                            angle
                    );
                }

                normal = normal.subtract(
                        tangent.scale(normal.dot(tangent))
                );

                if (normal.lengthSqr() < 0.000001D) {
                    Vec3 reference = Math.abs(tangent.y) < 0.85D
                            ? new Vec3(0, 1, 0)
                            : new Vec3(1, 0, 0);

                    normal = tangent.cross(reference);
                }

                normal = normal.normalize();
            }

            Vec3 binormal = tangent.cross(normal).normalize();

            path.normals[i] = normal;
            path.binormals[i] = binormal;

            previousTangent = tangent;
            previousNormal = normal;
        }
    }

    private static Vec3 rotateAroundAxis(
            Vec3 vector,
            Vec3 axis,
            double angle
    ) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);

        return vector.scale(cos)
                .add(axis.cross(vector).scale(sin))
                .add(
                        axis.scale(
                                axis.dot(vector) * (1.0D - cos)
                        )
                );
    }

    private static Vec3 getTangent(Vec3[] centers, int index) {
        if (index == 0) {
            return centers[1].subtract(centers[0]);
        }

        if (index == centers.length - 1) {
            return centers[index].subtract(centers[index - 1]);
        }

        return centers[index + 1].subtract(centers[index - 1]);
    }

    private static Vec3 randomPoint(
            Random random,
            float maxX,
            float maxY,
            float maxZ
    ) {
        return new Vec3(
                (random.nextDouble() * 2.0D - 1.0D) * maxX,
                (random.nextDouble() * 2.0D - 1.0D) * maxY,
                (random.nextDouble() * 2.0D - 1.0D) * maxZ
        );
    }

    private static Vec3 randomDirection(Random random) {
        Vec3 direction = new Vec3(
                random.nextGaussian(),
                random.nextGaussian(),
                random.nextGaussian()
        );

        return direction.normalize();
    }

    private static Vec3 clampPoint(
            Vec3 point,
            float maxX,
            float maxY,
            float maxZ
    ) {
        return new Vec3(
                Mth.clamp(point.x, -maxX, maxX),
                Mth.clamp(point.y, -maxY, maxY),
                Mth.clamp(point.z, -maxZ, maxZ)
        );
    }

    private static void drawPath(
            PoseStack poseStack,
            VertexConsumer vertices,
            RiftPath path,
            int sides,
            float pulse,
            float age
    ) {
        PoseStack.Pose pose = poseStack.last();

        for (int ring = 0; ring < path.centers.length - 1; ring++) {
            for (int side = 0; side < sides; side++) {
                Vec3 a = getPoint(
                        path, ring, side,
                        sides, pulse, age
                );

                Vec3 b = getPoint(
                        path, ring, side + 1,
                        sides, pulse, age
                );

                Vec3 c = getPoint(
                        path, ring + 1, side + 1,
                        sides, pulse, age
                );

                Vec3 d = getPoint(
                        path, ring + 1, side,
                        sides, pulse, age
                );

                emitQuad(vertices, pose, a, b, c, d);
                emitQuad(vertices, pose, d, c, b, a);
            }
        }
    }

    private static Vec3 getPoint(
            RiftPath path,
            int ring,
            int side,
            int sides,
            float pulse,
            float age
    ) {
        float angle =
                side / (float) sides * Mth.TWO_PI
                        + path.twistOffset
                        + ring * 0.08F
                        + age * 0.012F;

        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);

        float radius = path.radii[ring] * pulse;

        Vec3 radialOffset = path.normals[ring].scale(cos * radius)
                .add(path.binormals[ring].scale(sin * radius));

        return path.centers[ring].add(radialOffset);
    }


    private static void emitQuad(
            VertexConsumer vertices,
            PoseStack.Pose pose,
            Vec3 a,
            Vec3 b,
            Vec3 c,
            Vec3 d
    ) {
        vertices.addVertex(pose, (float) a.x, (float) a.y, (float) a.z);
        vertices.addVertex(pose, (float) b.x, (float) b.y, (float) b.z);
        vertices.addVertex(pose, (float) c.x, (float) c.y, (float) c.z);
        vertices.addVertex(pose, (float) d.x, (float) d.y, (float) d.z);
    }


    private static final class RiftGeometry {
        private RiftPath mainPath;
        private final List<RiftPath> branches = new ArrayList<>();

        private float rotationOffset;
        private float phase;
    }

    private static final class RiftPath {
        private final Vec3[] centers;
        private final float[] radii;
        private final Vec3[] normals;
        private final Vec3[] binormals;

        private float twistOffset;

        private RiftPath(int segments) {
            centers = new Vec3[segments];
            radii = new float[segments];
            normals = new Vec3[segments];
            binormals = new Vec3[segments];
        }
    }
}
