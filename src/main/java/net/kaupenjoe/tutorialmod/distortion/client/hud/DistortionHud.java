package net.kaupenjoe.tutorialmod.client.hud;

import net.kaupenjoe.tutorialmod.TutorialMod;
import net.kaupenjoe.tutorialmod.distortion.client.DistortionClientState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import java.util.Locale;

@EventBusSubscriber(
        modid = TutorialMod.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public final class DistortionHud {

    private static final int BAR_WIDTH = 10;
    private static final int BAR_HEIGHT = 110;

    private DistortionHud() {
    }

    @SubscribeEvent
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(
                        TutorialMod.MOD_ID,
                        "distortion_hud"
                ),
                DistortionHud::render
        );
    }

    private static void render(
            GuiGraphics graphics,
            DeltaTracker deltaTracker
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null) {
            return;
        }

        int valueTenths = DistortionClientState.getDistortionTenths();

        int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        int left = 10;
        int top = (screenHeight - BAR_HEIGHT) / 2;
        int bottom = top + BAR_HEIGHT;

        // Внешняя рамка
        graphics.fill(
                left - 2,
                top - 2,
                left + BAR_WIDTH + 2,
                bottom + 2,
                0xAA080A10
        );

        // Фон шкалы
        graphics.fill(
                left,
                top,
                left + BAR_WIDTH,
                bottom,
                0xFF252833
        );

        // Заполнение шкалы
        int fillHeight = Math.round(
                BAR_HEIGHT * (valueTenths / 1000.0F)
        );

        if (valueTenths > 0) {
            fillHeight = Math.max(1, fillHeight);

            graphics.fill(
                    left + 1,
                    bottom - fillHeight,
                    left + BAR_WIDTH - 1,
                    bottom,
                    getColor(valueTenths)
            );
        }

        String value = String.format(
                Locale.ROOT,
                "%.1f",
                valueTenths / 10.0F
        );

        graphics.drawString(
                minecraft.font,
                "Искажение",
                left + BAR_WIDTH + 7,
                (top + bottom) / 2 - 12,
                0xFFE7E4EC,
                true
        );

        graphics.drawString(
                minecraft.font,
                value,
                left + BAR_WIDTH + 7,
                (top + bottom) / 2 + 1,
                getColor(valueTenths),
                true
        );
    }

    private static int getColor(int valueTenths) {
        if (valueTenths >= 800) {
            return 0xFFE6535E;
        }

        if (valueTenths >= 600) {
            return 0xFFE5A34B;
        }

        if (valueTenths >= 300) {
            return 0xFFAB83E8;
        }

        return 0xFF68C5A5;
    }
}