package com.levelscraft7.thelostcamera.item;

import com.levelscraft7.thelostcamera.photo.PhotoCaptureManager;
import com.levelscraft7.thelostcamera.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public final class LostCameraItem extends Item {
    public LostCameraItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack camera = player.getItemInHand(hand);

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        if (isActive(camera) && player.isShiftKeyDown()) {
            setActive(camera, false);
            level.playSound(null, player.blockPosition(), SoundEvents.SPYGLASS_STOP_USING,
                    SoundSource.PLAYERS, 0.65F, 0.82F);
            player.sendOverlayMessage(Component.translatable("message.thelostcamera.camera_closed"));
            return InteractionResult.SUCCESS;
        }

        if (!isActive(camera)) {
            setActive(camera, true);
            level.playSound(null, player.blockPosition(), SoundEvents.SPYGLASS_USE,
                    SoundSource.PLAYERS, 0.70F, 0.78F);
            player.sendOverlayMessage(Component.translatable("message.thelostcamera.camera_opened"));
            return InteractionResult.SUCCESS;
        }

        PhotoCaptureManager.CaptureResult result = PhotoCaptureManager.beginCapture(serverPlayer);
        switch (result) {
            case NORMAL_PHOTO -> {
                playShutter(level, player);
                setActive(camera, false);
                player.sendOverlayMessage(Component.translatable("message.thelostcamera.capture_started"));
            }
            case RUIN_PHOTO -> {
                playShutter(level, player);
                setActive(camera, false);
                player.sendOverlayMessage(Component.translatable("message.thelostcamera.ruin_locked"));
            }
            case NO_PLATE -> player.sendOverlayMessage(Component.translatable("message.thelostcamera.no_plate"));
            case COOLDOWN -> player.sendOverlayMessage(Component.translatable("message.thelostcamera.cooldown"));
            case ALREADY_CAPTURING -> player.sendOverlayMessage(
                    Component.translatable("message.thelostcamera.already_capturing")
            );
        }

        return InteractionResult.SUCCESS;
    }

    private static void playShutter(Level level, Player player) {
        level.playSound(null, player.blockPosition(), SoundEvents.LEVER_CLICK,
                SoundSource.PLAYERS, 0.85F, 1.65F);
        level.playSound(null, player.blockPosition(), SoundEvents.TRIPWIRE_CLICK_ON,
                SoundSource.PLAYERS, 0.55F, 0.72F);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> tooltip,
            TooltipFlag flag
    ) {
        tooltip.accept(Component.translatable("tooltip.thelostcamera.camera.raise_capture")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.thelostcamera.camera.zoom")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("tooltip.thelostcamera.camera.lower")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("tooltip.thelostcamera.camera.plate")
                .withStyle(ChatFormatting.GOLD));
        super.appendHoverText(stack, context, display, tooltip, flag);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (slot == null || !(entity instanceof Player player) || player.getItemBySlot(slot) != stack) {
            setActive(stack, false);
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 50_000;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return isActive(stack) ? ItemUseAnimation.BOW : ItemUseAnimation.NONE;
    }

    public static boolean isActive(ItemStack stack) {
        return stack.has(ModDataComponents.CAMERA_ACTIVE);
    }

    public static void setActive(ItemStack stack, boolean active) {
        if (active) {
            stack.set(ModDataComponents.CAMERA_ACTIVE, Unit.INSTANCE);
        } else {
            stack.remove(ModDataComponents.CAMERA_ACTIVE);
        }
    }
}
