package net.kaupenjoe.tutorialmod.distortion;

import net.kaupenjoe.tutorialmod.distortion.network.DistortionNetwork;
import net.kaupenjoe.tutorialmod.rift.RiftManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public final class DistortionManager {

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        boolean overworld = level.dimension().equals(Level.OVERWORLD);

        boolean prison = level.dimension().equals(
                RiftManager.PRISON_DIMENSION
        );

        if (!overworld && !prison) {
            return;
        }

        for (ServerPlayer player : level.players()) {
            DistortionData data = player.getData(
                    ModAttachments.DISTORTION.get()
            );

            boolean changed = overworld
                    ? data.tickInOverworld()
                    : data.tickInPrison();

            if (changed) {
                DistortionNetwork.sync(
                        player,
                        data.getDistortionTenths()
                );
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        DistortionData data = player.getData(
                ModAttachments.DISTORTION.get()
        );

        DistortionNetwork.sync(
                player,
                data.getDistortionTenths()
        );
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        DistortionData data = player.getData(
                ModAttachments.DISTORTION.get()
        );

        boolean diedFromDistortion =
                data.consumeDeathPenaltyAfterRespawn();

        if (diedFromDistortion) {
            player.removeEffect(MobEffects.DARKNESS);
            player.removeEffect(MobEffects.BLINDNESS);
        }

        DistortionEffectManager.refreshAttributeModifiers(
                player,
                data
        );

        DistortionNetwork.sync(
                player,
                data.getDistortionTenths()
        );
    }

    public static void addDistortion(
            ServerPlayer player,
            int amountTenths
    ) {
        DistortionData data = player.getData(
                ModAttachments.DISTORTION.get()
        );

        if (data.addTenths(amountTenths)) {
            DistortionNetwork.sync(
                    player,
                    data.getDistortionTenths()
            );
        }
    }

    public static int getDistortionTenths(ServerPlayer player) {
        return player.getData(
                ModAttachments.DISTORTION.get()
        ).getDistortionTenths();
    }
}