package net.kaupenjoe.tutorialmod.item.custom;

import net.kaupenjoe.tutorialmod.item.ModItems;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class PipeItem extends Item {
    private static final int SLOTS = 6;

    public PipeItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    private static NonNullList<ItemStack> getItems(ItemStack pipe) {
        NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

        ItemContainerContents contents =
                pipe.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);

        contents.copyInto(items);

        return items;
    }

    private static void setItems(ItemStack pipe, List<ItemStack> items) {
        pipe.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
    }

    private static MobEffectInstance getEffect(ItemStack item) {
        if (item.is(ModItems.BISMUTH.get())) {
            return new MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED,
                    20 * 15,
                    0
            );
        }

        if (item.is(ModItems.STARLIGHT_ASHES.get())) {
            return new MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.JUMP,
                    20 * 15,
                    0
            );
        }

        if (item.is(Items.RED_MUSHROOM)) {
            return new MobEffectInstance(
                    MobEffects.CONFUSION,
                    20 * 15,
                    200
            );
        }

        return null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack pipe = player.getItemInHand(hand);

        if (!level.isClientSide) {
            NonNullList<ItemStack> items = getItems(pipe);

            if (player.isShiftKeyDown()) {
                for (int i = SLOTS - 1; i >= 0; i--) {
                    if (!items.get(i).isEmpty()) {
                        player.getInventory().placeItemBackInInventory(items.get(i).copy());
                        items.set(i, ItemStack.EMPTY);
                        setItems(pipe, items);

                        return InteractionResultHolder.success(pipe);
                    }
                }

                return InteractionResultHolder.pass(pipe);
            }

            InteractionHand otherHand =
                    hand == InteractionHand.MAIN_HAND
                            ? InteractionHand.OFF_HAND
                            : InteractionHand.MAIN_HAND;

            ItemStack otherStack = player.getItemInHand(otherHand);

            MobEffectInstance possibleEffect = getEffect(otherStack);

            if (possibleEffect != null) {
                for (int i = 0; i < SLOTS; i++) {
                    if (items.get(i).isEmpty()) {
                        ItemStack inserted = otherStack.copyWithCount(1);

                        items.set(i, inserted);
                        otherStack.shrink(1);

                        setItems(pipe, items);

                        return InteractionResultHolder.success(pipe);
                    }
                }
            }
        }

        player.startUsingItem(hand);

        return InteractionResultHolder.consume(pipe);
    }

    @Override
    public void releaseUsing(
            ItemStack pipe,
            Level level,
            LivingEntity livingEntity,
            int timeLeft
    ) {
        if (level.isClientSide || !(livingEntity instanceof Player player)) {
            return;
        }

        int usedTicks = getUseDuration(pipe, livingEntity) - timeLeft;

        if (usedTicks < 20) {
            return;
        }

        NonNullList<ItemStack> items = getItems(pipe);

        for (int i = 0; i < SLOTS; i++) {
            ItemStack material = items.get(i);

            if (material.isEmpty()) {
                continue;
            }

            MobEffectInstance effect = getEffect(material);

            if (effect != null) {
                player.addEffect(effect);

                items.set(i, ItemStack.EMPTY);
                setItems(pipe, items);

                spawnSmoke(level, player);

                level.playSound(
                        null,
                        player.blockPosition(),
                        SoundEvents.BONE_MEAL_USE,
                        SoundSource.PLAYERS,
                        0.7f,
                        1.2f
                );

                break;
            }
        }
    }

    private static void spawnSmoke(Level level, Player player) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        Vec3 position = player.getEyePosition()
                .add(player.getLookAngle().scale(0.6));

        serverLevel.sendParticles(
                ParticleTypes.SMOKE,
                position.x,
                position.y,
                position.z,
                6,
                0.03,
                0.03,
                0.03,
                0.01
        );
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BLOCK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag
    ) {
        ItemContainerContents contents =
                stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);

        for (ItemStack item : contents.nonEmptyItems()) {
            tooltipComponents.add(item.getHoverName());
        }

        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}