package net.kaupenjoe.tutorialmod.block.custom;

import net.kaupenjoe.tutorialmod.rift.RiftManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class RiftBlock extends Block {
    public RiftBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        if (!RiftManager.teleportPlayer(serverPlayer, serverLevel)) {
            serverPlayer.sendSystemMessage(
                    net.minecraft.network.chat.Component.translatable("message.tutorialmod.rift.no_destination")
            );
            return InteractionResult.FAIL;
        }

        serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        return InteractionResult.CONSUME;
    }
}
