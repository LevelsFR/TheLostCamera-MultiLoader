package com.levelscraft7.thelostcamera.client.gui;

import com.levelscraft7.thelostcamera.platform.PlatformServices;

import com.levelscraft7.thelostcamera.TheLostCamera;
import com.levelscraft7.thelostcamera.album.StoredPhoto;
import com.levelscraft7.thelostcamera.client.album.ClientAlbumState;
import com.levelscraft7.thelostcamera.client.photo.ClientPhotoCache;
import com.levelscraft7.thelostcamera.data.PhotoData;
import com.levelscraft7.thelostcamera.network.payload.ArchivePhotoPayload;
import com.levelscraft7.thelostcamera.network.payload.SetPhotoArchivedPayload;
import com.levelscraft7.thelostcamera.network.payload.SetPhotoFavoritePayload;
import com.levelscraft7.thelostcamera.registry.ModItems;
import com.levelscraft7.thelostcamera.ruin.RuinCatalog;
import com.levelscraft7.thelostcamera.ruin.RuinRarity;
import com.levelscraft7.thelostcamera.ruin.RuinSizeClass;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Photography-first personal gallery backed by the owner's server-side library. */
public final class AlbumScreen extends Screen {
    private static final Identifier BACKGROUND = id("textures/gui/photo_album.png");
    private static final Identifier CARD = id("textures/gui/album_card.png");

    private static final int TEXT = 0xFF3A2A1D;
    private static final int MUTED = 0xFF705E4A;
    private static final int GOLD = 0xFF9A6427;
    private static final int PHOTO_BORDER = 0xFFC8A879;
    private static final int FAVORITE_BORDER = 0xFFFFC857;
    private static final int EMPTY_PHOTO_BORDER = 0x66705E4A;
    private static final int RECORD_FILL = 0xFFD1B27A;
    private static final int RECORD_BORDER = 0xFF6B4528;
    private static final int DISABLED_TEXT = 0xFF928673;
    private static final int TOOLTIP_TEXT = 0xFFFFFFFF;
    private static final int MAX_TOOLTIP_WIDTH = 220;
    private static final int COLUMNS = 8;
    private static final int COLUMNS_PER_PAGE = 4;
    private static final int ROWS = 3;
    private static final int PAGE_SIZE = COLUMNS * ROWS;
    private static final int IMPORT_PAGE_SIZE = 16;
    private static final int CATALOG_PAGE_SIZE = 2;
    private static final int PAGE_MARGIN_X = 32;
    private static final int PAGE_TOP = 22;
    private static final int PAGE_BOTTOM = 24;
    private static final int FOOTER_OFFSET_Y = 10;
    private static final int BOTTOM_INSET = 10;
    private static final int NAV_ARROW_WIDTH = 34;
    private static final int NAV_LABEL_WIDTH = 78;
    private static final int NAV_GAP = 4;

    private final InteractionHand hand;
    private Mode mode = Mode.GALLERY;
    private GalleryFilter galleryFilter = GalleryFilter.ALL;
    private boolean newestFirst = true;
    private boolean favoritesOnly;
    private int page;
    private int importPage;
    private int catalogPage;
    private int selectedRuinIndex;
    private int entryDescriptionScroll;
    private RuinSizeClass sizeFilter;
    private RuinRarity rarityFilter;
    private Component hoverTooltip;

    public AlbumScreen(InteractionHand hand) {
        super(Component.translatable("gui.thelostcamera.album"));
        this.hand = hand;
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
        Layout layout = layout();
        hoverTooltip = null;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, layout.x, layout.y, 0.0F, 0.0F,
                layout.width, layout.height, 512, 256, 512, 256);

        if (!hasAlbumAvailable()) {
            drawCenteredText(graphics, Component.translatable("gui.thelostcamera.album_missing"));
            return;
        }

