package com.levelscraft7.thelostcamera.client.gui;

import com.levelscraft7.thelostcamera.TheLostCamera;
import com.levelscraft7.thelostcamera.client.photo.ClientPhotoCache;
import com.levelscraft7.thelostcamera.data.PhotoData;
import com.levelscraft7.thelostcamera.registry.ModDataComponents;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Lossless fullscreen photograph viewer with an optional metadata overlay. */
public final class PhotoScreen extends Screen {
    private static final Identifier INFO_PANEL = Identifier.fromNamespaceAndPath(
            TheLostCamera.MOD_ID, "textures/gui/album_info_panel.png"
    );
    private static final int TEXT = 0xFF3A2A1D;
    private static final int GOLD = 0xFF9A6427;
    private static final int PHOTO_BORDER = 0xFFC8A879;
    private static final int FAVORITE_BORDER = 0xFFFFC857;
    private static final int INFO_BUTTON_WIDTH = 62;
    private static final int INFO_BUTTON_HEIGHT = 22;
    private static final int INFO_BUTTON_MARGIN = 10;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private final PhotoData photoData;
    private final Screen parent;
    private final boolean favorite;
    private boolean showMetadata;
    private Bounds photoBounds;

    public PhotoScreen(ItemStack stack) {
        this(stack, null);
    }

    public PhotoScreen(ItemStack stack, Screen parent) {
        this(stack.get(ModDataComponents.PHOTO_DATA.get()), parent);
    }

    public PhotoScreen(PhotoData photoData, Screen parent) {
        this(photoData, parent, false);
    }

    public PhotoScreen(PhotoData photoData, Screen parent, boolean favorite) {
        super(Component.translatable("gui.thelostcamera.photograph"));
        this.photoData = photoData;
        this.parent = parent;
        this.favorite = favorite;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractBlurredBackground(graphics);
        if (photoData == null) {
            drawCenteredText(graphics, Component.translatable("gui.thelostcamera.invalid_photograph"));
            return;
        }

        Identifier texture = ClientPhotoCache.getTexture(photoData.imageId());
        NativeImage image = ClientPhotoCache.getNativeImage(photoData.imageId());
        if (texture == null || image == null) {
            drawCenteredText(graphics, Component.translatable("gui.thelostcamera.loading_photograph"));
            drawInfoButton(graphics, mouseX, mouseY);
            if (showMetadata) {
                drawMetadata(graphics);
            }
            return;
        }

        int drawSize = Math.min((int) (width * 0.94F), (int) (height * 0.90F));
        int x = (width - drawSize) / 2;
        int y = (height - drawSize) / 2;
        int cropSize = Math.min(image.getWidth(), image.getHeight());
        float cropX = (image.getWidth() - cropSize) / 2.0F;
        float cropY = (image.getHeight() - cropSize) / 2.0F;
        photoBounds = new Bounds(x, y, drawSize, drawSize);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, cropX, cropY,
                drawSize, drawSize, cropSize, cropSize, image.getWidth(), image.getHeight());
        int outlineThickness = favorite ? 2 : 1;
        int outlineColor = favorite ? FAVORITE_BORDER : PHOTO_BORDER;
        for (int inset = 1; inset <= outlineThickness; inset++) {
            graphics.outline(x - inset, y - inset, drawSize + inset * 2, drawSize + inset * 2, outlineColor);
        }

