package com.levelscraft7.thelostcamera.item;

import com.levelscraft7.thelostcamera.photo.PhotoCaptureManager;
import com.levelscraft7.thelostcamera.registry.ModDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.Level;

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
            player.sendOverlayMessage(Component.translatable("message.thelostcamera.camera_closed"));
            return InteractionResult.SUCCESS;
        }

        if (!isActive(camera)) {
            setActive(camera, true);
            player.sendOverlayMessage(Component.translatable("message.thelostcamera.camera_opened"));
            return InteractionResult.SUCCESS;
        }

        PhotoCaptureManager.CaptureResult result = PhotoCaptureManager.beginCapture(serverPlayer);
        switch (result) {
            case NORMAL_PHOTO -> {
                setActive(camera, false);
                player.sendOverlayMessage(Component.translatable("message.thelostcamera.capture_started"));
            }
            case RUIN_PHOTO -> {
                setActive(camera, false);
                player.sendOverlayMessage(Component.translatable("message.thelostcamera.ruin_locked"));
            }
            case NO_PLATE -> player.sendOverlayMessage(Component.translatable("message.thelostcamera.no_plate"));
            case COOLDOWN -> player.sendOverlayMessage(Component.translatable("message.thelostcamera.cooldown"));
            case ALREADY_CAPTURING -> {
                setActive(camera, false);
                player.sendOverlayMessage(Component.translatable("message.thelostcamera.already_capturing"));
            }
        }

        return InteractionResult.SUCCESS;
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
