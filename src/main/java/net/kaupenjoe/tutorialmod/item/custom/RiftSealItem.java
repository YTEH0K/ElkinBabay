
package net.kaupenjoe.tutorialmod.item.custom;

import net.kaupenjoe.tutorialmod.rift.RiftProtection;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

import java.util.List;

public class RiftSealItem extends Item {

    public static final int PROTECTION_TICKS = 20 * 8;

    private static final DustParticleOptions SEAL_PARTICLE =
            new DustParticleOptions(
                    new Vector3f(0.15F, 0.75F, 1.0F),
                    1.15F
            );

    public RiftSealItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand hand
    ) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide
                && player instanceof ServerPlayer serverPlayer
                && !player.getCooldowns().isOnCooldown(this)) {

            RiftProtection.protect(
                    serverPlayer,
                    PROTECTION_TICKS
            );

            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }

            player.getCooldowns().addCooldown(
                    this,
                    PROTECTION_TICKS
            );

            serverPlayer.sendSystemMessage(
                    Component.translatable(
                            "message.tutorialmod.rift.seal_activated"
                    )
            );

            ServerLevel serverLevel = serverPlayer.serverLevel();

            serverLevel.playSound(
                    null,
                    serverPlayer.blockPosition(),
                    SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS,
                    0.9F,
                    1.35F
            );

            serverLevel.sendParticles(
                    SEAL_PARTICLE,
                    serverPlayer.getX(),
                    serverPlayer.getY() + 0.9D,
                    serverPlayer.getZ(),
                    18,
                    0.35D,
                    0.55D,
                    0.35D,
                    0.015D
            );
        }

        return InteractionResultHolder.sidedSuccess(
                stack,
                level.isClientSide
        );
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
                        "tooltip.tutorialmod.rift_seal"
                )
        );

        super.appendHoverText(
                stack,
                context,
                tooltipComponents,
                tooltipFlag
        );
    }
}
