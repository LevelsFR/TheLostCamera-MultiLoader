package com.levelscraft7.thelostcamera.item;

import com.levelscraft7.thelostcamera.platform.PlatformServices;

import com.levelscraft7.thelostcamera.album.AlbumInventory;
import com.levelscraft7.thelostcamera.album.PlayerAlbumStorage;
import com.levelscraft7.thelostcamera.data.PhotoData;
import com.levelscraft7.thelostcamera.network.payload.AlbumSnapshotPayload;
import com.levelscraft7.thelostcamera.network.payload.OpenAlbumPayload;
import com.levelscraft7.thelostcamera.registry.ModDataComponents;
import com.levelscraft7.thelostcamera.registry.ModItems;
import com.levelscraft7.thelostcamera.ruin.RuinCatalog;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A personal key to the owner's server-side photograph library. */
public final class PhotoAlbumItem extends Item {
    public PhotoAlbumItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        ItemStack album = serverPlayer.getItemInHand(hand);
        bindIfNeeded(album, serverPlayer);
        if (!isOwnedBy(album, serverPlayer)) {
            serverPlayer.sendOverlayMessage(Component.translatable("message.thelostcamera.album_not_yours"));
            return InteractionResult.FAIL;
        }

        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN,
                SoundSource.PLAYERS, 0.70F, 0.92F);
        PlatformServices.sendToPlayer(serverPlayer, new AlbumSnapshotPayload(PlayerAlbumStorage.load(serverPlayer)));
        PlatformServices.sendToPlayer(serverPlayer, new OpenAlbumPayload(hand == InteractionHand.MAIN_HAND));
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> tooltip,
            TooltipFlag flag
    ) {
        String owner = stack.get(ModDataComponents.ALBUM_OWNER_NAME.get());
        if (owner == null || owner.isBlank()) {
            tooltip.accept(Component.translatable("tooltip.thelostcamera.album.unbound").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.accept(Component.translatable("tooltip.thelostcamera.album.owner", owner).withStyle(ChatFormatting.GOLD));
            tooltip.accept(Component.translatable("tooltip.thelostcamera.album.personal").withStyle(ChatFormatting.GRAY));
        }
        tooltip.accept(Component.translatable("tooltip.thelostcamera.album.ruins_total", RuinCatalog.entries().size())
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("tooltip.thelostcamera.album.open").withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, display, tooltip, flag);
    }

    public static void bindIfNeeded(ItemStack album, ServerPlayer player) {
        if (album.getItem() != ModItems.PHOTO_ALBUM.get()) {
            return;
        }
        if (album.get(ModDataComponents.ALBUM_OWNER.get()) == null) {
            album.set(ModDataComponents.ALBUM_OWNER.get(), player.getUUID().toString());
            album.set(ModDataComponents.ALBUM_OWNER_NAME.get(), player.getName().getString());
            player.getInventory().setChanged();
        }
        if (isOwnedBy(album, player)) {
            String currentName = player.getName().getString();
            if (!currentName.equals(album.get(ModDataComponents.ALBUM_OWNER_NAME.get()))) {
                album.set(ModDataComponents.ALBUM_OWNER_NAME.get(), currentName);
                player.getInventory().setChanged();
            }
            migrateLegacyContents(album, player);
        }
    }

    private static void migrateLegacyContents(ItemStack album, ServerPlayer player) {
        AlbumInventory legacyInventory = new AlbumInventory(album);
        if (legacyInventory.photoCount() == 0) {
            return;
        }

        List<PhotoData> legacyPhotos = new ArrayList<>();
        for (ItemStack photograph : legacyInventory.photographs()) {
            PhotoData data = photograph.get(ModDataComponents.PHOTO_DATA.get());
            if (data != null) {
                legacyPhotos.add(data);
            }
        }

        int result = PlayerAlbumStorage.importLegacy(player, legacyPhotos);
        if (result >= 0) {
            album.set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
        }
    }

    public static boolean isOwnedBy(ItemStack album, ServerPlayer player) {
        if (album.getItem() != ModItems.PHOTO_ALBUM.get()) {
            return false;
        }
        String owner = album.get(ModDataComponents.ALBUM_OWNER.get());
        return owner != null && owner.equals(player.getUUID().toString());
    }

    public static boolean hasOwnedAlbum(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() == ModItems.PHOTO_ALBUM.get()) {
                bindIfNeeded(stack, player);
                if (isOwnedBy(stack, player)) {
                    return true;
                }
            }
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() == ModItems.PHOTO_ALBUM.get()) {
                bindIfNeeded(stack, player);
                if (isOwnedBy(stack, player)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean archivePhoto(ServerPlayer player, PhotoData data) {
        return PlayerAlbumStorage.add(player, data);
    }
}



