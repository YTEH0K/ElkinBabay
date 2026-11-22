package net.kaupenjoe.tutorialmod.item;

import net.kaupenjoe.tutorialmod.TutorialMod;
import net.kaupenjoe.tutorialmod.item.custom.PipeItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.kaupenjoe.tutorialmod.item.custom.RiftSealItem;
import net.kaupenjoe.tutorialmod.item.custom.RiftCompassItem;
import java.util.List;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TutorialMod.MOD_ID);

    public static final DeferredItem<Item> PIPE = ITEMS.register("pipe",
            () -> new PipeItem(new Item.Properties()));

    public static final DeferredItem<Item> RIFT_SEAL =
            ITEMS.register("rift_seal",
                    () -> new RiftSealItem(
                            new Item.Properties().stacksTo(16)
                    ));

    public static final DeferredItem<Item> RIFT_COMPASS =
            ITEMS.register(
                    "rift_compass",
                    () -> new RiftCompassItem(
                            new Item.Properties().stacksTo(1)
                    )
            );


    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
