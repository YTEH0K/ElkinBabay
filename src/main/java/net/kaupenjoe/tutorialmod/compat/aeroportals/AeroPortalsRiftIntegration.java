package net.kaupenjoe.tutorialmod.compat.aeroportals;

import com.breakinblocks.aeroportals.api.AeroPortalType;
import com.breakinblocks.aeroportals.api.AeroPortalsApi;
import com.breakinblocks.aeroportals.api.PortalDestination;
import com.breakinblocks.aeroportals.util.AabbUtil;
import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.kaupenjoe.tutorialmod.TutorialMod;
import net.kaupenjoe.tutorialmod.block.ModBlocks;
import net.kaupenjoe.tutorialmod.entity.custom.RiftEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.slf4j.Logger;

import java.util.List;

public final class AeroPortalsRiftIntegration {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation PORTAL_ID =
            ResourceLocation.fromNamespaceAndPath(TutorialMod.MOD_ID, "rift");

    private AeroPortalsRiftIntegration() {
    }

    public static void register() {
        AeroPortalsApi.registerPortal(new AeroPortalType() {
            @Override
            public ResourceLocation id() {
                return PORTAL_ID;
            }

            @Override
            public boolean matches(BlockState state) {
                return state.is(ModBlocks.RIFT_MARKER.get());
            }

            @Override
            public PortalDestination resolve(
                    ServerLevel sourceLevel,
                    ServerSubLevel sub,
                    BlockPos hitPos
            ) {
                AABB searchBounds = new AABB(hitPos).inflate(2.0D, 4.0D, 2.0D);

                RiftEntity normalRift = sourceLevel.getEntitiesOfClass(
                        RiftEntity.class, searchBounds, Entity::isAlive
                ).stream().findFirst().orElse(null);

                if (normalRift == null || normalRift.getDestinationDimension() == null
                        || normalRift.getDestinationPos() == null) {
                    return null;
                }

                ServerLevel destinationLevel = sourceLevel.getServer().getLevel(
                        normalRift.getDestinationDimension()
                );

                if (destinationLevel == null) {
                    LOGGER.warn("Rift destination dimension is unavailable.");
                    return null;
                }

                BlockPos destination = normalRift.getDestinationPos();
                normalRift.onPortalEntry();

                AABB shipBounds = AabbUtil.worldAabb(sub);
                double shipOriginY = sub.logicalPose().position().y();
                double minYOffset = shipBounds.minY - shipOriginY;
                double destinationY = destination.getY() - minYOffset;

                Vec3 shipDestination = new Vec3(
                        destination.getX() + 0.5D,
                        destinationY,
                        destination.getZ() + 0.5D
                );

                PortalDestination portalDestination = PortalDestination.of(
                        destinationLevel, shipDestination, true, PORTAL_ID.toString()
                );

                LOGGER.info(
                        "AeroPortals rift detected: {} -> {} at {}",
                        sourceLevel.dimension().location(),
                        destinationLevel.dimension().location(),
                        destination
                );

                return portalDestination;
            }
        });

        LOGGER.info("Registered AeroPortals support for tutorialmod:rifts.");
    }

    public static void pullNearbyAirships(
            ServerLevel level,
            Vec3 riftCenter,
            double radius,
            double progress
    ) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);

        if (container == null) {
            return;
        }

        SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        double desiredPullSpeed = 1.0D + 17.0D * progress * progress;

        for (ServerSubLevel sub : container.getAllSubLevels()) {
            if (sub.isRemoved()) {
                continue;
            }

            AABB bounds = AabbUtil.worldAabb(sub);
            Vec3 toRift = riftCenter.subtract(bounds.getCenter());
            double distance = toRift.length();

            double halfX = bounds.getXsize() * 0.5D;
            double halfY = bounds.getYsize() * 0.5D;
            double halfZ = bounds.getZsize() * 0.5D;
            double halfDiagonal = Math.sqrt(halfX * halfX + halfY * halfY + halfZ * halfZ);

            if (distance - halfDiagonal > radius || distance < 0.001D) {
                continue;
            }

            RigidBodyHandle handle = physicsSystem.getPhysicsHandle(sub);
            if (!handle.isValid()) {
                continue;
            }

            Vector3d direction = new Vector3d(
                    toRift.x / distance, toRift.y / distance, toRift.z / distance
            );
            Vector3d currentVelocity = handle.getLinearVelocity(new Vector3d());
            double currentTowardSpeed = currentVelocity.dot(direction);

            if (currentTowardSpeed < desiredPullSpeed) {
                double appliedSpeed = Math.min(desiredPullSpeed - currentTowardSpeed, 0.75D);
                handle.addLinearAndAngularVelocity(direction.mul(appliedSpeed), new Vector3d());
                physicsSystem.getPipeline().wakeUp(sub);
            }
        }
    }

    public static void removeLegacyAggressivePortalMarkers(ServerLevel level, BlockPos base) {
        List<RiftEntity> normalRifts = level.getEntitiesOfClass(
                RiftEntity.class, new AABB(base).inflate(2.0D, 4.0D, 2.0D), Entity::isAlive
        );

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy < 4; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos markerPos = base.offset(dx, dy, dz);

                    if (level.getBlockState(markerPos).is(ModBlocks.RIFT_MARKER.get())
                            && normalRifts.stream().noneMatch(rift -> containsMarker(rift, markerPos))) {
                        level.setBlock(markerPos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private static boolean containsMarker(RiftEntity rift, BlockPos markerPos) {
        BlockPos base = rift.blockPosition();
        int dy = markerPos.getY() - base.getY();

        return Math.abs(markerPos.getX() - base.getX()) <= 1
                && dy >= 0 && dy < 4
                && Math.abs(markerPos.getZ() - base.getZ()) <= 1;
    }

    public static boolean isEntityOnAirship(Entity entity) {
        return Sable.HELPER.getTrackingOrVehicleSubLevel(entity) != null;
    }

    public static Vec3 getEntityWorldPosition(Entity entity) {
        return Sable.HELPER.projectOutOfSubLevel(entity.level(), entity.position());
    }
}