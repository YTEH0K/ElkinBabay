
package net.kaupenjoe.tutorialmod.distortion;

import net.kaupenjoe.tutorialmod.TutorialMod;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.UseAnim;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class DistortionEffectManager {

    private static final int DISTORTION_40 = 400;
    private static final int DISTORTION_50 = 500;
    private static final int DISTORTION_60 = 600;
    private static final int DISTORTION_70 = 700;
    private static final int DISTORTION_80 = 800;
    private static final int DISTORTION_100 = 1000;

    private static final int DARKNESS_DURATION_TICKS = 20 * 3;
    private static final int DEATH_EFFECT_EXTRA_TICKS = 5;

    private static final ResourceLocation HEALTH_40 =
            id("distortion_health_40");

    private static final ResourceLocation HEALTH_50 =
            id("distortion_health_50");

    private static final ResourceLocation ARMOR_50 =
            id("distortion_armor_50");

    private static final ResourceLocation ARMOR_60 =
            id("distortion_armor_60");

    private static final ResourceLocation MOVEMENT_70 =
            id("distortion_movement_70");

    private static final ResourceLocation MOVEMENT_100 =
            id("distortion_movement_100");

    private static final ResourceLocation BREAK_SPEED_70 =
            id("distortion_break_speed_70");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                TutorialMod.MOD_ID,
                path
        );
    }

    @SubscribeEvent
    public void onPlayerTickPre(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        DistortionData data = player.getData(
                ModAttachments.DISTORTION.get()
        );

        data.captureFoodState(player.getFoodData());
    }

    @SubscribeEvent
    public void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !player.isAlive()) {
            return;
        }

        DistortionData data = player.getData(
                ModAttachments.DISTORTION.get()
        );

        FoodData foodData = player.getFoodData();

        float additionalExhaustion =
                data.finishFoodTick(foodData);

        if (data.getDistortionTenths() >= DISTORTION_60
                && additionalExhaustion > 0.0F) {
            foodData.addExhaustion(additionalExhaustion);
        }

        refreshAttributeModifiers(player, data);

        if (data.tickDarknessCycle()) {
            player.addEffect(
                    new MobEffectInstance(
                            MobEffects.DARKNESS,
                            DARKNESS_DURATION_TICKS,
                            0,
                            false,
                            false,
                            false
                    )
            );
        }

        if (data.isDistortionDeathPending()) {
            applyDeathCountdownEffects(player, 2);
            player.kill();
            return;
        }

        boolean countdownStarted =
                data.startDeathCountdownIfNeeded();

        if (countdownStarted) {
            applyDeathCountdownEffects(
                    player,
                    data.getDeathCountdownTicks()
                            + DEATH_EFFECT_EXTRA_TICKS
            );

            return;
        }

        if (data.hasDeathCountdown()) {
            int remainingDuration =
                    data.getDeathCountdownTicks()
                            + DEATH_EFFECT_EXTRA_TICKS;

            applyDeathCountdownEffects(
                    player,
                    remainingDuration
            );

            if (data.tickDeathCountdown()) {
                player.kill();
            }
        }
    }

    @SubscribeEvent
    public void onItemUseStart(
            LivingEntityUseItemEvent.Start event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (DistortionManager.getDistortionTenths(player)
                < DISTORTION_60) {
            return;
        }

        UseAnim animation = event.getItem().getUseAnimation();

        if (animation != UseAnim.EAT
                && animation != UseAnim.DRINK) {
            return;
        }

        long extendedDuration =
                (long) event.getDuration() * 2L;

        event.setDuration(
                (int) Math.min(
                        Integer.MAX_VALUE,
                        extendedDuration
                )
        );
    }

    @SubscribeEvent
    public void onLivingHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (DistortionManager.getDistortionTenths(player)
                >= DISTORTION_70) {
            event.setAmount(event.getAmount() * 0.5F);
        }
    }

    public static void refreshAttributeModifiers(
            ServerPlayer player,
            DistortionData data
    ) {
        int distortion = data.getDistortionTenths();

        updateModifier(
                player,
                Attributes.MAX_HEALTH,
                HEALTH_40,
                -2.0D,
                AttributeModifier.Operation.ADD_VALUE,
                distortion >= DISTORTION_40
        );

        updateModifier(
                player,
                Attributes.MAX_HEALTH,
                HEALTH_50,
                -2.0D,
                AttributeModifier.Operation.ADD_VALUE,
                distortion >= DISTORTION_50
        );

        updateModifier(
                player,
                Attributes.ARMOR,
                ARMOR_50,
                -1.0D,
                AttributeModifier.Operation.ADD_VALUE,
                distortion >= DISTORTION_50
        );

        updateModifier(
                player,
                Attributes.ARMOR,
                ARMOR_60,
                -1.0D,
                AttributeModifier.Operation.ADD_VALUE,
                distortion >= DISTORTION_60
        );

        boolean at70 = distortion >= DISTORTION_70;
        boolean at100 = distortion >= DISTORTION_100;

        updateModifier(
                player,
                Attributes.MOVEMENT_SPEED,
                MOVEMENT_70,
                -0.10D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                at70 && !at100
        );

        updateModifier(
                player,
                Attributes.MOVEMENT_SPEED,
                MOVEMENT_100,
                -1.0D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                at100
        );

        updateModifier(
                player,
                Attributes.BLOCK_BREAK_SPEED,
                BREAK_SPEED_70,
                -0.10D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                at70
        );

        float maxHealth = player.getMaxHealth();

        if (player.getHealth() > maxHealth) {
            player.setHealth(maxHealth);
        }
    }

    private static void updateModifier(
            ServerPlayer player,
            Holder<Attribute> attribute,
            ResourceLocation modifierId,
            double amount,
            AttributeModifier.Operation operation,
            boolean enabled
    ) {
        AttributeInstance instance =
                player.getAttribute(attribute);

        if (instance == null) {
            return;
        }

        if (enabled) {
            if (!instance.hasModifier(modifierId)) {
                instance.addTransientModifier(
                        new AttributeModifier(
                                modifierId,
                                amount,
                                operation
                        )
                );
            }
        } else {
            instance.removeModifier(modifierId);
        }
    }

    private static void applyDeathCountdownEffects(
            ServerPlayer player,
            int duration
    ) {
        ensureEffect(
                player,
                MobEffects.DARKNESS,
                duration
        );

        ensureEffect(
                player,
                MobEffects.BLINDNESS,
                duration
        );
    }

    private static void ensureEffect(
            ServerPlayer player,
            Holder<MobEffect> effect,
            int duration
    ) {
        MobEffectInstance current = player.getEffect(effect);

        if (current == null || current.getDuration() < duration - 1) {
            player.addEffect(
                    new MobEffectInstance(
                            effect,
                            Math.max(2, duration),
                            0,
                            false,
                            false,
                            false
                    )
            );
        }
    }
}
