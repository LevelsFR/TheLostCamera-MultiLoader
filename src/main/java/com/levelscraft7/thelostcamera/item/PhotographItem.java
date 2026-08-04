package com.levelscraft7.thelostcamera.item;

import com.levelscraft7.thelostcamera.data.PhotoData;
import com.levelscraft7.thelostcamera.network.ClientUiBridge;
import com.levelscraft7.thelostcamera.registry.ModDataComponents;
import com.levelscraft7.thelostcamera.ruin.RuinCatalog;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Consumer;

public final class PhotographItem extends Item {
    private static final DateTimeFormatter CAPTURE_DATE_FORMAT = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public PhotographItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.level().isClientSide()) {
            ClientUiBridge.openPhoto(stack);
        }
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
        PhotoData data = stack.get(ModDataComponents.PHOTO_DATA);
        if (data == null) {
            super.appendHoverText(stack, context, display, tooltip, flag);
            return;
        }

        tooltip.accept(Component.translatable(
                "tooltip.thelostcamera.photographer",
                data.photographer()
        ).withStyle(ChatFormatting.GRAY));

        tooltip.accept(Component.translatable(
                "tooltip.thelostcamera.captured_at",
                CAPTURE_DATE_FORMAT.format(Instant.ofEpochMilli(data.capturedAt()))
        ).withStyle(ChatFormatting.DARK_GRAY));

        tooltip.accept(Component.translatable(
                "tooltip.thelostcamera.world_time",
                Math.floorDiv(data.worldTime(), 24_000L) + 1L,
                formatWorldTime(data.worldTime()),
                Component.translatable("tooltip.thelostcamera.weather." + data.weather())
        ).withStyle(ChatFormatting.DARK_GRAY));

        tooltip.accept(Component.translatable(
                "tooltip.thelostcamera.position",
                data.x(),
                data.y(),
                data.z(),
                readableDimension(data.dimension())
        ).withStyle(ChatFormatting.DARK_GRAY));

        tooltip.accept(Component.translatable(
                "tooltip.thelostcamera.angle",
                Component.translatable(directionKey(data.yaw())),
                formatOneDecimal(data.yaw()),
                formatOneDecimal(data.pitch())
        ).withStyle(ChatFormatting.GRAY));

        PhotoData.CameraSettings settings = data.cameraSettings();
        tooltip.accept(Component.translatable(
                "tooltip.thelostcamera.exposure",
                settings.focalLengthMm(),
                formatOneDecimal(settings.aperture()),
                settings.shutterDenominator(),
                settings.iso()
        ).withStyle(ChatFormatting.AQUA));

        if (data.hasResolution()) {
            tooltip.accept(Component.translatable(
                    "tooltip.thelostcamera.resolution",
                    data.imageWidth(),
                    data.imageHeight()
            ).withStyle(ChatFormatting.DARK_AQUA));
            tooltip.accept(Component.translatable(
                    "tooltip.thelostcamera.sensor_format"
            ).withStyle(ChatFormatting.DARK_AQUA));
            tooltip.accept(Component.translatable(
                    "tooltip.thelostcamera.format"
            ).withStyle(ChatFormatting.DARK_AQUA));
        }

        if (data.hasRuin()) {
            RuinCatalog.find(data.ruinId()).ifPresentOrElse(
                    entry -> {
                        tooltip.accept(Component.translatable(
                                "tooltip.thelostcamera.ruin",
                                Component.translatable(entry.nameKey())
                        ).withStyle(ChatFormatting.GOLD));
                        tooltip.accept(Component.translatable(
                                "tooltip.thelostcamera.ruin_classification",
                                Component.translatable(entry.size().translationKey()),
                                Component.translatable(entry.rarity().translationKey())
                        ).withStyle(ChatFormatting.YELLOW));
                    },
                    () -> tooltip.accept(Component.translatable(
                            "tooltip.thelostcamera.ruin",
                            data.ruinId()
                    ).withStyle(ChatFormatting.GOLD))
            );
        }

        super.appendHoverText(stack, context, display, tooltip, flag);
    }

    private static String formatWorldTime(long worldTime) {
        long dayTicks = Math.floorMod(worldTime, 24_000L);
        long totalMinutes = Math.floorMod(dayTicks * 60L / 1_000L + 360L, 1_440L);
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
    }

    private static String formatOneDecimal(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String readableDimension(String dimension) {
        int separator = dimension.lastIndexOf(" / ");
        if (separator >= 0 && dimension.endsWith("]")) {
            return dimension.substring(separator + 3, dimension.length() - 1);
        }
        return dimension;
    }

    private static String directionKey(float yaw) {
        int index = Math.floorMod(Math.round(yaw / 45.0F), 8);
        return switch (index) {
            case 0 -> "tooltip.thelostcamera.direction.south";
            case 1 -> "tooltip.thelostcamera.direction.south_west";
            case 2 -> "tooltip.thelostcamera.direction.west";
            case 3 -> "tooltip.thelostcamera.direction.north_west";
            case 4 -> "tooltip.thelostcamera.direction.north";
            case 5 -> "tooltip.thelostcamera.direction.north_east";
            case 6 -> "tooltip.thelostcamera.direction.east";
            default -> "tooltip.thelostcamera.direction.south_east";
        };
    }
}
