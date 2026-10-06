package com.levelscraft7.thelostcamera.client.gui;

import com.levelscraft7.thelostcamera.platform.PlatformServices;

import com.levelscraft7.thelostcamera.TheLostCamera;
import com.levelscraft7.thelostcamera.album.StoredPhoto;
import com.levelscraft7.thelostcamera.client.album.ClientAlbumState;
import com.levelscraft7.thelostcamera.client.photo.ClientPhotoCache;
import com.levelscraft7.thelostcamera.network.payload.DevelopPrintPayload;
import com.levelscraft7.thelostcamera.registry.ModBlocks;
import com.levelscraft7.thelostcamera.registry.ModItems;
import com.levelscraft7.thelostcamera.ruin.RuinCatalog;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Dedicated, non-pausing darkroom interface for producing physical prints. */
public final class DarkroomScreen extends Screen {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(
            TheLostCamera.MOD_ID, "textures/gui/darkroom_table_redesigned.png"
    );

    private static final int GUI_W = 420;
    private static final int GUI_H = 280;
    private static final int PAGE_SIZE = 6;
    private static final int CARD_W = 68;
    private static final int CARD_H = 73;
    private static final int CARD_PHOTO_SIZE = 57;
    private static final int DEVELOP_TICKS = 40;
    private static final double DARKROOM_RADIUS = 8.0D;

    private static final int INK = 0xFF3C281A;
    private static final int MUTED_INK = 0xFF76583E;
    private static final int GOLD = 0xFFD5A656;
    private static final int HOVER_FILL = 0x35D5A656;

    private final BlockPos darkroomPos;
    private int page;
    private int selectedIndex = -1;
    private int localDevelopTicks;
    private Component hoverTooltip;

    public DarkroomScreen(BlockPos darkroomPos) {
        super(Component.translatable("gui.thelostcamera.darkroom.title"));
        this.darkroomPos = darkroomPos.immutable();
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
    public void tick() {
        super.tick();
        if (localDevelopTicks > 0) {
            localDevelopTicks--;
        }
        if (minecraft.player == null || minecraft.level == null
                || !minecraft.level.getBlockState(darkroomPos).is(ModBlocks.DARKROOM_TABLE.get())
                || minecraft.player.distanceToSqr(Vec3.atCenterOf(darkroomPos)) > DARKROOM_RADIUS * DARKROOM_RADIUS) {
            onClose();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractBlurredBackground(graphics);
        hoverTooltip = null;
        UiTransform transform = uiTransform();
        int localMouseX = transform.localX(mouseX);
        int localMouseY = transform.localY(mouseY);

        graphics.pose().pushMatrix();
        graphics.pose().translate(transform.x, transform.y);
        graphics.pose().scale(transform.scale, transform.scale);

        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, 0.0F, 0.0F,
                GUI_W, GUI_H, GUI_W, GUI_H, GUI_W, GUI_H);
        drawHeader(graphics);

        List<StoredPhoto> photos = photos();
        int maximumPage = maximumPage(photos.size());
        page = Mth.clamp(page, 0, maximumPage);
        if (selectedIndex >= photos.size()) {
            selectedIndex = -1;
        }

        drawCentered(graphics, Component.translatable("gui.thelostcamera.darkroom.select"),
                127, 58, INK, 220);
        if (photos.isEmpty()) {
            drawCentered(graphics, Component.translatable("gui.thelostcamera.darkroom.no_photos"),
                    127, 130, MUTED_INK, 210);
        } else {
            for (int visibleIndex = 0; visibleIndex < PAGE_SIZE; visibleIndex++) {
                int absoluteIndex = page * PAGE_SIZE + visibleIndex;
                if (absoluteIndex >= photos.size()) {
                    break;
                }
                drawPhotoCard(graphics, photos.get(absoluteIndex), absoluteIndex,
                        cardBounds(visibleIndex), localMouseX, localMouseY);
            }
        }

        int paper = paperCount();
        drawCenteredWrappedScaled(graphics,
                Component.translatable("gui.thelostcamera.darkroom.paper_count", paper),
                new Bounds(265, 16, 138, 25), paper > 0 ? INK : 0xFF9B4038, 0.86F);

        StoredPhoto selected = selectedPhoto(photos);
        Bounds preview = previewBounds();
        if (selected == null) {
            drawCentered(graphics, Component.translatable("gui.thelostcamera.darkroom.selected"),
                    preview.centerX(), preview.y + 54, MUTED_INK, preview.width - 12);
        } else {
            drawPhoto(graphics, selected, preview.inset(5));
            graphics.outline(preview.x, preview.y, preview.width, preview.height, GOLD);
        }

        boolean ready = selected != null && paper > 0 && localDevelopTicks <= 0;
        drawDevelopButton(graphics, ready, developBounds().contains(localMouseX, localMouseY));
        drawFooter(graphics, selected, ready, maximumPage, localMouseX, localMouseY);

        graphics.pose().popMatrix();
        drawHoverTooltip(graphics, mouseX, mouseY);
    }

    private void drawHeader(GuiGraphicsExtractor graphics) {
        drawCentered(graphics, title, 127, 29, INK, 220);
        drawCentered(graphics, Component.translatable("gui.thelostcamera.darkroom.subtitle"),
                127, 41, MUTED_INK, 220);
    }

    private void drawPhotoCard(GuiGraphicsExtractor graphics, StoredPhoto photo, int absoluteIndex,
                               Bounds card, int mouseX, int mouseY) {
        boolean selected = absoluteIndex == selectedIndex;
        boolean hovered = card.contains(mouseX, mouseY);
        Bounds photoArea = new Bounds(card.centerX() - CARD_PHOTO_SIZE / 2, card.y + 2,
                CARD_PHOTO_SIZE, CARD_PHOTO_SIZE);
        drawPhoto(graphics, photo, photoArea);

        int frameColor = selected ? GOLD : hovered ? 0xFF9A6427 : 0xFFC8A879;
        graphics.outline(card.x, card.y, card.width, card.height, frameColor);
        if (hovered) {
            graphics.fill(card.x + 1, card.y + 1, card.x + card.width - 1, card.y + card.height - 1, HOVER_FILL);
            hoverTooltip = photoLabel(photo);
        }

        Component label = photoLabel(photo);
        drawCenteredScaled(graphics, label, card.centerX(), card.y + 63,
                selected ? GOLD : INK, card.width - 6, 0.86F);
    }

    private void drawPhoto(GuiGraphicsExtractor graphics, StoredPhoto photo, Bounds area) {
        Identifier texture = ClientPhotoCache.getThumbnailTexture(photo.data().imageId());
        NativeImage image = ClientPhotoCache.getThumbnailNativeImage(photo.data().imageId());
        if (texture == null || image == null) {
            drawCentered(graphics, Component.literal("…"), area.centerX(),
                    area.y + (area.height - font.lineHeight) / 2 + 1, MUTED_INK, area.width);
            return;
        }

        int drawSize = Math.min(area.width, area.height);
        int drawX = area.x + (area.width - drawSize) / 2;
        int drawY = area.y + (area.height - drawSize) / 2;
        int cropSize = Math.min(image.getWidth(), image.getHeight());
        float cropX = (image.getWidth() - cropSize) / 2.0F;
        float cropY = (image.getHeight() - cropSize) / 2.0F;
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, drawX, drawY, cropX, cropY,
                drawSize, drawSize, cropSize, cropSize, image.getWidth(), image.getHeight());
    }