        switch (mode) {
            case GALLERY -> drawGallery(graphics, layout, mouseX, mouseY, false);
            case ARCHIVES -> drawGallery(graphics, layout, mouseX, mouseY, true);
            case IMPORT -> drawImport(graphics, layout, mouseX, mouseY);
            case CATALOG -> drawCatalog(graphics, layout, mouseX, mouseY);
            case ENTRY -> drawEntry(graphics, layout, mouseX, mouseY);
        }
        drawHoverTooltip(graphics, mouseX, mouseY);
    }

    private void drawGallery(GuiGraphicsExtractor graphics, Layout layout, int mouseX, int mouseY, boolean archived) {
        List<StoredPhoto> photos = filteredPhotos(archived);
        int maximumPage = maximumPage(photos.size(), PAGE_SIZE);
        page = Math.min(page, maximumPage);
        Component title = Component.translatable(archived
                ? "gui.thelostcamera.album.archives_title"
                : "gui.thelostcamera.album.gallery");
        Bounds leftPage = layout.leftPage();
        Bounds rightPage = layout.rightPage();
        drawCenteredInPage(graphics, title, leftPage.x, leftPage.width, layout.y + 18, TEXT);

        Bounds sort = new Bounds(leftPage.x - 3, layout.y + 36, 82, 21);
        Bounds filter = new Bounds(leftPage.x + 85, layout.y + 36, leftPage.width - 88, 21);
        Bounds favorites = new Bounds(rightPage.x + 3, layout.y + 36, 92, 21);
        Bounds switchSection = new Bounds(rightPage.x + 101, layout.y + 36, rightPage.width - 98, 21);
        drawButton(graphics, sort, Component.translatable(newestFirst
                        ? "gui.thelostcamera.album.sort_newest" : "gui.thelostcamera.album.sort_oldest"),
                sort.contains(mouseX, mouseY), false,
                Component.translatable("gui.thelostcamera.album.tooltip.sort"));
        drawButton(graphics, filter, Component.translatable("gui.thelostcamera.album.photo_filter",
                        Component.translatable(galleryFilter.translationKey)),
                filter.contains(mouseX, mouseY), galleryFilter != GalleryFilter.ALL,
                Component.translatable("gui.thelostcamera.album.tooltip.photo_filter"));
        drawButton(graphics, favorites, Component.translatable("gui.thelostcamera.album.favorites"),
                favorites.contains(mouseX, mouseY), favoritesOnly,
                Component.translatable("gui.thelostcamera.album.tooltip.favorites"));
        drawButton(graphics, switchSection, Component.translatable(archived
                        ? "gui.thelostcamera.album.gallery" : "gui.thelostcamera.album.collection"),
                switchSection.contains(mouseX, mouseY), false,
                Component.translatable(archived
                        ? "gui.thelostcamera.album.tooltip.switch_gallery"
                        : "gui.thelostcamera.album.tooltip.open_archive"));

        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int visibleIndex = row * COLUMNS + column;
                int photoIndex = page * PAGE_SIZE + visibleIndex;
                Bounds slot = gallerySlotBounds(layout, visibleIndex);
                StoredPhoto photo = photoIndex < photos.size() ? photos.get(photoIndex) : null;
                drawGallerySlot(graphics, slot, photo, slot.contains(mouseX, mouseY));
            }
        }

        int bottomY = layout.y + layout.height - 36;
        Bounds back = new Bounds(leftPage.x + BOTTOM_INSET, bottomY, 62, 23);
        Bounds importButton = new Bounds(back.x + back.width + 6, bottomY,
                leftPage.width - BOTTOM_INSET * 2 - back.width - 6, 23);
        PageControls controls = pageControls(rightPage, bottomY);
        drawButton(graphics, back, Component.translatable("gui.thelostcamera.album.collection"), back.contains(mouseX, mouseY), false,
                Component.translatable("gui.thelostcamera.album.tooltip.open_archive"));
        drawButton(graphics, importButton, Component.translatable("gui.thelostcamera.album.import"), importButton.contains(mouseX, mouseY), false,
                Component.translatable("gui.thelostcamera.album.tooltip.import"));
        drawPageControls(graphics, controls, page, maximumPage, mouseX, mouseY,
                "gui.thelostcamera.album.tooltip.previous_page",
                "gui.thelostcamera.album.tooltip.next_page");

        Component hint = slotHint(layout, mouseX, mouseY, archived, photos);
        if (sort.contains(mouseX, mouseY)) hint = Component.translatable("gui.thelostcamera.album.tooltip.sort");
        else if (filter.contains(mouseX, mouseY)) hint = Component.translatable("gui.thelostcamera.album.tooltip.photo_filter");
        else if (favorites.contains(mouseX, mouseY)) hint = Component.translatable("gui.thelostcamera.album.tooltip.favorites");
        else if (switchSection.contains(mouseX, mouseY)) hint = Component.translatable(archived
                ? "gui.thelostcamera.album.tooltip.switch_gallery"
                : "gui.thelostcamera.album.tooltip.switch_archives");
        else if (importButton.contains(mouseX, mouseY)) hint = Component.translatable("gui.thelostcamera.album.tooltip.import");
        drawFooterHint(graphics, layout, hint);
    }

    private void drawImport(GuiGraphicsExtractor graphics, Layout layout, int mouseX, int mouseY) {
        List<InventoryPhoto> photos = inventoryPhotos();
        int maximumPage = maximumPage(photos.size(), IMPORT_PAGE_SIZE);
        importPage = Math.min(importPage, maximumPage);
        Bounds leftPage = layout.leftPage();
        Bounds rightPage = layout.rightPage();
        drawCenteredTextAt(graphics, Component.translatable("gui.thelostcamera.album.import_title"),
                leftPage.x + leftPage.width / 2, layout.y + 18, TEXT);
        drawCenteredInPage(graphics, Component.translatable("gui.thelostcamera.album.import_subtitle", photos.size()),
                rightPage.x, rightPage.width, layout.y + 18, MUTED);

        Component hint = Component.translatable("gui.thelostcamera.album.import_hint");
        for (int index = 0; index < IMPORT_PAGE_SIZE; index++) {
            int photoIndex = importPage * IMPORT_PAGE_SIZE + index;
            Bounds bounds = importSlotBounds(layout, index);
            StoredPhoto temporary = photoIndex < photos.size()
                    ? new StoredPhoto(photos.get(photoIndex).data(), false, false) : null;
            boolean hovered = bounds.contains(mouseX, mouseY);
            drawGallerySlot(graphics, bounds, temporary, hovered);
            if (hovered && temporary != null) {
                hint = Component.translatable("gui.thelostcamera.album.tooltip.archive_photo");
                hoverTooltip = hint;
            }
        }

        int bottomY = layout.y + layout.height - 36;
        Bounds back = centeredButton(leftPage, bottomY, 118, 23);
        PageControls controls = pageControls(rightPage, bottomY);
        drawButton(graphics, back, Component.translatable("gui.thelostcamera.album.back_gallery"), back.contains(mouseX, mouseY), false,
                Component.translatable("gui.thelostcamera.album.tooltip.back_gallery"));
        drawPageControls(graphics, controls, importPage, maximumPage, mouseX, mouseY,
                "gui.thelostcamera.album.tooltip.previous_page",
                "gui.thelostcamera.album.tooltip.next_page");
        drawFooterHint(graphics, layout, hint);
    }

    private void drawCatalog(GuiGraphicsExtractor graphics, Layout layout, int mouseX, int mouseY) {
        List<RuinCatalog.Entry> entries = filteredEntries();
        int maximumPage = maximumPage(entries.size(), CATALOG_PAGE_SIZE);
        catalogPage = Math.min(catalogPage, maximumPage);
        Set<String> discovered = discoveredRuinIds();
        Bounds leftPage = layout.leftPage();
        Bounds rightPage = layout.rightPage();

        drawCenteredTextAt(graphics, Component.translatable("gui.thelostcamera.album.ruin_archive"),
                leftPage.x + leftPage.width / 2, layout.y + 17, TEXT);
        drawCenteredTextAt(graphics, Component.translatable("gui.thelostcamera.album.ruin_progress",
                        discovered.size(), RuinCatalog.entries().size()),
                rightPage.x + rightPage.width / 2, layout.y + 17, MUTED);

        Bounds sizeButton = new Bounds(leftPage.x, layout.y + 45, leftPage.width, 23);
        Bounds rarityButton = new Bounds(rightPage.x, layout.y + 45, rightPage.width, 23);
        drawButton(graphics, sizeButton, Component.translatable("gui.thelostcamera.album.filter_size",
                        sizeFilter == null ? Component.translatable("gui.thelostcamera.album.filter_all")
                                : Component.translatable(sizeFilter.translationKey())),
                sizeButton.contains(mouseX, mouseY), sizeFilter != null,
                Component.translatable("gui.thelostcamera.album.tooltip.size_filter"));
        drawButton(graphics, rarityButton, Component.translatable("gui.thelostcamera.album.filter_rarity",
                        rarityFilter == null ? Component.translatable("gui.thelostcamera.album.filter_all")
                                : Component.translatable(rarityFilter.translationKey())),
                rarityButton.contains(mouseX, mouseY), rarityFilter != null,
                Component.translatable("gui.thelostcamera.album.tooltip.rarity_filter"));

        for (int index = 0; index < CATALOG_PAGE_SIZE; index++) {
            int entryIndex = catalogPage * CATALOG_PAGE_SIZE + index;
            Bounds page = index == 0 ? leftPage : rightPage;
            Bounds card = catalogCardBounds(layout, page);
            RuinCatalog.Entry entry = entryIndex < entries.size() ? entries.get(entryIndex) : null;
            StoredPhoto preview = entry == null ? null : newestRuinPhoto(entry.id().toString());
            drawCatalogCard(graphics, card, entry, preview,
                    entry != null && discovered.contains(entry.id().toString()), card.contains(mouseX, mouseY), entryIndex);
        }

        int bottomY = layout.y + layout.height - 36;
        Bounds back = centeredButton(leftPage, bottomY, 76, 23);
        PageControls controls = pageControls(rightPage, bottomY);
        drawButton(graphics, back, Component.translatable("gui.thelostcamera.album.collection"), back.contains(mouseX, mouseY), false,
                Component.translatable("gui.thelostcamera.album.tooltip.open_archive"));
        drawPageControls(graphics, controls, catalogPage, maximumPage, mouseX, mouseY,
                "gui.thelostcamera.album.tooltip.previous_page",
                "gui.thelostcamera.album.tooltip.next_page");
        drawFooterHint(graphics, layout, Component.translatable("gui.thelostcamera.album.catalog_hint"));
    }

    private void drawEntry(GuiGraphicsExtractor graphics, Layout layout, int mouseX, int mouseY) {
        List<RuinCatalog.Entry> entries = filteredEntries();
        if (entries.isEmpty()) {
            drawCenteredText(graphics, Component.translatable("gui.thelostcamera.album.no_ruins"));
            return;
        }
        selectedRuinIndex = Math.floorMod(selectedRuinIndex, entries.size());
        RuinCatalog.Entry entry = entries.get(selectedRuinIndex);
        RuinPhotoPair pair = newestRuinPair(entry.id().toString());
        StoredPhoto before = pair.before();
        StoredPhoto restored = pair.restored();
        StoredPhoto legacy = newestRuinPhoto(entry.id().toString());
        if (before == null && restored == null && legacy != null) {
            before = legacy;
        }
        boolean unlocked = before != null || restored != null;

        Bounds leftPage = layout.leftPage();
        Bounds rightPage = layout.rightPage();
        drawCenteredInPage(graphics, unlocked ? Component.translatable(entry.nameKey())
                        : Component.translatable("gui.thelostcamera.album.unknown_structure", selectedRuinIndex + 1),
                leftPage.x, leftPage.width, layout.y + 17, TEXT);

        drawCenteredInPage(graphics, Component.translatable("gui.thelostcamera.album.entry_information"),
                rightPage.x, rightPage.width, layout.y + 17, TEXT);

        int photoLabelY = layout.y + 38;
        int photoTop = layout.y + 51;
        int recordTop = layout.y + 151;
        int recordBottom = layout.y + layout.height - 41;
        int recordHeight = Math.max(42, recordBottom - recordTop);
        int photoSize = Math.max(54, Math.min(92, recordTop - photoTop - 8));

        drawComparisonPhoto(graphics, leftPage, photoTop, photoSize, before,
                Component.translatable("gui.thelostcamera.album.before"), photoLabelY, unlocked);
        drawComparisonPhoto(graphics, rightPage, photoTop, photoSize, restored,
                Component.translatable("gui.thelostcamera.album.restored"), photoLabelY, unlocked);

        Bounds descriptionFrame = entryDescriptionBounds(layout);
        drawRecordPanel(graphics, descriptionFrame);
        if (unlocked) {
            drawScrollableRecord(graphics, entry, descriptionFrame);
        } else {
            drawWrapped(graphics, Component.translatable("gui.thelostcamera.album.entry_locked"),
                    descriptionFrame.x + 10, descriptionFrame.y + 10,
                    descriptionFrame.width - 20, MUTED, 4);
        }

        Bounds factsFrame = new Bounds(rightPage.x + 8, recordTop, rightPage.width - 16, recordHeight);
        drawRecordPanel(graphics, factsFrame);
        int factsX = factsFrame.x + 10;
        int factsWidth = factsFrame.width - 20;
        graphics.text(font, Component.translatable("gui.thelostcamera.album.entry_size",
                        Component.translatable(entry.size().translationKey())).getVisualOrderText(),
                factsX, factsFrame.y + 7, GOLD, false);
        graphics.text(font, Component.translatable("gui.thelostcamera.album.entry_rarity",
                        Component.translatable(entry.rarity().translationKey())).getVisualOrderText(),
                factsX, factsFrame.y + 20, GOLD, false);
        drawWrapped(graphics, Component.translatable(entry.biomeHintKey()), factsX, factsFrame.y + 35,
                factsWidth, MUTED, 2);

        int bottomY = layout.y + layout.height - 36;
        Bounds back = centeredButton(leftPage, bottomY, 116, 23);
        PageControls controls = pageControls(rightPage, bottomY);
        drawButton(graphics, back, Component.translatable("gui.thelostcamera.album.back_archive"), back.contains(mouseX, mouseY), false,
                Component.translatable("gui.thelostcamera.album.tooltip.back_archive"));
        drawEntryControls(graphics, controls, selectedRuinIndex, entries.size(), mouseX, mouseY);
        drawFooterHint(graphics, layout, Component.translatable(unlocked
                ? "gui.thelostcamera.album.entry_unlocked_hint"
                : "gui.thelostcamera.album.entry_locked_hint"));
    }

    private void drawComparisonPhoto(GuiGraphicsExtractor graphics, Bounds page, int photoTop, int photoSize,
                                     StoredPhoto photo, Component label, int labelY, boolean unlocked) {
        drawCenteredTextAt(graphics, label, page.x + page.width / 2, labelY, GOLD);
        Bounds image = new Bounds(page.x + (page.width - photoSize) / 2, photoTop, photoSize, photoSize);
        if (photo != null) {
            drawPhotoOutline(graphics, drawPhoto(graphics, photo.data(), image.x, image.y, image.width, image.height, true), GOLD);
        } else if (unlocked) {
            graphics.outline(image.x, image.y, image.width, image.height, EMPTY_PHOTO_BORDER);
            drawCenteredWrapped(graphics, Component.translatable("gui.thelostcamera.album.return_for_after"),
                    image, MUTED, 5);
        } else {
            graphics.outline(image.x, image.y, image.width, image.height, EMPTY_PHOTO_BORDER);
            drawCenteredTextAt(graphics, Component.literal("?"), image.x + image.width / 2,
                    image.y + image.height / 2 - font.lineHeight / 2, TEXT);
        }
    }

    private void drawGallerySlot(GuiGraphicsExtractor graphics, Bounds bounds, StoredPhoto photo, boolean hovered) {
        if (photo == null) {
            graphics.outline(bounds.x, bounds.y, bounds.width, bounds.height, EMPTY_PHOTO_BORDER);
            return;
        }
        if (hovered) {
            hoverTooltip = Component.translatable(photo.archived()
                    ? "gui.thelostcamera.album.tooltip.archived_slot"
                    : "gui.thelostcamera.album.tooltip.photo_slot");
        }
        Bounds imageArea = bounds.inset(2);
        Bounds photoBounds = drawPhoto(graphics, photo.data(),
                imageArea.x, imageArea.y, imageArea.width, imageArea.height, true);
        drawPhotoOutline(graphics, photoBounds,
                photo.favorite() ? FAVORITE_BORDER : hovered ? 0xFFE2C58E : PHOTO_BORDER,
                photo.favorite() ? 2 : 1);
    }

    private void drawCatalogCard(GuiGraphicsExtractor graphics, Bounds card, RuinCatalog.Entry entry,
                                 StoredPhoto preview, boolean unlocked, boolean hovered, int index) {
        drawPanelFrame(graphics, CARD, card, 160, 112);
        if (hovered) {
            hoverTooltip = Component.translatable("gui.thelostcamera.album.tooltip.open_entry");
        }
        if (entry == null) {
            return;
        }

        Component title = unlocked ? Component.translatable(entry.nameKey())
                : Component.translatable("gui.thelostcamera.album.unknown_short", index + 1);
        drawCenteredTextAt(graphics, title, card.x + card.width / 2, card.y + 7, TEXT);

        if (unlocked && preview != null && card.height >= 82) {
            int imageSize = Math.max(40, Math.min(82, card.height - 49));
            Bounds image = new Bounds(card.x + (card.width - imageSize) / 2, card.y + 23,
                    imageSize, imageSize);
            drawPhotoOutline(graphics, drawPhoto(graphics, preview.data(), image.x, image.y,
                    image.width, image.height, true), GOLD);
            drawCenteredTextAt(graphics, Component.translatable("gui.thelostcamera.album.documented"),
                    card.x + card.width / 2, card.y + card.height - 27, GOLD);
            int metaY = card.y + card.height - 14;
            drawCenteredTextAt(graphics, Component.translatable(entry.size().translationKey()),
                    card.x + card.width / 4, metaY, MUTED);
            drawCenteredTextAt(graphics, Component.translatable(entry.rarity().translationKey()),
                    card.x + card.width * 3 / 4, metaY, MUTED);
            return;
        }

        int unknownSize = Math.min(72, card.height - 48);
        Bounds unknown = new Bounds(card.x + (card.width - unknownSize) / 2, card.y + 25,
                unknownSize, unknownSize);
        graphics.outline(unknown.x, unknown.y, unknown.width, unknown.height, EMPTY_PHOTO_BORDER);
        drawCenteredTextAt(graphics, Component.literal("?"), unknown.x + unknown.width / 2,
                unknown.y + unknown.height / 2 - font.lineHeight / 2, TEXT);
        drawCenteredTextAt(graphics, unlocked
                        ? Component.translatable("gui.thelostcamera.album.documented")
                        : Component.literal("?"),
                card.x + card.width / 2, card.y + card.height - 27, unlocked ? GOLD : TEXT);
        int metaY = card.y + card.height - 14;
        drawCenteredTextAt(graphics, Component.translatable(entry.size().translationKey()),
                card.x + card.width / 4, metaY, MUTED);
        drawCenteredTextAt(graphics, Component.translatable(entry.rarity().translationKey()),
                card.x + card.width * 3 / 4, metaY, MUTED);
    }

    private Bounds drawPhoto(GuiGraphicsExtractor graphics, PhotoData data, int x, int y,
                           int maximumWidth, int maximumHeight, boolean thumbnail) {
        Identifier texture = thumbnail
                ? ClientPhotoCache.getThumbnailTexture(data.imageId())
                : ClientPhotoCache.getTexture(data.imageId());
        NativeImage image = thumbnail
                ? ClientPhotoCache.getThumbnailNativeImage(data.imageId())
                : ClientPhotoCache.getNativeImage(data.imageId());
        if (texture == null || image == null) {
            return new Bounds(x, y, maximumWidth, maximumHeight);
        }
        int drawSize = Math.min(maximumWidth, maximumHeight);
        int drawX = x + (maximumWidth - drawSize) / 2;
        int drawY = y + (maximumHeight - drawSize) / 2;
        int cropSize = Math.min(image.getWidth(), image.getHeight());
        float cropX = (image.getWidth() - cropSize) / 2.0F;
        float cropY = (image.getHeight() - cropSize) / 2.0F;
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, drawX, drawY, cropX, cropY,
                drawSize, drawSize, cropSize, cropSize, image.getWidth(), image.getHeight());
        return new Bounds(drawX, drawY, drawSize, drawSize);
    }

    private void drawPhotoOutline(GuiGraphicsExtractor graphics, Bounds bounds, int colorHint) {
        drawPhotoOutline(graphics, bounds, colorHint, 1);
    }

    private void drawPhotoOutline(GuiGraphicsExtractor graphics, Bounds bounds, int colorHint, int thickness) {
        for (int inset = 1; inset <= thickness; inset++) {
            graphics.outline(bounds.x - inset, bounds.y - inset,
                    bounds.width + inset * 2, bounds.height + inset * 2, colorHint);
        }
    }

    private void drawRecordPanel(GuiGraphicsExtractor graphics, Bounds bounds) {
        graphics.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, RECORD_FILL);
        graphics.outline(bounds.x, bounds.y, bounds.width, bounds.height, RECORD_BORDER);
    }

    private void drawScrollableRecord(GuiGraphicsExtractor graphics, RuinCatalog.Entry entry, Bounds bounds) {
        List<ColoredLine> lines = recordLines(entry, bounds.width - 20);
        int visibleLines = visibleRecordLines(bounds);
        int maximumScroll = Math.max(0, lines.size() - visibleLines);
        entryDescriptionScroll = Math.max(0, Math.min(entryDescriptionScroll, maximumScroll));
        int lineY = bounds.y + 7;
        for (int index = entryDescriptionScroll;
             index < Math.min(lines.size(), entryDescriptionScroll + visibleLines); index++) {
            ColoredLine line = lines.get(index);
            graphics.text(font, line.text(), bounds.x + 10, lineY, line.color(), false);
            lineY += font.lineHeight + 1;
        }
    }

    private List<ColoredLine> recordLines(RuinCatalog.Entry entry, int width) {
        List<ColoredLine> lines = new ArrayList<>();
        for (FormattedCharSequence line : font.split(Component.translatable(entry.descriptionKey()), width)) {
            lines.add(new ColoredLine(line, TEXT));
        }
        for (FormattedCharSequence line : font.split(Component.translatable(entry.echoKey()), width)) {
            lines.add(new ColoredLine(line, GOLD));
        }
        return lines;
    }

    private int maximumRecordScroll(RuinCatalog.Entry entry, Bounds bounds) {
        return Math.max(0, recordLines(entry, bounds.width - 20).size() - visibleRecordLines(bounds));
    }

    private int visibleRecordLines(Bounds bounds) {
        return Math.max(1, (bounds.height - 12) / (font.lineHeight + 1));
    }

    private void drawCenteredWrapped(GuiGraphicsExtractor graphics, Component component,
                                     Bounds bounds, int color, int maximumLines) {
        List<FormattedCharSequence> lines = font.split(component, Math.max(8, bounds.width - 10));
        int lineCount = Math.min(maximumLines, lines.size());
        int totalHeight = lineCount * font.lineHeight + Math.max(0, lineCount - 1);
        int y = bounds.y + (bounds.height - totalHeight) / 2;
        for (int index = 0; index < lineCount; index++) {
            FormattedCharSequence line = lines.get(index);
            graphics.text(font, line, bounds.x + (bounds.width - font.width(line)) / 2,
                    y + index * (font.lineHeight + 1), color, false);
        }
    }

    private void drawButton(GuiGraphicsExtractor graphics, Bounds bounds, Component label, boolean hovered,
                            boolean active, Component tooltip) {
        drawButton(graphics, bounds, label, hovered, active, true, tooltip);
    }

    private void drawButton(GuiGraphicsExtractor graphics, Bounds bounds, Component label, boolean hovered,
                            boolean active, boolean enabled, Component tooltip) {
        ButtonVariant variant = buttonVariant(bounds);
        drawNineSlice(graphics, variant.texture(active, hovered && enabled), bounds,
                variant.textureWidth, variant.textureHeight, variant.border);
        drawCenteredButtonText(graphics, label, bounds, variant.textInset, enabled ? TEXT : DISABLED_TEXT);
        if (hovered && tooltip != null) {
            hoverTooltip = tooltip;
        }
    }

    private ButtonVariant buttonVariant(Bounds bounds) {
        if (bounds.height >= 48) {
            return ButtonVariant.HERO;
        }
        if (bounds.height <= 22 || bounds.width <= 54) {
            return ButtonVariant.COMPACT;
        }
        if (bounds.width >= 150) {
            return ButtonVariant.WIDE;
        }
        return ButtonVariant.STANDARD;
    }

    private void drawNineSlice(GuiGraphicsExtractor graphics, Identifier texture, Bounds bounds,
                               int textureWidth, int textureHeight, int border) {
        int left = Math.min(border, bounds.width / 2);
        int right = Math.min(border, Math.max(0, bounds.width - left));
        int top = Math.min(border, bounds.height / 2);
        int bottom = Math.min(border, Math.max(0, bounds.height - top));
        int centreWidth = Math.max(0, bounds.width - left - right);
        int centreHeight = Math.max(0, bounds.height - top - bottom);
        int sourceCentreWidth = Math.max(1, textureWidth - border * 2);
        int sourceCentreHeight = Math.max(1, textureHeight - border * 2);

        blitNative(graphics, texture, bounds.x, bounds.y, 0, 0, left, top, textureWidth, textureHeight);
        blitNative(graphics, texture, bounds.x + bounds.width - right, bounds.y,
                textureWidth - right, 0, right, top, textureWidth, textureHeight);
        blitNative(graphics, texture, bounds.x, bounds.y + bounds.height - bottom,
                0, textureHeight - bottom, left, bottom, textureWidth, textureHeight);
        blitNative(graphics, texture, bounds.x + bounds.width - right, bounds.y + bounds.height - bottom,
                textureWidth - right, textureHeight - bottom, right, bottom, textureWidth, textureHeight);

        drawTiled(graphics, texture, bounds.x + left, bounds.y,
                centreWidth, top, border, 0, sourceCentreWidth, top, textureWidth, textureHeight);
        drawTiled(graphics, texture, bounds.x + left, bounds.y + bounds.height - bottom,
                centreWidth, bottom, border, textureHeight - bottom, sourceCentreWidth, bottom, textureWidth, textureHeight);
        drawTiled(graphics, texture, bounds.x, bounds.y + top,
                left, centreHeight, 0, border, left, sourceCentreHeight, textureWidth, textureHeight);
        drawTiled(graphics, texture, bounds.x + bounds.width - right, bounds.y + top,
                right, centreHeight, textureWidth - right, border, right, sourceCentreHeight, textureWidth, textureHeight);
        drawTiled(graphics, texture, bounds.x + left, bounds.y + top,
                centreWidth, centreHeight, border, border, sourceCentreWidth, sourceCentreHeight, textureWidth, textureHeight);
    }

    private void drawTiled(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int width, int height,
                           int sourceX, int sourceY, int tileWidth, int tileHeight, int textureWidth, int textureHeight) {
        if (width <= 0 || height <= 0 || tileWidth <= 0 || tileHeight <= 0) {
            return;
        }
        for (int drawY = 0; drawY < height; drawY += tileHeight) {
            int chunkHeight = Math.min(tileHeight, height - drawY);
            for (int drawX = 0; drawX < width; drawX += tileWidth) {
                int chunkWidth = Math.min(tileWidth, width - drawX);
                blitNative(graphics, texture, x + drawX, y + drawY,
                        sourceX, sourceY, chunkWidth, chunkHeight, textureWidth, textureHeight);
            }
        }
    }

    private void blitNative(GuiGraphicsExtractor graphics, Identifier texture, int x, int y,
                            int sourceX, int sourceY, int width, int height, int textureWidth, int textureHeight) {
        if (width <= 0 || height <= 0) {
            return;
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, sourceX, sourceY,
                width, height, width, height, textureWidth, textureHeight);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0 && event.button() != 1 && event.button() != 2) {
            return true;
        }
        if (!hasAlbumAvailable()) {
            return true;
        }

        Layout layout = layout();
        switch (mode) {
            case GALLERY -> clickGallery(layout, event.x(), event.y(), false, event.button());
            case ARCHIVES -> clickGallery(layout, event.x(), event.y(), true, event.button());
            case IMPORT -> {
                if (event.button() == 0) clickImport(layout, event.x(), event.y());
            }
            case CATALOG -> {
                if (event.button() == 0) clickCatalog(layout, event.x(), event.y());
            }
            case ENTRY -> {
                if (event.button() == 0) clickEntry(layout, event.x(), event.y());
            }
        }
        return true;
    }

    private boolean clickGallery(Layout layout, double x, double y, boolean archived, int mouseButton) {
        Bounds leftPage = layout.leftPage();
        Bounds rightPage = layout.rightPage();
        Bounds sort = new Bounds(leftPage.x - 3, layout.y + 36, 82, 21);
        Bounds filter = new Bounds(leftPage.x + 85, layout.y + 36, leftPage.width - 88, 21);
        Bounds favorites = new Bounds(rightPage.x + 3, layout.y + 36, 92, 21);
        Bounds switchSection = new Bounds(rightPage.x + 101, layout.y + 36, rightPage.width - 98, 21);

        List<StoredPhoto> photos = filteredPhotos(archived);
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int index = page * PAGE_SIZE + row * COLUMNS + column;
                Bounds slot = gallerySlotBounds(layout, row * COLUMNS + column);
                if (!slot.contains(x, y) || index >= photos.size()) {
                    continue;
                }

                StoredPhoto photo = photos.get(index);
                if (mouseButton == 0) {
                    minecraft.setScreenAndShow(new PhotoScreen(photo.data(), this, photo.favorite()));
                } else if (mouseButton == 1) {
                    PlatformServices.sendToServer(
                            new SetPhotoFavoritePayload(photo.data().imageId(), !photo.favorite()));
                } else {
                    PlatformServices.sendToServer(
                            new SetPhotoArchivedPayload(photo.data().imageId(), !photo.archived()));
                }
                playPageSound();
                return true;
            }
        }

        if (mouseButton != 0) {
            return false;
        }

        if (sort.contains(x, y)) {
            newestFirst = !newestFirst;
            page = 0;
            playPageSound();
            return true;
        }
        if (filter.contains(x, y)) {
            galleryFilter = galleryFilter.next();
            page = 0;
            playPageSound();
            return true;
        }
        if (favorites.contains(x, y)) {
            favoritesOnly = !favoritesOnly;
            page = 0;
            playPageSound();
            return true;
        }
        if (switchSection.contains(x, y)) {
            setMode(archived ? Mode.GALLERY : Mode.CATALOG);
            return true;
        }

        int bottomY = layout.y + layout.height - 36;
        Bounds back = new Bounds(leftPage.x + BOTTOM_INSET, bottomY, 62, 23);
        Bounds importButton = new Bounds(back.x + back.width + 6, bottomY,
                leftPage.width - BOTTOM_INSET * 2 - back.width - 6, 23);
        PageControls controls = pageControls(rightPage, bottomY);
        if (back.contains(x, y)) {
            setMode(Mode.CATALOG);
            return true;
        }
        if (importButton.contains(x, y)) {
            setMode(Mode.IMPORT);
            return true;
        }

        int maximumPage = maximumPage(photos.size(), PAGE_SIZE);
        if (controls.previous.contains(x, y) && page > 0) {
            page--;
            playPageSound();
            return true;
        }
        if (controls.next.contains(x, y) && page < maximumPage) {
            page++;
            playPageSound();
            return true;
        }
        return false;
    }

    private boolean clickImport(Layout layout, double x, double y) {
        List<InventoryPhoto> photos = inventoryPhotos();
        for (int index = 0; index < IMPORT_PAGE_SIZE; index++) {
            int photoIndex = importPage * IMPORT_PAGE_SIZE + index;
            Bounds bounds = importSlotBounds(layout, index);
            if (bounds.contains(x, y) && photoIndex < photos.size()) {
                PlatformServices.sendToServer(new ArchivePhotoPayload(hand == InteractionHand.MAIN_HAND,
                        photos.get(photoIndex).slot()));
                playPageSound();
                return true;
            }
        }
        Bounds leftPage = layout.leftPage();
        Bounds rightPage = layout.rightPage();
        int bottomY = layout.y + layout.height - 36;
        Bounds back = centeredButton(leftPage, bottomY, 118, 23);
        PageControls controls = pageControls(rightPage, bottomY);
        if (back.contains(x, y)) { setMode(Mode.GALLERY); return true; }
        int maximumPage = maximumPage(photos.size(), IMPORT_PAGE_SIZE);
        if (controls.previous.contains(x, y) && importPage > 0) { importPage--; playPageSound(); return true; }
        if (controls.next.contains(x, y) && importPage < maximumPage) { importPage++; playPageSound(); return true; }
        return false;
    }

    private boolean clickCatalog(Layout layout, double x, double y) {
        Bounds leftPage = layout.leftPage();
        Bounds rightPage = layout.rightPage();
        Bounds sizeButton = new Bounds(leftPage.x, layout.y + 45, leftPage.width, 23);
        Bounds rarityButton = new Bounds(rightPage.x, layout.y + 45, rightPage.width, 23);
        if (sizeButton.contains(x, y)) { sizeFilter = nextSize(sizeFilter); catalogPage = 0; playPageSound(); return true; }
        if (rarityButton.contains(x, y)) { rarityFilter = nextRarity(rarityFilter); catalogPage = 0; playPageSound(); return true; }

        List<RuinCatalog.Entry> entries = filteredEntries();
        for (int index = 0; index < CATALOG_PAGE_SIZE; index++) {
            int entryIndex = catalogPage * CATALOG_PAGE_SIZE + index;
            Bounds page = index == 0 ? leftPage : rightPage;
            Bounds card = catalogCardBounds(layout, page);
            if (card.contains(x, y) && entryIndex < entries.size()) {
                selectedRuinIndex = entryIndex;
                setMode(Mode.ENTRY);
                return true;
            }
        }
        int bottomY = layout.y + layout.height - 36;
        Bounds back = centeredButton(leftPage, bottomY, 76, 23);
        PageControls controls = pageControls(rightPage, bottomY);
        if (back.contains(x, y)) { setMode(Mode.GALLERY); return true; }
        int maximumPage = maximumPage(entries.size(), CATALOG_PAGE_SIZE);
        if (controls.previous.contains(x, y) && catalogPage > 0) { catalogPage--; playPageSound(); return true; }
        if (controls.next.contains(x, y) && catalogPage < maximumPage) { catalogPage++; playPageSound(); return true; }
        return false;
    }

    private boolean clickEntry(Layout layout, double x, double y) {
        List<RuinCatalog.Entry> entries = filteredEntries();
        Bounds leftPage = layout.leftPage();
        Bounds rightPage = layout.rightPage();
        int bottomY = layout.y + layout.height - 36;
        Bounds back = centeredButton(leftPage, bottomY, 116, 23);
        PageControls controls = pageControls(rightPage, bottomY);
        if (back.contains(x, y)) { setMode(Mode.CATALOG); return true; }
        if (controls.previous.contains(x, y) && entries.size() > 1) {
            selectedRuinIndex = Math.floorMod(selectedRuinIndex - 1, entries.size());
            entryDescriptionScroll = 0;
            playPageSound();
            return true;
        }
        if (controls.next.contains(x, y) && entries.size() > 1) {
            selectedRuinIndex = (selectedRuinIndex + 1) % entries.size();
            entryDescriptionScroll = 0;
            playPageSound();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mode == Mode.GALLERY || mode == Mode.ARCHIVES) {
            List<StoredPhoto> photos = filteredPhotos(mode == Mode.ARCHIVES);
            int previousPage = page;
            page = Math.max(0, Math.min(maximumPage(photos.size(), PAGE_SIZE), page + (scrollY < 0 ? 1 : -1)));
            if (page != previousPage) playPageSound();
            return true;
        }
        if (mode == Mode.ENTRY) {
            List<RuinCatalog.Entry> entries = filteredEntries();
            if (!entries.isEmpty()) {
                selectedRuinIndex = Math.floorMod(selectedRuinIndex, entries.size());
                Bounds description = entryDescriptionBounds(layout());
                if (description.contains(mouseX, mouseY)) {
                    int maximumScroll = maximumRecordScroll(entries.get(selectedRuinIndex), description);
                    entryDescriptionScroll = Math.max(0, Math.min(maximumScroll,
                            entryDescriptionScroll + (scrollY < 0 ? 1 : -1)));
                    return true;
                }
                selectedRuinIndex = Math.floorMod(selectedRuinIndex + (scrollY < 0 ? 1 : -1), entries.size());
                entryDescriptionScroll = 0;
                playPageSound();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private List<StoredPhoto> filteredPhotos(boolean archived) {
        Comparator<StoredPhoto> comparator = Comparator.comparingLong(entry -> entry.data().capturedAt());
        if (newestFirst) comparator = comparator.reversed();
        return ClientAlbumState.photos().stream()
                .filter(entry -> entry.archived() == archived)
                .filter(entry -> !favoritesOnly || entry.favorite())
                .filter(entry -> switch (galleryFilter) {
                    case ALL -> true;
                    case NORMAL -> !entry.data().hasRuin();
                    case RUINS -> entry.data().hasRuin();
                })
                .sorted(comparator)
                .toList();
    }

    private Set<String> discoveredRuinIds() {
        Set<String> result = new HashSet<>();
        for (StoredPhoto photo : ClientAlbumState.photos()) {
            if (photo.data().hasRuin()) result.add(photo.data().ruinId());
        }
        return result;
    }

    private StoredPhoto newestRuinPhoto(String ruinId) {
        return ClientAlbumState.photos().stream()
                .filter(entry -> entry.data().hasRuin() && ruinId.equals(entry.data().ruinId()))
                .max(Comparator.comparingLong(entry -> entry.data().capturedAt()))
                .orElse(null);
    }

    private RuinPhotoPair newestRuinPair(String ruinId) {
        List<StoredPhoto> candidates = ClientAlbumState.photos().stream()
                .filter(entry -> entry.data().hasRuin() && ruinId.equals(entry.data().ruinId()) && entry.ruinPhoto() != null)
                .sorted(Comparator.comparingLong((StoredPhoto entry) -> entry.data().capturedAt()).reversed())
                .toList();

        for (StoredPhoto candidate : candidates) {
            if (!candidate.ruinPhoto().isRestored()) {
                continue;
            }
            StoredPhoto before = candidates.stream()
                    .filter(other -> other.ruinPhoto().isBefore() && candidate.ruinPhoto().sameInstance(other.ruinPhoto()))
                    .findFirst().orElse(null);
            return new RuinPhotoPair(before, candidate);
        }
        for (StoredPhoto candidate : candidates) {
            if (candidate.ruinPhoto().isBefore()) {
                StoredPhoto restored = candidates.stream()
                        .filter(other -> other.ruinPhoto().isRestored() && candidate.ruinPhoto().sameInstance(other.ruinPhoto()))
                        .findFirst().orElse(null);
                return new RuinPhotoPair(candidate, restored);
            }
        }
        return new RuinPhotoPair(null, null);
    }

    private record RuinPhotoPair(StoredPhoto before, StoredPhoto restored) { }

    private List<RuinCatalog.Entry> filteredEntries() {
        return RuinCatalog.entries().stream()
                .filter(entry -> sizeFilter == null || entry.size() == sizeFilter)
                .filter(entry -> rarityFilter == null || entry.rarity() == rarityFilter)
                .toList();
    }

    private List<InventoryPhoto> inventoryPhotos() {
        if (minecraft.player == null) return List.of();
        List<InventoryPhoto> result = new ArrayList<>();
        for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            if (stack.getItem() == ModItems.PHOTOGRAPH.get()) {
                PhotoData data = stack.get(com.levelscraft7.thelostcamera.registry.ModDataComponents.PHOTO_DATA.get());
                if (data != null) result.add(new InventoryPhoto(slot, data));
            }
        }
        return List.copyOf(result);
    }

    private Component slotHint(Layout layout, int mouseX, int mouseY, boolean archived, List<StoredPhoto> photos) {
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int index = page * PAGE_SIZE + row * COLUMNS + column;
                Bounds slot = gallerySlotBounds(layout, row * COLUMNS + column);
                if (slot.contains(mouseX, mouseY) && index < photos.size()) {
                    return Component.translatable(archived
                            ? "gui.thelostcamera.album.tooltip.archived_slot"
                            : "gui.thelostcamera.album.tooltip.photo_slot");
                }
            }
        }
        return Component.translatable(archived
                ? "gui.thelostcamera.album.archives_hint"
                : "gui.thelostcamera.album.gallery_hint");
    }

    private boolean hasAlbumAvailable() {
        if (minecraft.player == null) return false;
        for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++) {
            if (minecraft.player.getInventory().getItem(slot).getItem() == ModItems.PHOTO_ALBUM.get()) {
                return true;
            }
        }
        return false;
    }

    private void setMode(Mode value) {
        mode = value;
        page = 0;
        entryDescriptionScroll = 0;
        playPageSound();
    }

    private static int maximumPage(int itemCount, int pageSize) {
        return itemCount <= 0 ? 0 : (itemCount - 1) / pageSize;
    }

    private static RuinSizeClass nextSize(RuinSizeClass current) {
        List<RuinSizeClass> available = RuinCatalog.entries().stream()
                .map(RuinCatalog.Entry::size)
                .distinct()
                .toList();
        if (available.isEmpty()) return null;
        if (current == null) return available.getFirst();
        int index = available.indexOf(current);
        return index < 0 || index + 1 >= available.size() ? null : available.get(index + 1);
    }

    private static RuinRarity nextRarity(RuinRarity current) {
        List<RuinRarity> available = RuinCatalog.entries().stream()
                .map(RuinCatalog.Entry::rarity)
                .distinct()
                .toList();
        if (available.isEmpty()) return null;
        if (current == null) return available.getFirst();
        int index = available.indexOf(current);
        return index < 0 || index + 1 >= available.size() ? null : available.get(index + 1);
    }

    private void playPageSound() {
        if (minecraft.level != null && minecraft.player != null) {
            minecraft.level.playLocalSound(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ(),
                    SoundEvents.BOOK_PAGE_TURN, SoundSource.MASTER, 0.8F,
                    0.95F + minecraft.level.getRandom().nextFloat() * 0.1F, false);
        }
    }

    private Layout layout() {
        int footerSpace = FOOTER_OFFSET_Y + font.lineHeight + 8;
        int maximumHeight = Math.max(176, height - footerSpace - 12);
        int drawWidth = Math.min(512, Math.max(352, width - 24));
        int drawHeight = drawWidth / 2;
        if (drawHeight > maximumHeight) {
            drawHeight = maximumHeight;
            drawWidth = drawHeight * 2;
        }
        int drawY = Math.max(6, (height - drawHeight - footerSpace) / 2);
        return new Layout((width - drawWidth) / 2, drawY, drawWidth, drawHeight);
    }

    private PageControls pageControls(Bounds page, int y) {
        int availableWidth = Math.max(92, page.width - BOTTOM_INSET * 2);
        int arrowWidth = Math.min(NAV_ARROW_WIDTH, Math.max(20, (availableWidth - 54 - NAV_GAP * 2) / 2));
        int labelWidth = Math.min(NAV_LABEL_WIDTH,
                Math.max(46, availableWidth - arrowWidth * 2 - NAV_GAP * 2));
        int totalWidth = arrowWidth * 2 + labelWidth + NAV_GAP * 2;
        int startX = page.x + (page.width - totalWidth) / 2;
        Bounds previous = new Bounds(startX, y, arrowWidth, 23);
        Bounds indicator = new Bounds(previous.x + previous.width + NAV_GAP, y, labelWidth, 23);
        Bounds next = new Bounds(indicator.x + indicator.width + NAV_GAP, y, arrowWidth, 23);
        return new PageControls(previous, indicator, next);
    }

    private Bounds centeredButton(Bounds page, int y, int preferredWidth, int height) {
        int buttonWidth = Math.max(24, Math.min(preferredWidth, page.width - BOTTOM_INSET * 2));
        return new Bounds(page.x + (page.width - buttonWidth) / 2, y, buttonWidth, height);
    }

    private void drawPageControls(GuiGraphicsExtractor graphics, PageControls controls, int currentPage,
                                  int maximumPage, int mouseX, int mouseY,
                                  String previousTooltipKey, String nextTooltipKey) {
        boolean hasPrevious = currentPage > 0;
        boolean hasNext = currentPage < maximumPage;
        drawButton(graphics, controls.previous, Component.literal("<"),
                controls.previous.contains(mouseX, mouseY), false, hasPrevious,
                Component.translatable(hasPrevious
                        ? previousTooltipKey
                        : "gui.thelostcamera.album.tooltip.first_page"));
        Component pageLabel = controls.indicator.width >= 64
                ? Component.translatable("gui.thelostcamera.album.page", currentPage + 1, maximumPage + 1)
                : Component.literal((currentPage + 1) + " / " + (maximumPage + 1));
        drawButton(graphics, controls.indicator, pageLabel,
                controls.indicator.contains(mouseX, mouseY), false, true, null);
        drawButton(graphics, controls.next, Component.literal(">"),
                controls.next.contains(mouseX, mouseY), false, hasNext,
                Component.translatable(hasNext
                        ? nextTooltipKey
                        : "gui.thelostcamera.album.tooltip.last_page"));
    }

    private void drawEntryControls(GuiGraphicsExtractor graphics, PageControls controls, int selectedIndex,
                                   int entryCount, int mouseX, int mouseY) {
        boolean multipleEntries = entryCount > 1;
        drawButton(graphics, controls.previous, Component.literal("<"),
                controls.previous.contains(mouseX, mouseY), false, multipleEntries,
                Component.translatable(multipleEntries
                        ? "gui.thelostcamera.album.tooltip.previous_entry"
                        : "gui.thelostcamera.album.tooltip.only_entry"));
        Component entryLabel = controls.indicator.width >= 70
                ? Component.translatable("gui.thelostcamera.album.entry_page", selectedIndex + 1, entryCount)
                : Component.literal((selectedIndex + 1) + " / " + entryCount);
        drawButton(graphics, controls.indicator, entryLabel,
                controls.indicator.contains(mouseX, mouseY), false, true, null);
        drawButton(graphics, controls.next, Component.literal(">"),
                controls.next.contains(mouseX, mouseY), false, multipleEntries,
                Component.translatable(multipleEntries
                        ? "gui.thelostcamera.album.tooltip.next_entry"
                        : "gui.thelostcamera.album.tooltip.only_entry"));
    }

    private void drawPanelFrame(GuiGraphicsExtractor graphics, Identifier texture, Bounds bounds,
                                int textureWidth, int textureHeight) {
        drawNineSlice(graphics, texture, bounds, textureWidth, textureHeight, 4);
    }

    private Bounds gallerySlotBounds(Layout layout, int visibleIndex) {
        Bounds page = visibleIndex < PAGE_SIZE / 2 ? layout.leftPage() : layout.rightPage();
        int indexOnPage = visibleIndex % (PAGE_SIZE / 2);
        return gridSlotBounds(page, indexOnPage, COLUMNS_PER_PAGE, ROWS,
                page.y + 43, layoutContentBottom(layout), 46);
    }

    private Bounds importSlotBounds(Layout layout, int visibleIndex) {
        Bounds page = visibleIndex < IMPORT_PAGE_SIZE / 2 ? layout.leftPage() : layout.rightPage();
        int indexOnPage = visibleIndex % (IMPORT_PAGE_SIZE / 2);
        return gridSlotBounds(page, indexOnPage, 4, 2, page.y + 48, layoutContentBottom(layout), 36);
    }

    private Bounds gridSlotBounds(Bounds page, int indexOnPage, int columns, int rows,
                                  int top, int bottom, int preferredSize) {
        int column = indexOnPage % columns;
        int row = indexOnPage / columns;
        int horizontalGap = Math.max(2, Math.min(4, page.width / 48));
        int verticalGap = Math.max(2, Math.min(rows >= 4 ? 4 : 12, Math.max(2, (bottom - top) / 18)));
        int availableWidth = Math.max(columns * 12, page.width - 8);
        int availableHeight = Math.max(rows * 10, bottom - top);
        int widthLimit = (availableWidth - horizontalGap * (columns - 1)) / columns;
        int heightLimit = (availableHeight - verticalGap * (rows - 1)) / rows;
        int slotSize = Math.max(10, Math.min(preferredSize, Math.min(widthLimit, heightLimit)));
        int gridWidth = slotSize * columns + horizontalGap * (columns - 1);
        int gridHeight = slotSize * rows + verticalGap * (rows - 1);
        int startX = page.x + (page.width - gridWidth) / 2;
        int startY = top + Math.max(0, (availableHeight - gridHeight) / 2);
        return new Bounds(startX + column * (slotSize + horizontalGap),
                startY + row * (slotSize + verticalGap), slotSize, slotSize);
    }

    private Bounds catalogCardBounds(Layout layout, Bounds page) {
        int top = page.y + 56;
        int bottom = layoutContentBottom(layout);
        return new Bounds(page.x + 10, top, Math.max(24, page.width - 20),
                Math.max(82, Math.min(136, bottom - top)));
    }

    private Bounds entryDescriptionBounds(Layout layout) {
        Bounds leftPage = layout.leftPage();
        int top = layout.y + 151;
        int bottom = layout.y + layout.height - 41;
        return new Bounds(leftPage.x + 8, top, leftPage.width - 16, Math.max(42, bottom - top));
    }

    private int layoutContentBottom(Layout layout) {
        return Math.max(layout.y + PAGE_TOP + 72, layout.y + layout.height - 40);
    }

    private void drawCenteredInPage(GuiGraphicsExtractor graphics, Component component, int pageX, int pageWidth, int y, int color) {
        List<FormattedCharSequence> lines = font.split(component, Math.max(8, pageWidth));
        if (lines.isEmpty()) {
            return;
        }
        FormattedCharSequence line = lines.getFirst();
        graphics.text(font, line, pageX + (pageWidth - font.width(line)) / 2, y, color, false);
    }

    private void drawCenteredTextAt(GuiGraphicsExtractor graphics, Component component, int centerX, int y, int color) {
        graphics.text(font, component.getVisualOrderText(), centerX - font.width(component) / 2, y, color, false);
    }

    private void drawCenteredButtonText(GuiGraphicsExtractor graphics, Component label, Bounds bounds, int horizontalInset, int color) {
        if (label.getString().isEmpty()) {
            return;
        }
        int textWidth = Math.max(8, bounds.width - horizontalInset * 2);
        List<FormattedCharSequence> lines = font.split(label, textWidth);
        if (lines.isEmpty()) {
            return;
        }
        FormattedCharSequence line = lines.getFirst();
        int x = bounds.x + (bounds.width - font.width(line)) / 2;
        int y = bounds.y + (bounds.height - font.lineHeight) / 2 + 1;
        graphics.text(font, line, x, y, color, false);
    }

    private void drawWrapped(GuiGraphicsExtractor graphics, Component component, int x, int y, int width, int color, int maximumLines) {
        List<FormattedCharSequence> lines = font.split(component, width);
        for (int index = 0; index < Math.min(maximumLines, lines.size()); index++) {
            graphics.text(font, lines.get(index), x, y + index * (font.lineHeight + 1), color, false);
        }
    }

    private void drawCenteredText(GuiGraphicsExtractor graphics, Component component) {
        graphics.text(font, component.getVisualOrderText(), (width - font.width(component)) / 2,
                height / 2, 0xFFFFFF, true);
    }

    private void drawFooterHint(GuiGraphicsExtractor graphics, Layout layout, Component hint) {
        if (hoverTooltip == null) {
            hoverTooltip = hint;
        }
    }

    private void drawHoverTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (hoverTooltip == null) {
            return;
        }

        int maxWidth = Math.max(100, Math.min(MAX_TOOLTIP_WIDTH, width - 24));
        List<FormattedCharSequence> lines = font.split(hoverTooltip, maxWidth);
        if (lines.isEmpty()) {
            return;
        }

        Layout layout = layout();
        int totalHeight = lines.size() * font.lineHeight + Math.max(0, lines.size() - 1) * 2;
        int y = Math.min(height - totalHeight - 4, layout.y + layout.height + FOOTER_OFFSET_Y);
        for (int index = 0; index < lines.size(); index++) {
            FormattedCharSequence line = lines.get(index);
            int x = (width - font.width(line)) / 2;
            graphics.text(font, line, x, y + index * (font.lineHeight + 2), TOOLTIP_TEXT, true);
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TheLostCamera.MOD_ID, path);
    }

    private enum Mode { GALLERY, ARCHIVES, IMPORT, CATALOG, ENTRY }
    private enum GalleryFilter {
        ALL("gui.thelostcamera.album.filter_all"),
        NORMAL("gui.thelostcamera.album.filter_normal"),
        RUINS("gui.thelostcamera.album.filter_ruins");
        private final String translationKey;
        GalleryFilter(String translationKey) { this.translationKey = translationKey; }
        GalleryFilter next() { return values()[(ordinal() + 1) % values().length]; }
    }
    private record InventoryPhoto(int slot, PhotoData data) { }
    private record ColoredLine(FormattedCharSequence text, int color) { }
    private record Bounds(int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        Bounds inset(int amount) {
            return new Bounds(x + amount, y + amount,
                    Math.max(1, width - amount * 2), Math.max(1, height - amount * 2));
        }
    }

    private record PageControls(Bounds previous, Bounds indicator, Bounds next) { }
    private enum ButtonVariant {
        COMPACT("album_button_compact", 64, 24, 4, 5),
        STANDARD("album_button", 128, 24, 4, 7),
        WIDE("album_button_wide", 192, 24, 4, 8),
        HERO("album_button_hero", 192, 72, 4, 10);

        private final Identifier normal;
        private final Identifier hover;
        private final Identifier active;
        private final int textureWidth;
        private final int textureHeight;
        private final int border;
        private final int textInset;

        ButtonVariant(String textureName, int textureWidth, int textureHeight, int border, int textInset) {
            this.normal = id("textures/gui/" + textureName + ".png");
            this.hover = id("textures/gui/" + textureName + "_hover.png");
            this.active = id("textures/gui/" + textureName + "_active.png");
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
            this.border = border;
            this.textInset = textInset;
        }

        private Identifier texture(boolean active, boolean hovered) {
            if (active) {
                return this.active;
            }
            return hovered ? hover : normal;
        }
    }
    private record Layout(int x, int y, int width, int height) {
        int pageWidth() { return width / 2 - PAGE_MARGIN_X - 18; }
        Bounds leftPage() { return new Bounds(x + PAGE_MARGIN_X, y + PAGE_TOP, pageWidth(), height - PAGE_TOP - PAGE_BOTTOM); }
        Bounds rightPage() { return new Bounds(x + width / 2 + 18, y + PAGE_TOP, pageWidth(), height - PAGE_TOP - PAGE_BOTTOM); }
    }
}



