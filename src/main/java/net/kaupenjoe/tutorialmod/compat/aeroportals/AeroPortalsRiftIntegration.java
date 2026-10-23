package net.kaupenjoe.tutorialmod.compat.aeroportals;

import com.breakinblocks.aeroportals.api.AeroPortalType;
import com.breakinblocks.aeroportals.api.AeroPortalsApi;
import com.breakinblocks.aeroportals.api.PortalDestination;
import com.breakinblocks.aeroportals.util.AabbUtil;
import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.kaupenjoe.tutorialmod.TutorialMod;
import net.kaupenjoe.tutorialmod.block.ModBlocks;
import net.kaupenjoe.tutorialmod.entity.custom.RiftEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.List;

public final class AeroPortalsRiftIntegration {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation PORTAL_ID =
            ResourceLocation.fromNamespaceAndPath(
                    TutorialMod.MOD_ID,
                    "rift"
            );

    private AeroPortalsRiftIntegration() {
    }

    public static void register() {
        AeroPortalsApi.registerPortal(new AeroPortalType() {

            @Override
            public ResourceLocation id() {
                return PORTAL_ID;
            }

            @Override
            public Collection<Block> matchedBlocks() {
                return List.of(ModBlocks.RIFT_MARKER.get());
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
                AABB searchBounds = new AABB(hitPos)
                        .inflate(2.0D, 4.0D, 2.0D);

                RiftEntity rift = sourceLevel.getEntitiesOfClass(
                        RiftEntity.class,
                        searchBounds,
                        Entity::isAlive
                ).stream().findFirst().orElse(null);

                if (rift == null
                        || rift.getDestinationDimension() == null
                        || rift.getDestinationPos() == null) {

                    LOGGER.warn(
                            "AeroPortals found a rift marker at {}, "
                                    + "but its RiftEntity or destination "
                                    + "could not be found.",
                            hitPos
                    );

                    return null;
                }

                ServerLevel destinationLevel =
                        sourceLevel.getServer().getLevel(
                                rift.getDestinationDimension()
                        );

                if (destinationLevel == null) {
                    LOGGER.warn(
                            "Rift destination dimension {} is unavailable.",
                            rift.getDestinationDimension().location()
                    );

                    return null;
                }

                BlockPos destination = rift.getDestinationPos();

                // AeroPortals expects the ship's world position,
                // not a block position on which to place the ship.
                AABB shipBounds = AabbUtil.worldAabb(sub);

                double shipOriginY =
                        sub.logicalPose().position().y();

                double minYOffset =
                        shipBounds.minY - shipOriginY;

                double destinationY =
                        destination.getY() - minYOffset;

                Vec3 shipDestination = new Vec3(
                        destination.getX() + 0.5D,
                        destinationY,
                        destination.getZ() + 0.5D
                );

                // Reset the rift's closing timer when a ship
                // attempts to enter it.
                rift.onPortalEntry();

                LOGGER.info(
                        "AeroPortals rift detected: {} -> {} at {}",
                        sourceLevel.dimension().location(),
                        destinationLevel.dimension().location(),
                        destination
                );

                return PortalDestination.of(
                        destinationLevel,
                        shipDestination,
                        true,
                        PORTAL_ID.toString()
                );
            }
        });

        LOGGER.info("Registered AeroPortals support for tutorialmod:rifts.");
    }

    public static boolean isEntityOnAirship(Entity entity) {
        return Sable.HELPER.getTrackingSubLevel(entity) != null;
    }
}