    private void drawDevelopButton(GuiGraphicsExtractor graphics, boolean ready, boolean hovered) {
        Bounds bounds = developBounds();
        if (ready && hovered) {
            graphics.fill(bounds.x + 2, bounds.y + 2, bounds.x + bounds.width - 2,
                    bounds.y + bounds.height - 2, HOVER_FILL);
        }
        graphics.outline(bounds.x, bounds.y, bounds.width, bounds.height,
                ready ? GOLD : 0xFF9A8064);
        drawCenteredInBounds(graphics, Component.translatable("gui.thelostcamera.darkroom.develop"),
                bounds, ready ? INK : 0xFF897B68, bounds.width - 12);

        if (localDevelopTicks > 0) {
            int completed = DEVELOP_TICKS - localDevelopTicks;
            int progressWidth = Math.round((bounds.width - 10) * completed / (float) DEVELOP_TICKS);
            graphics.fill(bounds.x + 5, bounds.y + bounds.height - 4,
                    bounds.x + 5 + progressWidth, bounds.y + bounds.height - 2, GOLD);
        }
    }

    private void drawFooter(GuiGraphicsExtractor graphics, StoredPhoto selected, boolean ready,
                            int maximumPage, int mouseX, int mouseY) {
        Bounds previous = previousBounds();
        Bounds next = nextBounds();
        drawFooterButton(graphics, previous, Component.literal("‹"), page > 0,
                previous.contains(mouseX, mouseY));
        drawCentered(graphics, Component.translatable("gui.thelostcamera.darkroom.page", page + 1, maximumPage + 1),
                127, 242, INK, 126);
        drawFooterButton(graphics, next, Component.literal("›"), page < maximumPage,
                next.contains(mouseX, mouseY));

        Component selectedName = selected == null
                ? Component.translatable("gui.thelostcamera.darkroom.selected")
                : photoLabel(selected);
        drawCenteredScaled(graphics, selectedName, 334, 218,
                selected == null ? MUTED_INK : INK, 138, 0.9F);
        if (selected != null && new Bounds(265, 214, 138, 14).contains(mouseX, mouseY)) {
            hoverTooltip = selectedName;
        }

        Component status;
        if (localDevelopTicks > 0) {
            status = Component.translatable("message.thelostcamera.darkroom.developing");
        } else if (ready) {
            status = Component.translatable("gui.thelostcamera.darkroom.ready");
        } else if (paperCount() <= 0) {
            status = Component.translatable("gui.thelostcamera.darkroom.no_paper");
        } else {
            status = Component.translatable("gui.thelostcamera.darkroom.hint");
        }
        drawCenteredWrappedScaled(graphics, status, new Bounds(265, 231, 138, 36), MUTED_INK, 0.86F);
    }