        drawInfoButton(graphics, mouseX, mouseY);
        if (showMetadata) {
            drawMetadata(graphics);
        }
    }

    private void drawInfoButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Bounds button = infoButtonBounds();
        boolean hovered = button.contains(mouseX, mouseY);
        graphics.fill(button.x, button.y, button.x + button.width, button.y + button.height, 0xEFD1B27A);
        graphics.outline(button.x, button.y, button.width, button.height, 0xFF6B4528);
        Component label = Component.translatable(showMetadata
                ? "gui.thelostcamera.photo.hide_info"
                : "gui.thelostcamera.photo.show_info");
        int color = hovered || showMetadata ? GOLD : TEXT;
        graphics.text(font, label.getVisualOrderText(), button.x + (button.width - font.width(label)) / 2,
                button.y + (button.height - font.lineHeight) / 2 + 1, color, false);
    }


    private void drawMetadata(GuiGraphicsExtractor graphics) {
        int panelWidth = Math.min(360, width - 24);
        int panelHeight = photoData.hasRuin() ? 214 : 194;
        int x = Math.max(12, width - panelWidth - 12);
        int y = infoButtonBounds().y + INFO_BUTTON_HEIGHT + 6;
        drawInfoPanel(graphics, x, y, panelWidth, panelHeight);

        PhotoData.CameraSettings settings = photoData.cameraSettings();
        int lineX = x + 12;
        int lineY = y + 10;
        int textWidth = panelWidth - 24;

        lineY = drawLine(graphics, Component.translatable("gui.thelostcamera.photo.info_title"), lineX, lineY, textWidth, GOLD);
        lineY = drawLine(graphics, Component.translatable("gui.thelostcamera.photo.photographer", photoData.photographer()), lineX, lineY, textWidth, TEXT);
        lineY = drawLine(graphics, Component.translatable("gui.thelostcamera.photo.date",
                DATE_FORMAT.format(Instant.ofEpochMilli(photoData.capturedAt()))), lineX, lineY, textWidth, TEXT);
        lineY = drawLine(graphics, Component.translatable("gui.thelostcamera.photo.dimension", photoData.dimension()), lineX, lineY, textWidth, TEXT);
        lineY = drawLine(graphics, Component.translatable("gui.thelostcamera.photo.coordinates",
                photoData.x(), photoData.y(), photoData.z()), lineX, lineY, textWidth, TEXT);
        lineY = drawLine(graphics, Component.translatable("gui.thelostcamera.photo.orientation",
                Math.round(photoData.yaw()), Math.round(photoData.pitch())), lineX, lineY, textWidth, TEXT);
        lineY = drawLine(graphics, Component.translatable("gui.thelostcamera.photo.focal", settings.focalLengthMm()), lineX, lineY, textWidth, GOLD);
        lineY = drawLine(graphics, Component.translatable("gui.thelostcamera.photo.aperture", formatAperture(settings.aperture())), lineX, lineY, textWidth, GOLD);
        lineY = drawLine(graphics, Component.translatable("gui.thelostcamera.photo.shutter", settings.shutterDenominator()), lineX, lineY, textWidth, GOLD);
        lineY = drawLine(graphics, Component.translatable("gui.thelostcamera.photo.iso", settings.iso()), lineX, lineY, textWidth, GOLD);
        lineY = drawLine(graphics, Component.translatable("gui.thelostcamera.photo.weather",
                Component.translatable("tooltip.thelostcamera.weather." + photoData.weather())), lineX, lineY, textWidth, TEXT);
        lineY = drawLine(graphics, Component.translatable("gui.thelostcamera.photo.image", resolutionLabel()), lineX, lineY, textWidth, TEXT);
        if (photoData.hasRuin()) {
            lineY = drawLine(graphics, Component.translatable("gui.thelostcamera.photo.ruin", ruinLabel()), lineX, lineY, textWidth, GOLD);
            drawLine(graphics, Component.translatable("gui.thelostcamera.photo.restoration",
                            Component.translatable(photoData.restorationTriggered()
                                    ? "gui.thelostcamera.photo.restoration_triggered"
                                    : "gui.thelostcamera.photo.restoration_not_triggered")),
                    lineX, lineY, textWidth, GOLD);
        }
    }

    private Component resolutionLabel() {
        if (photoData.hasResolution()) {
            return Component.translatable("gui.thelostcamera.photo.resolution_value",
                    photoData.imageWidth(), photoData.imageHeight());
        }
        return Component.translatable("gui.thelostcamera.photo.resolution_unknown");
    }

    private Component ruinLabel() {
        return com.levelscraft7.thelostcamera.ruin.RuinCatalog.find(photoData.ruinId())
                .<Component>map(entry -> Component.translatable(entry.nameKey()))
                .orElse(Component.literal(photoData.ruinId()));
    }

    private int drawLine(GuiGraphicsExtractor graphics, Component line, int x, int y, int width, int color) {
        List<FormattedCharSequence> lines = font.split(line, width);
        for (int index = 0; index < lines.size(); index++) {
            graphics.text(font, lines.get(index), x, y + index * (font.lineHeight + 1), color, false);
        }
        return y + Math.max(1, lines.size()) * (font.lineHeight + 1) + 2;
    }

    private static String formatAperture(float aperture) {
        return String.format(Locale.ROOT, "%.1f", aperture);
    }


    private void drawInfoPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, INFO_PANEL, x, y, 0.0F, 0.0F,
                width, height, 180, 162, 180, 162);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && infoButtonBounds().contains(event.x(), event.y())) {
            showMetadata = !showMetadata;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private Bounds infoButtonBounds() {
        if (photoBounds != null) {
            int x = Math.min(width - INFO_BUTTON_WIDTH - INFO_BUTTON_MARGIN,
                    photoBounds.x + photoBounds.width - INFO_BUTTON_WIDTH - 6);
            int y = Math.max(INFO_BUTTON_MARGIN, photoBounds.y + 6);
            return new Bounds(x, y, INFO_BUTTON_WIDTH, INFO_BUTTON_HEIGHT);
        }
        return new Bounds(width - INFO_BUTTON_WIDTH - INFO_BUTTON_MARGIN, INFO_BUTTON_MARGIN,
                INFO_BUTTON_WIDTH, INFO_BUTTON_HEIGHT);
    }

    private void drawCenteredText(GuiGraphicsExtractor graphics, Component component) {
        graphics.text(font, component.getVisualOrderText(), (width - font.width(component)) / 2,
                height / 2, 0xFFFFFF, true);
    }

    @Override
    public void onClose() {
        ClientPhotoCache.clearFullResolutionTextures();
        if (parent != null) {
            minecraft.setScreenAndShow(parent);
        } else {
            super.onClose();
        }
    }

    private record Bounds(int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}


