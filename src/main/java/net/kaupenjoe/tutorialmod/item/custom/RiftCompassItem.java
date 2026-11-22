
package net.kaupenjoe.tutorialmod.item.custom;

import net.kaupenjoe.tutorialmod.compat.aeroportals.AeroPortalsRiftIntegration;
import net.kaupenjoe.tutorialmod.distortion.network.DistortionNetwork;
import net.kaupenjoe.tutorialmod.rift.RiftLifetimeSavedData;
import net.kaupenjoe.tutorialmod.rift.RiftManager;
import net.kaupenjoe.tutorialmod.rift.client.RiftCompassClientState;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import net.neoforged.fml.ModList;

import java.util.List;

public class RiftCompassItem extends Item {

    private static final long SYNC_INTERVAL_TICKS = 10L;

    private static final double MAX_SEARCH_DISTANCE = 1000.0D;
    private static final double MAX_SEARCH_DISTANCE_SQUARED =
            MAX_SEARCH_DISTANCE * MAX_SEARCH_DISTANCE;

    private record RiftTarget(
            BlockPos position,
            ResourceKey<Level> dimension
    ) {
    }

    public RiftCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(
            ItemStack stack,
            Level level,
            Entity entity,
            int slotId,
            boolean isSelected
    ) {
        super.inventoryTick(
                stack,
                level,
                entity,
                slotId,
                isSelected
        );

        if (level.isClientSide
                || !(level instanceof ServerLevel serverLevel)
                || !(entity instanceof ServerPlayer player)) {
            return;
        }

        boolean active =
                isSelected || player.getOffhandItem() == stack;

        if (!active) {
            return;
        }

        if (serverLevel.getGameTime() % SYNC_INTERVAL_TICKS != 0) {
            return;
        }

        RiftTarget target = findNearestRift(player);

        if (target == null) {
            DistortionNetwork.syncRiftCompass(
                    player,
                    false,
                    0,
                    0,
                    0,
                    ""
            );

            return;
        }

        DistortionNetwork.syncRiftCompass(
                player,
                true,
                target.position().getX(),
                target.position().getY(),
                target.position().getZ(),
                target.dimension().location().toString()
        );
    }

    private static RiftTarget findNearestRift(ServerPlayer player) {
        ServerLevel currentLevel = player.serverLevel();

        Vec3 playerPosition = player.position();

        if (ModList.get().isLoaded("aeroportals")) {
            playerPosition =
                    AeroPortalsRiftIntegration.getEntityWorldPosition(player);
        }

        RiftLifetimeSavedData data =
                RiftLifetimeSavedData.get(currentLevel);

        RiftTarget closestTarget = null;
        double closestDistanceSquared = MAX_SEARCH_DISTANCE_SQUARED;

        for (BlockPos position : data.getActiveRiftPositions()) {
            Vec3 targetPosition = new Vec3(
                    position.getX() + 0.5D,
                    position.getY() + 0.5D,
                    position.getZ() + 0.5D
            );

            double distanceSquared =
                    playerPosition.distanceToSqr(targetPosition);

            if (distanceSquared >= closestDistanceSquared) {
                continue;
            }

            closestDistanceSquared = distanceSquared;

            closestTarget = new RiftTarget(
                    position,
                    currentLevel.dimension()
            );
        }

        return closestTarget;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag
    ) {
        tooltipComponents.add(
                Component.translatable(
                        "tooltip.tutorialmod.rift_compass.description"
                )
        );

        if (!RiftCompassClientState.hasTarget()) {
            tooltipComponents.add(
                    Component.translatable(
                            "tooltip.tutorialmod.rift_compass.no_target"
                    )
            );
        } else {
            String dimensionId =
                    RiftCompassClientState.getDimension();

            Component dimensionName;

            if (dimensionId.equals(
                    RiftManager.PRISON_DIMENSION.location().toString()
            )) {
                dimensionName = Component.translatable(
                        "tooltip.tutorialmod.rift_compass.prison"
                );
            } else if (dimensionId.equals(
                    Level.OVERWORLD.location().toString()
            )) {
                dimensionName = Component.translatable(
                        "tooltip.tutorialmod.rift_compass.overworld"
                );
            } else {
                dimensionName = Component.literal(dimensionId);
            }

            tooltipComponents.add(
                    Component.translatable(
                            "tooltip.tutorialmod.rift_compass.target",
                            dimensionName
                    )
            );

            tooltipComponents.add(
                    Component.translatable(
                            "tooltip.tutorialmod.rift_compass.coordinates",
                            RiftCompassClientState.getX(),
                            RiftCompassClientState.getY(),
                            RiftCompassClientState.getZ()
                    )
            );
        }

        super.appendHoverText(
                stack,
                context,
                tooltipComponents,
                tooltipFlag
        );
    }
}