    private void drawFooterButton(GuiGraphicsExtractor graphics, Bounds bounds, Component label,
                                  boolean enabled, boolean hovered) {
        if (enabled && hovered) {
            graphics.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, HOVER_FILL);
        }
        graphics.outline(bounds.x, bounds.y, bounds.width, bounds.height,
                enabled ? (hovered ? GOLD : 0xFF8B6847) : 0xFFB5A58E);
        drawCenteredInBounds(graphics, label, bounds,
                enabled ? INK : 0xFF9C8A73, bounds.width - 4);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) {
            return super.mouseClicked(event, doubleClick);
        }

        UiTransform transform = uiTransform();
        int mouseX = transform.localX(event.x());
        int mouseY = transform.localY(event.y());
        if (mouseX < 0 || mouseY < 0 || mouseX >= GUI_W || mouseY >= GUI_H) {
            return super.mouseClicked(event, doubleClick);
        }

        List<StoredPhoto> photos = photos();
        for (int visibleIndex = 0; visibleIndex < PAGE_SIZE; visibleIndex++) {
            int absoluteIndex = page * PAGE_SIZE + visibleIndex;
            if (absoluteIndex >= photos.size()) {
                break;
            }
            if (cardBounds(visibleIndex).contains(mouseX, mouseY)) {
                selectedIndex = absoluteIndex;
                playUiSound(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 0.35F, 1.15F);
                return true;
            }
        }

        int maximumPage = maximumPage(photos.size());
        if (previousBounds().contains(mouseX, mouseY) && page > 0) {
            page--;
            selectedIndex = -1;
            playUiSound(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 0.35F, 1.0F);
            return true;
        }
        if (nextBounds().contains(mouseX, mouseY) && page < maximumPage) {
            page++;
            selectedIndex = -1;
            playUiSound(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 0.35F, 1.0F);
            return true;
        }

        StoredPhoto selected = selectedPhoto(photos);
        if (developBounds().contains(mouseX, mouseY) && selected != null
                && paperCount() > 0 && localDevelopTicks <= 0) {
            PlatformServices.sendToServer(new DevelopPrintPayload(selected.data().imageId(), darkroomPos));
            localDevelopTicks = DEVELOP_TICKS;
            playUiSound(net.minecraft.sounds.SoundEvents.BREWING_STAND_BREW, 0.5F, 0.8F);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private List<StoredPhoto> photos() {
        List<StoredPhoto> result = new ArrayList<>(ClientAlbumState.photos());
        result.sort(Comparator.comparingLong((StoredPhoto photo) -> photo.data().capturedAt()).reversed());
        return result;
    }

    private StoredPhoto selectedPhoto(List<StoredPhoto> photos) {
        return selectedIndex >= 0 && selectedIndex < photos.size() ? photos.get(selectedIndex) : null;
    }

    private Component photoLabel(StoredPhoto photo) {
        if (photo.data().hasRuin()) {
            return RuinCatalog.find(photo.data().ruinId())
                    .<Component>map(entry -> Component.translatable(entry.nameKey()))
                    .orElseGet(() -> Component.literal(readableIdentifier(photo.data().ruinId())));
        }
        String photographer = photo.data().photographer();
        return photographer == null || photographer.isBlank()
                ? Component.literal("Photo")
                : Component.literal(photographer);
    }

    private static String readableIdentifier(String identifier) {
        int separator = identifier.indexOf(':');
        String path = separator >= 0 ? identifier.substring(separator + 1) : identifier;
        String[] words = path.split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private int paperCount() {
        if (minecraft.player == null) {
            return 0;
        }
        int count = 0;
        for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            if (stack.getItem() == ModItems.PHOTOGRAPHIC_PAPER.get()) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void drawCentered(GuiGraphicsExtractor graphics, Component component, int centerX, int y,
                              int color, int maximumWidth) {
        String value = component.getString();
        if (font.width(value) > maximumWidth) {
            String ellipsis = "…";
            value = font.plainSubstrByWidth(value, Math.max(1, maximumWidth - font.width(ellipsis))) + ellipsis;
        }
        graphics.text(font, value, centerX - font.width(value) / 2, y, color, false);
    }

    private void drawCenteredInBounds(GuiGraphicsExtractor graphics, Component component, Bounds bounds,
                                      int color, int maximumWidth) {
        drawCentered(graphics, component, bounds.centerX(),
                bounds.y + (bounds.height - font.lineHeight) / 2 + 1, color, maximumWidth);
    }

    private void drawCenteredScaled(GuiGraphicsExtractor graphics, Component component, int centerX, int y,
                                    int color, int maximumWidth, float scale) {
        String value = component.getString();
        int unscaledWidth = Math.max(1, Mth.floor(maximumWidth / scale));
        if (font.width(value) > unscaledWidth) {
            String ellipsis = "\u2026";
            value = font.plainSubstrByWidth(value,
                    Math.max(1, unscaledWidth - font.width(ellipsis))) + ellipsis;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, value, -font.width(value) / 2, 0, color, false);
        graphics.pose().popMatrix();
    }

    /** Draws every wrapped line; instructional copy must never be replaced by an ellipsis. */
    private void drawCenteredWrappedScaled(GuiGraphicsExtractor graphics, Component component, Bounds bounds,
                                           int color, float scale) {
        int wrapWidth = Math.max(8, Mth.floor((bounds.width - 4) / scale));
        List<FormattedCharSequence> lines = font.split(component, wrapWidth);
        float lineStep = font.lineHeight + 1.0F;
        float totalHeight = lines.size() * lineStep - 1.0F;
        float startY = bounds.y + Math.max(0.0F, (bounds.height - totalHeight * scale) / 2.0F);
        graphics.pose().pushMatrix();
        graphics.pose().translate(bounds.centerX(), startY);
        graphics.pose().scale(scale, scale);
        for (int index = 0; index < lines.size(); index++) {
            FormattedCharSequence line = lines.get(index);
            graphics.text(font, line, -font.width(line) / 2, Mth.floor(index * lineStep), color, false);
        }
        graphics.pose().popMatrix();
    }

    private void drawHoverTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (hoverTooltip == null) {
            return;
        }
        int maximumWidth = Math.max(100, Math.min(220, width - 24));
        List<FormattedCharSequence> lines = font.split(hoverTooltip, maximumWidth);
        int contentWidth = lines.stream().mapToInt(font::width).max().orElse(0);
        int boxWidth = contentWidth + 10;
        int boxHeight = lines.size() * (font.lineHeight + 1) + 7;
        int x = Mth.clamp(mouseX + 12, 4, Math.max(4, width - boxWidth - 4));
        int y = Mth.clamp(mouseY + 12, 4, Math.max(4, height - boxHeight - 4));
        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xF018100A);
        graphics.outline(x, y, boxWidth, boxHeight, 0xFFD5A656);
        for (int index = 0; index < lines.size(); index++) {
            graphics.text(font, lines.get(index), x + 5, y + 4 + index * (font.lineHeight + 1),
                    0xFFF3E4C1, false);
        }
    }

    private void playUiSound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        if (minecraft.player != null) {
            minecraft.player.playSound(sound, volume, pitch);
        }
    }

    private UiTransform uiTransform() {
        float scale = Math.min(1.0F, Math.min((width - 12.0F) / GUI_W, (height - 12.0F) / GUI_H));
        scale = Math.max(0.1F, scale);
        int drawWidth = Math.round(GUI_W * scale);
        int drawHeight = Math.round(GUI_H * scale);
        return new UiTransform((width - drawWidth) / 2, (height - drawHeight) / 2, scale);
    }

    private static int maximumPage(int photoCount) {
        return photoCount <= 0 ? 0 : (photoCount - 1) / PAGE_SIZE;
    }

    private static Bounds cardBounds(int visibleIndex) {
        int column = visibleIndex % 3;
        int row = visibleIndex / 3;
        return new Bounds(20 + column * 74, 72 + row * 80, CARD_W, CARD_H);
    }

    private static Bounds previewBounds() {
        return new Bounds(274, 49, 120, 120);
    }

    private static Bounds developBounds() {
        return new Bounds(270, 181, 128, 27);
    }

    private static Bounds previousBounds() {
        return new Bounds(18, 235, 32, 23);
    }

    private static Bounds nextBounds() {
        return new Bounds(204, 235, 32, 23);
    }

    private record UiTransform(int x, int y, float scale) {
        int localX(double screenX) {
            return Mth.floor((screenX - x) / scale);
        }

        int localY(double screenY) {
            return Mth.floor((screenY - y) / scale);
        }
    }

    private record Bounds(int x, int y, int width, int height) {
        boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }

        int centerX() {
            return x + width / 2;
        }

        Bounds inset(int amount) {
            return new Bounds(x + amount, y + amount,
                    Math.max(1, width - amount * 2), Math.max(1, height - amount * 2));
        }
    }
}



