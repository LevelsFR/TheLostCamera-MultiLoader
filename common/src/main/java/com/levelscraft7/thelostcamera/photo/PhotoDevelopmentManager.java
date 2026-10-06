package com.levelscraft7.thelostcamera.photo;

import com.levelscraft7.thelostcamera.album.PlayerAlbumStorage;
import com.levelscraft7.thelostcamera.album.StoredPhoto;
import com.levelscraft7.thelostcamera.data.PhotoData;
import com.levelscraft7.thelostcamera.data.RuinPhotoData;
import com.levelscraft7.thelostcamera.registry.ModBlocks;
import com.levelscraft7.thelostcamera.registry.ModDataComponents;
import com.levelscraft7.thelostcamera.registry.ModItems;
import com.levelscraft7.thelostcamera.platform.PlatformServices;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Small darkroom workflow that turns a stored original into a physical copy. */
public final class PhotoDevelopmentManager {
    private static final int DEVELOP_TICKS = 40;
    private static final int DARKROOM_RADIUS = 8;
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static MinecraftServer activeServer;

    private PhotoDevelopmentManager() {
    }

    public static void request(ServerPlayer player, UUID imageId, BlockPos darkroomPos) {
        if (PENDING.containsKey(player.getUUID())) {
            player.sendOverlayMessage(Component.translatable("message.thelostcamera.darkroom.busy"));
            return;
        }
        if (!validDarkroom(player, darkroomPos)) {
            player.sendOverlayMessage(Component.translatable("message.thelostcamera.darkroom.required"));
            return;
        }

        AccessiblePhoto accessible = accessiblePhoto(player, imageId);
        if (accessible == null) {
            player.sendOverlayMessage(Component.translatable("message.thelostcamera.darkroom.photo_missing"));
            return;
        }
        if (!hasPaper(player)) {
            player.sendOverlayMessage(Component.translatable("message.thelostcamera.darkroom.paper_required"));
            return;
        }

        PENDING.put(player.getUUID(), new Pending(accessible.data, accessible.ruinPhoto,
                player.level().getServer().getTickCount() + DEVELOP_TICKS));
        player.level().playSound(null, player.blockPosition(), SoundEvents.BREWING_STAND_BREW,
                SoundSource.BLOCKS, 0.55F, 0.75F);
        player.sendOverlayMessage(Component.translatable("message.thelostcamera.darkroom.developing"));
    }

    public static void onServerTick(MinecraftServer server) {
        if (activeServer != server) {
            activeServer = server;
            PENDING.clear();
        }
        Iterator<Map.Entry<UUID, Pending>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Pending> entry = iterator.next();
            if (server.getTickCount() < entry.getValue().completeAtTick) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                if (!consumePaper(player)) {
                    player.sendOverlayMessage(Component.translatable("message.thelostcamera.darkroom.paper_required"));
                    iterator.remove();
                    continue;
                }
                ItemStack print = new ItemStack(ModItems.PHOTOGRAPHIC_PRINT.get());
                print.set(ModDataComponents.PHOTO_DATA.get(), entry.getValue().data);
                if (entry.getValue().ruinPhoto != null) {
                    print.set(ModDataComponents.RUIN_PHOTO_DATA.get(), entry.getValue().ruinPhoto);
                }
                if (!player.addItem(print)) {
                    PlatformServices.dropItem(player, print, false);
                }
                player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                        SoundSource.PLAYERS, 0.55F, 1.35F);
                player.sendOverlayMessage(Component.translatable("message.thelostcamera.darkroom.complete"));
            }
            iterator.remove();
        }
    }

    private static AccessiblePhoto accessiblePhoto(ServerPlayer player, UUID imageId) {
        StoredPhoto stored = PlayerAlbumStorage.findPhoto(player, imageId);
        if (stored != null) {
            return new AccessiblePhoto(stored.data(), stored.ruinPhoto());
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            PhotoData data = stack.get(ModDataComponents.PHOTO_DATA.get());
            if (data != null && imageId.equals(data.imageId())) {
                return new AccessiblePhoto(data, stack.get(ModDataComponents.RUIN_PHOTO_DATA.get()));
            }
        }
        return null;
    }

    private static boolean validDarkroom(ServerPlayer player, BlockPos pos) {
        double maximumDistanceSqr = (double) DARKROOM_RADIUS * DARKROOM_RADIUS;
        if (pos == null || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > maximumDistanceSqr) {
            return false;
        }
        return player.level().isLoaded(pos) && player.level().getBlockState(pos).is(ModBlocks.DARKROOM_TABLE.get());
    }

    private static boolean hasPaper(ServerPlayer player) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).getItem() == ModItems.PHOTOGRAPHIC_PAPER.get()) {
                return true;
            }
        }
        return false;
    }

    private static boolean consumePaper(ServerPlayer player) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() == ModItems.PHOTOGRAPHIC_PAPER.get()) {
                stack.shrink(1);
                player.getInventory().setChanged();
                player.inventoryMenu.broadcastChanges();
                return true;
            }
        }
        return false;
    }

    private record AccessiblePhoto(PhotoData data, RuinPhotoData ruinPhoto) {
    }

    private record Pending(PhotoData data, RuinPhotoData ruinPhoto, int completeAtTick) {
    }
}



