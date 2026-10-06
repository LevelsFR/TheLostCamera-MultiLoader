package com.levelscraft7.thelostcamera.item;

import com.levelscraft7.thelostcamera.data.PhotoData;
import com.levelscraft7.thelostcamera.network.ClientUiBridge;
import com.levelscraft7.thelostcamera.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/** A physical developed copy of an album photograph. */
public final class PhotographicPrintItem extends Item {
    public PhotographicPrintItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.has(ModDataComponents.PHOTO_DATA.get())) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendOverlayMessage(Component.translatable("message.thelostcamera.print.empty"));
            }
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            ClientUiBridge.openPhoto(stack);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        PhotoData data = stack.get(ModDataComponents.PHOTO_DATA.get());
        if (data == null) {
            tooltip.accept(Component.translatable("tooltip.thelostcamera.print.empty")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.accept(Component.translatable("tooltip.thelostcamera.print.empty_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
            super.appendHoverText(stack, context, display, tooltip, flag);
            return;
        }
        tooltip.accept(Component.translatable("tooltip.thelostcamera.print.photographer", data.photographer())
                .withStyle(ChatFormatting.GRAY));
        if (data.hasRuin()) {
            tooltip.accept(Component.translatable("tooltip.thelostcamera.print.ruin")
                    .withStyle(ChatFormatting.GOLD));
        }
        tooltip.accept(Component.translatable("tooltip.thelostcamera.print.frame")
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, display, tooltip, flag);
    }
}


