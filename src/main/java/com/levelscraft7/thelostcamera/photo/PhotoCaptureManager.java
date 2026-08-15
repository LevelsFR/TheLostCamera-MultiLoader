package com.levelscraft7.thelostcamera.photo;

import com.levelscraft7.thelostcamera.TheLostCamera;
import com.levelscraft7.thelostcamera.config.ModConfig;
import com.levelscraft7.thelostcamera.data.PhotoData;
import com.levelscraft7.thelostcamera.data.RuinPhotoData;
import com.levelscraft7.thelostcamera.album.PlayerAlbumStorage;
import com.levelscraft7.thelostcamera.item.PhotoAlbumItem;
import com.levelscraft7.thelostcamera.network.payload.CaptureRequestPayload;
import com.levelscraft7.thelostcamera.registry.ModDataComponents;
import com.levelscraft7.thelostcamera.registry.ModItems;
import com.levelscraft7.thelostcamera.ruin.RuinDetector;
import com.levelscraft7.thelostcamera.ruin.RestorationRotation;
import com.levelscraft7.thelostcamera.ruin.VirtualRuinRestorationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class PhotoCaptureManager {
    private static final Map<UUID, PendingCapture> PENDING = new HashMap<>();
    private static final Map<UUID, CaptureClock> LAST_CAPTURE = new HashMap<>();
    private static final long CAPTURE_TIMEOUT_MILLIS = 30_000L;
    private static MinecraftServer activeServer;

    private PhotoCaptureManager() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (activeServer != server) {
            activeServer = server;
            PENDING.clear();
            LAST_CAPTURE.clear();
            return;
        }
        if (server.getTickCount() % 20 != 0 || PENDING.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, PendingCapture>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            PendingCapture pending = iterator.next().getValue();
            if (pending.server != server || now - pending.createdAtMillis <= CAPTURE_TIMEOUT_MILLIS) {
                continue;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(pending.playerId);
            if (player == null && !pending.frameConfirmed) {
                // Keep a tiny unconfirmed entry so the plate can still be refunded if the player reconnects.
                continue;
            }

            iterator.remove();
            if (player != null) {
                refundPlate(player, pending);
            }
        }
    }

    public static CaptureResult beginCapture(ServerPlayer player) {
        prepareForCurrentServer(player);
        if (hasPendingCapture(player)) {
            return CaptureResult.ALREADY_CAPTURING;
        }

        MinecraftServer server = player.level().getServer();
        long now = player.level().getGameTime();
        CaptureClock previous = LAST_CAPTURE.get(player.getUUID());
        if (previous != null
                && previous.server == server
                && now >= previous.gameTick
                && now - previous.gameTick < ModConfig.PHOTO_COOLDOWN_TICKS.get()) {
            return CaptureResult.COOLDOWN;
        }

        boolean plateConsumed = !player.getAbilities().instabuild;
        if (!consumePlate(player)) {
            return CaptureResult.NO_PLATE;
        }

        RuinDetector.FramedRuin ruin = RuinDetector.findFramedRuin(player);
        UUID imageId = UUID.randomUUID();
        BlockPos playerPos = player.blockPosition();
        ResourceKey<Level> dimension = player.level().dimension();
        BlockPos anchorPos = ruin == null ? null : ruin.anchorPos().immutable();
        String ruinId = ruin == null ? "" : ruin.ruinId();
        RestorationRotation ruinRotation = ruin == null ? null : ruin.rotation();
        boolean ruinAlreadyRestored = ruin != null && ruin.alreadyRestored();
        long worldTime = player.level().getDefaultClockTime();

        PhotoData photoData = new PhotoData(
                imageId,
                player.getName().getString(),
                System.currentTimeMillis(),
                worldTime,
                dimension.toString(),
                playerPos.getX(),
                playerPos.getY(),
                playerPos.getZ(),
                normalizeYaw(player.getYRot()),
                player.getXRot(),
                cameraSettingsFor(player, worldTime),
                weatherFor(player),
                0,
                0,
                ruinId,
                ruin != null && !ruinAlreadyRestored
        );

        PENDING.put(imageId, new PendingCapture(
                player.getUUID(),
                server,
                dimension,
                anchorPos,
                ruinRotation,
                ruinAlreadyRestored,
                photoData,
                ModConfig.MAX_PHOTO_BYTES.get(),
                plateConsumed,
                System.currentTimeMillis()
        ));
        LAST_CAPTURE.put(player.getUUID(), new CaptureClock(server, now));

        PacketDistributor.sendToPlayer(player, new CaptureRequestPayload(imageId));
        return ruin == null ? CaptureResult.NORMAL_PHOTO : CaptureResult.RUIN_PHOTO;
    }

    public static void confirmCapturedFrame(
            ServerPlayer player,
            UUID imageId,
            PhotoData.CameraSettings cameraSettings
    ) {
        PendingCapture pending = PENDING.get(imageId);
        if (pending == null
                || pending.server != player.level().getServer()
                || !pending.playerId.equals(player.getUUID())
                || pending.frameConfirmed) {
            return;
        }
        pending.photoData = pending.photoData.withCameraSettings(sanitizeCameraSettings(cameraSettings));
        pending.frameConfirmed = true;
        player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.SPYGLASS_USE,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.42F, 1.65F);
        player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.24F, 1.85F);
        try {
            startRestoration(player, pending);
        } catch (RuntimeException exception) {
            TheLostCamera.LOGGER.error("Failed to start early restoration for photograph {}", imageId, exception);
        }
    }

    public static void acceptChunk(
            ServerPlayer player,
            UUID imageId,
            int offset,
            int totalLength,
            int imageWidth,
            int imageHeight,
            byte[] bytes
    ) {
        PendingCapture pending = PENDING.get(imageId);
        if (pending == null
                || pending.server != player.level().getServer()
                || !pending.playerId.equals(player.getUUID())) {
            return;
        }

        if (!pending.accept(offset, totalLength, imageWidth, imageHeight, bytes)) {
            cancel(imageId, player, "message.thelostcamera.invalid_photo");
            return;
        }

        if (!pending.complete()) {
            return;
        }

        byte[] image = pending.imageBytes();
        if (!looksLikePng(image) || !PhotoStorage.validatePng(image, pending.imageWidth, pending.imageHeight)) {
            cancel(imageId, player, "message.thelostcamera.invalid_photo");
            return;
        }

        try {
            PhotoStorage.save(player, imageId, image);
            storeOrGivePhoto(player, pending, pending.photoData.withResolution(pending.imageWidth, pending.imageHeight));
            PENDING.remove(imageId);
        } catch (IOException exception) {
            TheLostCamera.LOGGER.error("Failed to save photograph {}", imageId, exception);
            cancel(imageId, player, "message.thelostcamera.photo_save_failed");
            return;
        }

        try {
            if (!pending.frameConfirmed) {
                startRestoration(player, pending);
            }
        } catch (RuntimeException exception) {
            TheLostCamera.LOGGER.error(
                    "Failed to start restoration for photograph {} / ruin {} at {}",
                    imageId,
                    pending.photoData.ruinId(),
                    pending.anchorPos,
                    exception
            );
        }

        player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.35F, 1.48F);
        player.sendOverlayMessage(Component.translatable("message.thelostcamera.photo_saved"));
    }

    public static void requestPhoto(ServerPlayer player, UUID imageId, boolean thumbnail) {
        PhotoStorage.sendToClient(player, imageId, thumbnail);
    }

    public static void cancelCapture(ServerPlayer player, UUID imageId) {
        PendingCapture pending = PENDING.get(imageId);
        if (pending == null
                || pending.server != player.level().getServer()
                || !pending.playerId.equals(player.getUUID())) {
            return;
        }
        cancel(imageId, player, "message.thelostcamera.capture_failed");
    }

    private static PhotoData.CameraSettings sanitizeCameraSettings(PhotoData.CameraSettings settings) {
        if (settings == null) {
            return PhotoData.CameraSettings.DEFAULT;
        }
        return new PhotoData.CameraSettings(
                Math.max(35, Math.min(135, settings.focalLengthMm())),
                Math.max(18, Math.min(160, settings.apertureTenths())),
                Math.max(8, Math.min(2_000, settings.shutterDenominator())),
                Math.max(100, Math.min(6_400, settings.iso()))
        );
    }

    private static PhotoData.CameraSettings cameraSettingsFor(ServerPlayer player, long worldTime) {
        int focal = currentFocalLength(player);
        long timeOfDay = Math.floorMod(worldTime, 24_000L);
        boolean night = timeOfDay >= 12_500L && timeOfDay <= 23_000L;
        boolean storm = player.level().isThundering();
        boolean rain = player.level().isRaining();

        if (night || storm) {
            return new PhotoData.CameraSettings(focal, 18, 40, 800);
        }
        if (rain) {
            return new PhotoData.CameraSettings(focal, 22, 80, 400);
        }
        int shutter = focal >= 100 ? 250 : focal >= 70 ? 160 : 125;
        return new PhotoData.CameraSettings(focal, 28, shutter, 100);
    }

    private static int currentFocalLength(ServerPlayer player) {
        for (net.minecraft.world.InteractionHand hand : net.minecraft.world.InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() == ModItems.LOST_CAMERA.get()) {
                Integer focal = stack.get(ModDataComponents.CAMERA_FOCAL_MM);
                return focal == null ? 35 : Math.max(35, Math.min(135, focal));
            }
        }
        return 35;
    }

    private static String weatherFor(ServerPlayer player) {
        if (player.level().isThundering()) {
            return "thunder";
        }
        if (player.level().isRaining()) {
            return "rain";
        }
        return "clear";
    }

    private static float normalizeYaw(float yaw) {
        float normalized = yaw % 360.0F;
        return normalized < 0.0F ? normalized + 360.0F : normalized;
    }

    private static void prepareForCurrentServer(ServerPlayer player) {
        MinecraftServer currentServer = player.level().getServer();
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, PendingCapture>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            PendingCapture pending = iterator.next().getValue();
            boolean differentServerForPlayer = pending.playerId.equals(player.getUUID())
                    && pending.server != currentServer;
            boolean expiredForPlayer = pending.playerId.equals(player.getUUID())
                    && pending.server == currentServer
                    && now - pending.createdAtMillis > CAPTURE_TIMEOUT_MILLIS;

            if (!differentServerForPlayer && !expiredForPlayer) {
                continue;
            }

            iterator.remove();
            if (expiredForPlayer) {
                refundPlate(player, pending);
            }
        }

        CaptureClock previous = LAST_CAPTURE.get(player.getUUID());
        if (previous != null && previous.server != currentServer) {
            LAST_CAPTURE.remove(player.getUUID());
        }
    }

    private static boolean hasPendingCapture(ServerPlayer player) {
        return PENDING.values().stream().anyMatch(pending ->
                pending.server == player.level().getServer() && pending.playerId.equals(player.getUUID())
        );
    }

    private static void startRestoration(ServerPlayer player, PendingCapture pending) {
        if (pending.anchorPos == null) {
            return;
        }

        ServerLevel level = pending.server.getLevel(pending.dimension);
        if (level == null) {
            return;
        }

        if (pending.ruinAlreadyRestored) {
            VirtualRuinRestorationManager.markCompleted(level, pending.anchorPos, pending.photoData.ruinId());
            return;
        }

        if (VirtualRuinRestorationManager.begin(level, pending.anchorPos, pending.photoData.ruinId(), pending.ruinRotation)) {
            player.sendOverlayMessage(Component.translatable("message.thelostcamera.restoration_started"));
        }
    }

    private static void storeOrGivePhoto(ServerPlayer player, PendingCapture pending, PhotoData photoData) {
        RuinPhotoData ruinPhoto = pending.anchorPos == null ? null : new RuinPhotoData(
                pending.dimension.toString(),
                pending.anchorPos.getX(),
                pending.anchorPos.getY(),
                pending.anchorPos.getZ(),
                pending.ruinAlreadyRestored ? "restored" : "before"
        );

        ItemStack photograph = new ItemStack(ModItems.PHOTOGRAPH.get());
        photograph.set(ModDataComponents.PHOTO_DATA, photoData);
        if (ruinPhoto != null) {
            photograph.set(ModDataComponents.RUIN_PHOTO_DATA, ruinPhoto);
        }

        boolean archived = PhotoAlbumItem.hasOwnedAlbum(player)
                && PlayerAlbumStorage.add(player, photoData, ruinPhoto);
        if (archived) {
            player.sendSystemMessage(Component.translatable("message.thelostcamera.photo_archived_automatically"));
        }

        if (!archived && !player.addItem(photograph)) {
            Containers.dropItemStack(
                    player.level(),
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    photograph
            );
        }

        if (photoData.restorationTriggered() && !archived) {
            player.sendSystemMessage(Component.translatable("message.thelostcamera.historical_photo_unarchived"));
        }
    }

    private static boolean consumePlate(ServerPlayer player) {
        if (player.getAbilities().instabuild) {
            return true;
        }

        ItemStack offhand = player.getOffhandItem();
        if (offhand.getItem() == ModItems.PHOTOGRAPHIC_PLATE.get()) {
            offhand.shrink(1);
            return true;
        }

        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() == ModItems.PHOTOGRAPHIC_PLATE.get()) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikePng(byte[] bytes) {
        return bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A;
    }

    private static void cancel(UUID imageId, ServerPlayer player, String messageKey) {
        PendingCapture pending = PENDING.remove(imageId);
        if (pending != null && pending.server == player.level().getServer()) {
            refundPlate(player, pending);
        }
        player.sendOverlayMessage(Component.translatable(messageKey));
    }

    private static void refundPlate(ServerPlayer player, PendingCapture pending) {
        if (!pending.plateConsumed || pending.frameConfirmed) {
            return;
        }
        ItemStack plate = new ItemStack(ModItems.PHOTOGRAPHIC_PLATE.get());
        if (!player.addItem(plate)) {
            Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), plate);
        }
    }

    public enum CaptureResult {
        NORMAL_PHOTO,
        RUIN_PHOTO,
        NO_PLATE,
        COOLDOWN,
        ALREADY_CAPTURING
    }

    private record CaptureClock(MinecraftServer server, long gameTick) {
    }

    private static final class PendingCapture {
        private final UUID playerId;
        private final MinecraftServer server;
        private final ResourceKey<Level> dimension;
        private final BlockPos anchorPos;
        private final RestorationRotation ruinRotation;
        private final boolean ruinAlreadyRestored;
        private PhotoData photoData;
        private final int maximumLength;
        private final boolean plateConsumed;
        private final long createdAtMillis;
        private byte[] imageBytes;
        private int nextOffset;
        private int imageWidth;
        private int imageHeight;
        private boolean frameConfirmed;

        private PendingCapture(
                UUID playerId,
                MinecraftServer server,
                ResourceKey<Level> dimension,
                BlockPos anchorPos,
                RestorationRotation ruinRotation,
                boolean ruinAlreadyRestored,
                PhotoData photoData,
                int maximumLength,
                boolean plateConsumed,
                long createdAtMillis
        ) {
            this.playerId = playerId;
            this.server = server;
            this.dimension = dimension;
            this.anchorPos = anchorPos;
            this.ruinRotation = ruinRotation;
            this.ruinAlreadyRestored = ruinAlreadyRestored;
            this.photoData = photoData;
            this.maximumLength = maximumLength;
            this.plateConsumed = plateConsumed;
            this.createdAtMillis = createdAtMillis;
        }

        private boolean accept(
                int offset,
                int totalLength,
                int width,
                int height,
                byte[] bytes
        ) {
            if (totalLength <= 0 || totalLength > maximumLength || bytes.length == 0 || bytes.length > 28_000) {
                return false;
            }
            int maximumDimension = ModConfig.PHOTO_MAX_DIMENSION.get();
            if (width <= 0 || height <= 0 || width > maximumDimension || height > maximumDimension) {
                return false;
            }
            if (offset < 0 || offset != nextOffset || offset + bytes.length > totalLength) {
                return false;
            }
            if (imageBytes == null) {
                imageBytes = new byte[totalLength];
                imageWidth = width;
                imageHeight = height;
            } else if (imageBytes.length != totalLength || imageWidth != width || imageHeight != height) {
                return false;
            }

            System.arraycopy(bytes, 0, imageBytes, offset, bytes.length);
            nextOffset += bytes.length;
            return true;
        }

        private boolean complete() {
            return imageBytes != null && nextOffset == imageBytes.length;
        }

        private byte[] imageBytes() {
            return imageBytes;
        }
    }
}
