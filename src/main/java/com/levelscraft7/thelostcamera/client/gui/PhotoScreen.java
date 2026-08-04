package com.levelscraft7.thelostcamera.client.gui;

import com.levelscraft7.thelostcamera.TheLostCamera;
import com.levelscraft7.thelostcamera.client.photo.ClientPhotoCache;
import com.levelscraft7.thelostcamera.data.PhotoData;
import com.levelscraft7.thelostcamera.registry.ModDataComponents;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
public final class PhotoScreen extends AbstractContainerScreen<DummyPhotoMenu> {
    private static final Identifier DARK_PANEL = Identifier.fromNamespaceAndPath(
            TheLostCamera.MOD_ID, "textures/gui/dark_panel.png"
    );
    private static final Identifier INFO_PANEL = Identifier.fromNamespaceAndPath(
            TheLostCamera.MOD_ID, "textures/gui/album_info_panel.png"
    );
    private static final int TEXT = 0xFF3A2A1D;
    private static final int GOLD = 0xFF9A6427;
    private static final int INFO_BUTTON_WIDTH = 62;
    private static final int INFO_BUTTON_HEIGHT = 22;
    private static final int INFO_BUTTON_MARGIN = 10;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private final PhotoData photoData;
    private final Screen parent;
    private boolean showMetadata;
    private Bounds photoBounds;

    public PhotoScreen(ItemStack stack) {
        this(stack, null);
    }

    public PhotoScreen(ItemStack stack, Screen parent) {
        this(stack.get(ModDataComponents.PHOTO_DATA), parent);
    }

    public PhotoScreen(PhotoData photoData, Screen parent) {
        super(new DummyPhotoMenu(), Minecraft.getInstance().player.getInventory(),
                Component.translatable("gui.thelostcamera.photograph"));
        this.photoData = photoData;
        this.parent = parent;
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

        float availableWidth = width * 0.94F;
        float availableHeight = height * 0.90F;
        float screenRatio = availableWidth / availableHeight;
        float imageRatio = (float) image.getWidth() / image.getHeight();

        int drawWidth;
        int drawHeight;
        if (screenRatio > imageRatio) {
            drawHeight = (int) availableHeight;
            drawWidth = (int) (drawHeight * imageRatio);
        } else {
            drawWidth = (int) availableWidth;
            drawHeight = (int) (drawWidth / imageRatio);
        }

        int x = (width - drawWidth) / 2;
        int y = (height - drawHeight) / 2;
        photoBounds = new Bounds(x, y, drawWidth, drawHeight);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F,
                drawWidth, drawHeight, image.getWidth(), image.getHeight(), image.getWidth(), image.getHeight());

        drawInfoButton(graphics, mouseX, mouseY);
        if (showMetadata) {
            drawMetadata(graphics);
        }
    }

    private void drawInfoButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Bounds button = infoButtonBounds();
        boolean hovered = button.contains(mouseX, mouseY);
        drawPanel(graphics, button.x, button.y, button.width, button.height);
        Component label = Component.literal(showMetadata ? "Hide" : "Infos");
        int color = hovered || showMetadata ? 0xFFE4A4 : 0xFFFFFF;
        graphics.text(font, label.getVisualOrderText(), button.x + (button.width - font.width(label)) / 2,
                button.y + (button.height - font.lineHeight) / 2, color, false);
    }

    private void drawMetadata(GuiGraphicsExtractor graphics) {
        int panelWidth = Math.min(340, width - 24);
        int panelHeight = photoData.hasRuin() ? 202 : 184;
        int x = Math.max(12, width - panelWidth - 12);
        int y = infoButtonBounds().y + INFO_BUTTON_HEIGHT + 6;
        drawInfoPanel(graphics, x, y, panelWidth, panelHeight);

        PhotoData.CameraSettings settings = photoData.cameraSettings();
        int lineX = x + 12;
        int lineY = y + 10;
        int textWidth = panelWidth - 24;

        lineY = drawLine(graphics, Component.literal("Informations de la photo"), lineX, lineY, textWidth, GOLD);
        lineY = drawLine(graphics, Component.literal("Photographe : " + photoData.photographer()), lineX, lineY, textWidth, TEXT);
        lineY = drawLine(graphics, Component.literal("Date : " + DATE_FORMAT.format(Instant.ofEpochMilli(photoData.capturedAt()))), lineX, lineY, textWidth, TEXT);
        lineY = drawLine(graphics, Component.literal("Dimension : " + photoData.dimension()), lineX, lineY, textWidth, TEXT);
        lineY = drawLine(graphics, Component.literal("Coordonnees : X " + photoData.x() + " / Y " + photoData.y() + " / Z " + photoData.z()), lineX, lineY, textWidth, TEXT);
        lineY = drawLine(graphics, Component.literal("Orientation : yaw " + Math.round(photoData.yaw()) + " deg / pitch "
                + Math.round(photoData.pitch()) + " deg"), lineX, lineY, textWidth, TEXT);
        lineY = drawLine(graphics, Component.literal("Focale : " + settings.focalLengthMm() + " mm"), lineX, lineY, textWidth, GOLD);
        lineY = drawLine(graphics, Component.literal("Diaph : f/" + formatAperture(settings.aperture())), lineX, lineY, textWidth, GOLD);
        lineY = drawLine(graphics, Component.literal("Vitesse : 1/" + settings.shutterDenominator() + " s"), lineX, lineY, textWidth, GOLD);
        lineY = drawLine(graphics, Component.literal("ISO : " + settings.iso()), lineX, lineY, textWidth, GOLD);
        lineY = drawLine(graphics, Component.literal("Meteo : " + weatherLabel(photoData.weather())), lineX, lineY, textWidth, TEXT);
        lineY = drawLine(graphics, Component.literal("Image : " + resolutionLabel() + " / PNG 4:3"), lineX, lineY, textWidth, TEXT);
        if (photoData.hasRuin()) {
            lineY = drawLine(graphics, Component.literal("Ruine : " + photoData.ruinId()), lineX, lineY, textWidth, GOLD);
            drawLine(graphics, Component.literal("Restauration : " + (photoData.restorationTriggered() ? "declenchee" : "non declenchee")),
                    lineX, lineY, textWidth, GOLD);
        }
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

    private String resolutionLabel() {
        if (photoData.hasResolution()) {
            return photoData.imageWidth() + " x " + photoData.imageHeight() + " px";
        }
        return "resolution inconnue";
    }

    private static String weatherLabel(String weather) {
        return switch (weather) {
            case "rain" -> "pluie";
            case "thunder" -> "orage";
            default -> "degage";
        };
    }

    private void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, DARK_PANEL, x, y, 0.0F, 0.0F,
                width, height, 1, 1, 1, 1);
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

    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) { }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) { }

    private record Bounds(int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
