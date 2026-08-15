package com.levelscraft7.thelostcamera.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** Consumable glass plate used by the Lost Camera. */
public final class PhotographicPlateItem extends Item {
    public PhotographicPlateItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> tooltip,
            TooltipFlag flag
    ) {
        tooltip.accept(Component.translatable("tooltip.thelostcamera.plate.use")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.thelostcamera.plate.consume")
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, display, tooltip, flag);
    }
}
