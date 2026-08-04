package com.levelscraft7.thelostcamera.client.gui;

import com.levelscraft7.thelostcamera.TheLostCamera;
import com.levelscraft7.thelostcamera.album.StoredPhoto;
import com.levelscraft7.thelostcamera.client.album.ClientAlbumState;
import com.levelscraft7.thelostcamera.client.photo.ClientPhotoCache;
import com.levelscraft7.thelostcamera.data.PhotoData;
import com.levelscraft7.thelostcamera.network.payload.ArchivePhotoPayload;
import com.levelscraft7.thelostcamera.network.payload.RequestAlbumPayload;
import com.levelscraft7.thelostcamera.network.payload.SetPhotoArchivedPayload;
import com.levelscraft7.thelostcamera.network.payload.SetPhotoFavoritePayload;
import com.levelscraft7.thelostcamera.registry.ModItems;
import com.levelscraft7.thelostcamera.ruin.RuinCatalog;
import com.levelscraft7.thelostcamera.ruin.RuinRarity;
import com.levelscraft7.thelostcamera.ruin.RuinSizeClass;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Personal album hub backed by the owner's server-side library. */
public final class AlbumScreen extends AbstractContainerScreen<DummyPhotoMenu> {
    private static final Identifier BACKGROUND = id("textures/gui/photo_album.png");
    private static final Identifier CARD = id("textures/gui/album_card.png");
    private static final Identifier INFO_PANEL = id("textures/gui/album_info_panel.png");
    private static final Identifier PHOTO_FRAME = id("textures/gui/album_photo_frame.png");
    private static final Identifier FRAME_DARK = id("textures/gui/album_frame_dark.png");
    private static final Identifier FRAME_GOLD = id("textures/gui/album_frame_gold.png");

    private static final int TEXT = 0xFF3A2A1D;
    private static final int MUTED = 0xFF705E4A;
    private static final int GOLD = 0xFF9A6427;
    private static final int DISABLED_TEXT = 0xFF928673;
    private static final int TOOLTIP_TEXT = 0xFFFFFFFF;
    private static final int MAX_TOOLTIP_WIDTH = 220;
    private static final int COLUMNS = 8;
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
    private Mode mode = Mode.HOME;
    private GalleryFilter galleryFilter = GalleryFilter.ALL;
    private boolean newestFirst = true;
    private boolean favoritesOnly;
    private int page;
    private int importPage;
    private int catalogPage;
    private int selectedRuinIndex;
    private RuinSizeClass sizeFilter;
    private RuinRarity rarityFilter;
    private Component hoverTooltip;

    public AlbumScreen(InteractionHand hand) {
        super(new DummyPhotoMenu(), Minecraft.getInstance().player.getInventory(),
                Component.translatable("gui.thelostcamera.album"));
        this.hand = hand;
        ClientPacketDistributor.sendToServer(new RequestAlbumPayload(hand == InteractionHand.MAIN_HAND));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractBlurredBackground(graphics);
        Layout layout = layout();
        hoverTooltip = null;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, layout.x, layout.y, 0.0F, 0.0F,
                layout.width, layout.height, 512, 256, 512, 256);

        if (!hasAlbumInHand()) {
            drawCenteredText(graphics, Component.translatable("gui.thelostcamera.album_missing"));
            return;
        }

        switch (mode) {
            case HOME -> drawHome(graphics, layout, mouseX, mouseY);
            case GALLERY -> drawGallery(graphics, layout, mouseX, mouseY, false);
            case ARCHIVES -> drawGallery(graphics, layout, mouseX, mouseY, true);
            case IMPORT -> drawImport(graphics, layout, mouseX, mouseY);
            case CATALOG -> drawCatalog(graphics, layout, mouseX, mouseY);
            case ENTRY -> drawEntry(graphics, layout, mouseX, mouseY);
        }
        drawHoverTooltip(graphics, mouseX, mouseY);
    }

    private void drawHome(GuiGraphicsExtractor graphics, Layout layout, int mouseX, int mouseY) {
        int documented = discoveredRuinIds().size();
        int totalPhotos = ClientAlbumState.photos().size();

        Bounds leftPage = layout.leftPage();
        Bounds rightPage = layout.rightPage();
        drawCenteredInPage(graphics, Component.translatable("gui.thelostcamera.album.home.gallery_title"),
                leftPage.x, leftPage.width, layout.y + 28, TEXT);
        drawCenteredInPage(graphics, Component.translatable("gui.thelostcamera.album.home.archive_title"),
                rightPage.x, rightPage.width, layout.y + 28, TEXT);

        Bounds gallery = new Bounds(leftPage.x + 12, layout.y + 58, leftPage.width - 24, 92);
        Bounds catalogue = new Bounds(rightPage.x + 12, layout.y + 58, rightPage.width - 24, 92);
        drawButton(graphics, gallery, Component.translatable("gui.thelostcamera.album.home.gallery_button"),
                gallery.contains(mouseX, mouseY), false,
                Component.translatable("gui.thelostcamera.album.tooltip.open_gallery"));
        drawButton(graphics, catalogue, Component.translatable("gui.thelostcamera.album.home.archive_button"),
                catalogue.contains(mouseX, mouseY), false,
                Component.translatable("gui.thelostcamera.album.tooltip.open_archive"));

        drawWrapped(graphics, Component.translatable("gui.thelostcamera.album.home.personal_summary", totalPhotos),
                leftPage.x + 10, layout.y + 160, leftPage.width - 20, MUTED, 3);
        drawWrapped(graphics, Component.translatable("gui.thelostcamera.album.home.archive_summary", documented, RuinCatalog.entries().size()),
                rightPage.x + 10, layout.y + 160, rightPage.width - 20, MUTED, 3);

        Bounds archives = centeredButton(leftPage, layout.y + layout.height - 44, 128, 22);
        drawButton(graphics, archives, Component.translatable("gui.thelostcamera.album.archives"),
                archives.contains(mouseX, mouseY), false,
                Component.translatable("gui.thelostcamera.album.tooltip.open_archives"));

        Component hint = gallery.contains(mouseX, mouseY)
                ? Component.translatable("gui.thelostcamera.album.tooltip.open_gallery")
                : catalogue.contains(mouseX, mouseY)
                ? Component.translatable("gui.thelostcamera.album.tooltip.open_archive")
                : archives.contains(mouseX, mouseY)
                ? Component.translatable("gui.thelostcamera.album.tooltip.open_archives")
                : Component.translatable("gui.thelostcamera.album.home_hint");
        drawFooterHint(graphics, layout, hint);
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
                        ? "gui.thelostcamera.album.gallery" : "gui.thelostcamera.album.archives"),
                switchSection.contains(mouseX, mouseY), false,
                Component.translatable(archived
                        ? "gui.thelostcamera.album.tooltip.switch_gallery"
                        : "gui.thelostcamera.album.tooltip.switch_archives"));

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
        drawButton(graphics, back, Component.translatable("gui.thelostcamera.album.back"), back.contains(mouseX, mouseY), false,
                Component.translatable("gui.thelostcamera.album.tooltip.back_home"));
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
        drawButton(graphics, back, Component.translatable("gui.thelostcamera.album.back"), back.contains(mouseX, mouseY), false,
                Component.translatable("gui.thelostcamera.album.tooltip.back_home"));
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
        StoredPhoto documented = newestRuinPhoto(entry.id().toString());
        boolean unlocked = documented != null;

        Bounds leftPage = layout.leftPage();
        Bounds rightPage = layout.rightPage();
        int pageWidth = leftPage.width;
        drawCenteredInPage(graphics, unlocked ? Component.translatable(entry.nameKey())
                        : Component.translatable("gui.thelostcamera.album.unknown_structure", selectedRuinIndex + 1),
                leftPage.x, pageWidth, layout.y + 20, TEXT);

        Bounds photoFrame = entryFrameBounds(layout, leftPage, 14, 154);
        drawPanelFrame(graphics, PHOTO_FRAME, photoFrame, 180, 154);
        Bounds image = photoFrame.inset(7);
        if (unlocked) {
            drawPhotoOutline(graphics, drawPhoto(graphics, documented.data(), image.x, image.y,
                    image.width, image.height, false), GOLD);
        } else {
            drawCenteredTextAt(graphics, Component.literal("?"), image.x + image.width / 2,
                    image.y + image.height / 2 - font.lineHeight / 2, TEXT);
            drawWrapped(graphics, Component.translatable(entry.hintKey()), image.x + 6,
                    Math.min(image.y + image.height - 36, image.y + Math.max(48, image.height / 2 + 16)),
                    image.width - 12, MUTED, 4);
        }

        Bounds recordFrame = entryFrameBounds(layout, rightPage, 12, 162);
        drawPanelFrame(graphics, INFO_PANEL, recordFrame, 180, 162);
        drawCenteredInPage(graphics, Component.translatable("gui.thelostcamera.album.entry_information"),
                recordFrame.x + 6, recordFrame.width - 12, recordFrame.y + 8, TEXT);
        int textX = recordFrame.x + 14;
        int textWidth = recordFrame.width - 28;
        graphics.text(font, Component.translatable("gui.thelostcamera.album.entry_size",
                        Component.translatable(entry.size().translationKey())).getVisualOrderText(),
                textX, recordFrame.y + 29, GOLD, false);
        graphics.text(font, Component.translatable("gui.thelostcamera.album.entry_rarity",
                        Component.translatable(entry.rarity().translationKey())).getVisualOrderText(),
                textX, recordFrame.y + 43, GOLD, false);
        drawWrapped(graphics, Component.translatable(entry.biomeHintKey()), textX, recordFrame.y + 63,
                textWidth, MUTED, 3);
        if (unlocked) {
            drawWrapped(graphics, Component.translatable(entry.descriptionKey()), textX, recordFrame.y + 99,
                    textWidth, TEXT, 4);
            drawWrapped(graphics, Component.translatable(entry.echoKey()), textX, recordFrame.y + 138,
                    textWidth, GOLD, 2);
        } else {
            drawWrapped(graphics, Component.translatable("gui.thelostcamera.album.entry_locked"),
                    textX, recordFrame.y + 104, textWidth, MUTED, 4);
        }

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

    private void drawGallerySlot(GuiGraphicsExtractor graphics, Bounds bounds, StoredPhoto photo, boolean hovered) {
        SlotVariant variant = slotVariant(bounds);
        drawSlotFrame(graphics, variant.texture(hovered, photo == null), bounds, variant);
        if (photo == null) {
            return;
        }
        if (hovered) {
            hoverTooltip = Component.translatable(photo.archived()
                    ? "gui.thelostcamera.album.tooltip.archived_slot"
                    : "gui.thelostcamera.album.tooltip.photo_slot");
        }
        Bounds photoBounds = drawPhoto(graphics, photo.data(),
                bounds.x + variant.contentInset, bounds.y + variant.contentInset,
                bounds.width - variant.contentInset * 2, bounds.height - variant.contentInset * 2, true);
        drawPhotoOutline(graphics, photoBounds, photo.favorite() ? GOLD : TEXT);
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
        drawCenteredTextAt(graphics, title, card.x + card.width / 2, card.y + 8, TEXT);

        if (unlocked && preview != null && card.height >= 72) {
            Bounds previewFrame = new Bounds(card.x + 10, card.y + 24, card.width - 20,
                    Math.max(28, Math.min(57, card.height - 54)));
            drawPanelFrame(graphics, PHOTO_FRAME, previewFrame, 180, 154);
            Bounds image = previewFrame.inset(4);
            drawPhotoOutline(graphics, drawPhoto(graphics, preview.data(), image.x, image.y,
                    image.width, image.height, true), GOLD);
            drawCenteredTextAt(graphics, Component.translatable("gui.thelostcamera.album.documented"),
                    card.x + card.width / 2, Math.min(card.y + card.height - 28, card.y + 84), GOLD);
            int metaY = Math.min(card.y + card.height - 14, card.y + 99);
            drawCenteredTextAt(graphics, Component.translatable(entry.size().translationKey()),
                    card.x + card.width / 4, metaY, MUTED);
            drawCenteredTextAt(graphics, Component.translatable(entry.rarity().translationKey()),
                    card.x + card.width * 3 / 4, metaY, MUTED);
            return;
        }

        drawCenteredTextAt(graphics, Component.translatable(entry.size().translationKey()),
                card.x + card.width / 2, Math.min(card.y + card.height - 28, card.y + 40), GOLD);
        drawCenteredTextAt(graphics, Component.translatable(entry.rarity().translationKey()),
                card.x + card.width / 2, Math.min(card.y + card.height - 16, card.y + 55), MUTED);
        drawCenteredTextAt(graphics, unlocked
                        ? Component.translatable("gui.thelostcamera.album.documented")
                        : Component.literal("?"),
                card.x + card.width / 2, Math.min(card.y + card.height - 12, card.y + 82), unlocked ? GOLD : TEXT);
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
            drawCenteredTextAt(graphics, Component.translatable("gui.thelostcamera.loading_photograph"),
                    x + maximumWidth / 2, y + maximumHeight / 2, MUTED);
            return new Bounds(x, y, maximumWidth, maximumHeight);
        }
        float ratio = (float) image.getWidth() / image.getHeight();
        int drawWidth = maximumWidth;
        int drawHeight = Math.round(drawWidth / ratio);
        if (drawHeight > maximumHeight) {
            drawHeight = maximumHeight;
            drawWidth = Math.round(drawHeight * ratio);
        }
        int drawX = x + (maximumWidth - drawWidth) / 2;
        int drawY = y + (maximumHeight - drawHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, drawX, drawY, 0.0F, 0.0F,
                drawWidth, drawHeight, image.getWidth(), image.getHeight(), image.getWidth(), image.getHeight());
        return new Bounds(drawX, drawY, drawWidth, drawHeight);
    }

    private void drawPhotoOutline(GuiGraphicsExtractor graphics, Bounds bounds, int colorHint) {
        Identifier texture = colorHint == GOLD ? FRAME_GOLD : FRAME_DARK;
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, bounds.x - 1, bounds.y - 1, 0.0F, 0.0F,
                bounds.width + 2, 1, 1, 1, 1, 1);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, bounds.x - 1, bounds.y + bounds.height, 0.0F, 0.0F,
                bounds.width + 2, 1, 1, 1, 1, 1);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, bounds.x - 1, bounds.y, 0.0F, 0.0F,
                1, bounds.height, 1, 1, 1, 1);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, bounds.x + bounds.width, bounds.y, 0.0F, 0.0F,
                1, bounds.height, 1, 1, 1, 1);
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

    private SlotVariant slotVariant(Bounds bounds) {
        if (bounds.width <= 30 || bounds.height <= 24) {
            return SlotVariant.COMPACT;
        }
        if (bounds.width >= 42 && bounds.height >= 32) {
            return SlotVariant.LARGE;
        }
        return SlotVariant.STANDARD;
    }

    private void drawSlotFrame(GuiGraphicsExtractor graphics, Identifier texture, Bounds bounds, SlotVariant variant) {
        int left = Math.min(variant.border, bounds.width / 2);
        int right = Math.min(variant.border, Math.max(0, bounds.width - left));
        int top = Math.min(variant.border, bounds.height / 2);
        int bottom = Math.min(variant.border, Math.max(0, bounds.height - top));
        int centreWidth = Math.max(0, bounds.width - left - right);
        int centreHeight = Math.max(0, bounds.height - top - bottom);

        blitNative(graphics, texture, bounds.x, bounds.y, 0, 0, left, top, variant.textureWidth, variant.textureHeight);
        blitNative(graphics, texture, bounds.x + bounds.width - right, bounds.y,
                variant.textureWidth - right, 0, right, top, variant.textureWidth, variant.textureHeight);
        blitNative(graphics, texture, bounds.x, bounds.y + bounds.height - bottom,
                0, variant.textureHeight - bottom, left, bottom, variant.textureWidth, variant.textureHeight);
        blitNative(graphics, texture, bounds.x + bounds.width - right, bounds.y + bounds.height - bottom,
                variant.textureWidth - right, variant.textureHeight - bottom,
                right, bottom, variant.textureWidth, variant.textureHeight);

        stretchNative(graphics, texture, bounds.x + left, bounds.y,
                centreWidth, top, left, 0,
                Math.max(1, variant.textureWidth - left - right), top, variant.textureWidth, variant.textureHeight);
        stretchNative(graphics, texture, bounds.x + left, bounds.y + bounds.height - bottom,
                centreWidth, bottom, left, variant.textureHeight - bottom,
                Math.max(1, variant.textureWidth - left - right), bottom, variant.textureWidth, variant.textureHeight);
        stretchNative(graphics, texture, bounds.x, bounds.y + top,
                left, centreHeight, 0, top,
                left, Math.max(1, variant.textureHeight - top - bottom), variant.textureWidth, variant.textureHeight);
        stretchNative(graphics, texture, bounds.x + bounds.width - right, bounds.y + top,
                right, centreHeight, variant.textureWidth - right, top,
                right, Math.max(1, variant.textureHeight - top - bottom), variant.textureWidth, variant.textureHeight);
        stretchNative(graphics, texture, bounds.x + left, bounds.y + top,
                centreWidth, centreHeight, variant.fillX, variant.fillY,
                1, 1, variant.textureWidth, variant.textureHeight);
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

    private void stretchNative(GuiGraphicsExtractor graphics, Identifier texture, int x, int y,
                               int width, int height, int sourceX, int sourceY,
                               int sourceWidth, int sourceHeight, int textureWidth, int textureHeight) {
        if (width <= 0 || height <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            return;
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, sourceX, sourceY,
                width, height, sourceWidth, sourceHeight, textureWidth, textureHeight);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0 && event.button() != 1 && event.button() != 2) {
            return super.mouseClicked(event, doubleClick);
        }
        if (!hasAlbumInHand()) {
            return false;
        }

        Layout layout = layout();
        return switch (mode) {
            case HOME -> event.button() == 0 && clickHome(layout, event.x(), event.y());
            case GALLERY -> clickGallery(layout, event.x(), event.y(), false, event.button());
            case ARCHIVES -> clickGallery(layout, event.x(), event.y(), true, event.button());
            case IMPORT -> event.button() == 0 && clickImport(layout, event.x(), event.y());
            case CATALOG -> event.button() == 0 && clickCatalog(layout, event.x(), event.y());
            case ENTRY -> event.button() == 0 && clickEntry(layout, event.x(), event.y());
        };
    }

    private boolean clickHome(Layout layout, double x, double y) {
        Bounds leftPage = layout.leftPage();
        Bounds rightPage = layout.rightPage();
        Bounds gallery = new Bounds(leftPage.x + 12, layout.y + 58, leftPage.width - 24, 92);
        Bounds catalogue = new Bounds(rightPage.x + 12, layout.y + 58, rightPage.width - 24, 92);
        Bounds archives = centeredButton(leftPage, layout.y + layout.height - 44, 128, 22);
        if (gallery.contains(x, y)) { setMode(Mode.GALLERY); return true; }
        if (catalogue.contains(x, y)) { setMode(Mode.CATALOG); return true; }
        if (archives.contains(x, y)) { setMode(Mode.ARCHIVES); return true; }
        return false;
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
                    minecraft.setScreenAndShow(new PhotoScreen(photo.data(), this));
                } else if (mouseButton == 1) {
                    ClientPacketDistributor.sendToServer(
                            new SetPhotoFavoritePayload(photo.data().imageId(), !photo.favorite()));
                } else {
                    ClientPacketDistributor.sendToServer(
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
            setMode(archived ? Mode.GALLERY : Mode.ARCHIVES);
            return true;
        }

        int bottomY = layout.y + layout.height - 36;
        Bounds back = new Bounds(leftPage.x + BOTTOM_INSET, bottomY, 62, 23);
        Bounds importButton = new Bounds(back.x + back.width + 6, bottomY,
                leftPage.width - BOTTOM_INSET * 2 - back.width - 6, 23);
        PageControls controls = pageControls(rightPage, bottomY);
        if (back.contains(x, y)) {
            setMode(Mode.HOME);
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
                ClientPacketDistributor.sendToServer(new ArchivePhotoPayload(hand == InteractionHand.MAIN_HAND,
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
        if (back.contains(x, y)) { setMode(Mode.HOME); return true; }
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
        if (controls.previous.contains(x, y) && entries.size() > 1) { selectedRuinIndex = Math.floorMod(selectedRuinIndex - 1, entries.size()); playPageSound(); return true; }
        if (controls.next.contains(x, y) && entries.size() > 1) { selectedRuinIndex = (selectedRuinIndex + 1) % entries.size(); playPageSound(); return true; }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mode == Mode.GALLERY || mode == Mode.ARCHIVES) {
            List<StoredPhoto> photos = filteredPhotos(mode == Mode.ARCHIVES);
            page = Math.max(0, Math.min(maximumPage(photos.size(), PAGE_SIZE), page + (scrollY < 0 ? 1 : -1)));
            playPageSound();
            return true;
        }
        if (mode == Mode.ENTRY) {
            List<RuinCatalog.Entry> entries = filteredEntries();
            if (!entries.isEmpty()) {
                selectedRuinIndex = Math.floorMod(selectedRuinIndex + (scrollY < 0 ? 1 : -1), entries.size());
                playPageSound();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        ClientPhotoCache.clearThumbnails();
        super.onClose();
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
                PhotoData data = stack.get(com.levelscraft7.thelostcamera.registry.ModDataComponents.PHOTO_DATA);
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

    private boolean hasAlbumInHand() {
        if (minecraft.player == null) return false;
        return minecraft.player.getItemInHand(hand).getItem() == ModItems.PHOTO_ALBUM.get();
    }

    private void setMode(Mode value) {
        mode = value;
        page = 0;
        playPageSound();
    }

    private static int maximumPage(int itemCount, int pageSize) {
        return itemCount <= 0 ? 0 : (itemCount - 1) / pageSize;
    }

    private static RuinSizeClass nextSize(RuinSizeClass current) {
        if (current == null) return RuinSizeClass.SMALL;
        return switch (current) {
            case SMALL -> RuinSizeClass.MEDIUM;
            case MEDIUM -> RuinSizeClass.LARGE;
            case LARGE -> null;
        };
    }

    private static RuinRarity nextRarity(RuinRarity current) {
        if (current == null) return RuinRarity.COMMON;
        return switch (current) {
            case COMMON -> RuinRarity.UNCOMMON;
            case UNCOMMON -> RuinRarity.RARE;
            case RARE -> null;
        };
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
        return gridSlotBounds(page, indexOnPage, 4, 3, page.y + 43, layoutContentBottom(layout), 52, 42);
    }

    private Bounds importSlotBounds(Layout layout, int visibleIndex) {
        Bounds page = visibleIndex < IMPORT_PAGE_SIZE / 2 ? layout.leftPage() : layout.rightPage();
        int indexOnPage = visibleIndex % (IMPORT_PAGE_SIZE / 2);
        return gridSlotBounds(page, indexOnPage, 4, 2, page.y + 48, layoutContentBottom(layout), 44, 34);
    }

    private Bounds gridSlotBounds(Bounds page, int indexOnPage, int columns, int rows,
                                  int top, int bottom, int preferredWidth, int preferredHeight) {
        int column = indexOnPage % columns;
        int row = indexOnPage / columns;
        int horizontalGap = Math.max(2, Math.min(4, page.width / 48));
        int verticalGap = Math.max(2, Math.min(rows >= 4 ? 4 : 12, Math.max(2, (bottom - top) / 18)));
        int availableWidth = Math.max(columns * 12, page.width - 8);
        int availableHeight = Math.max(rows * 10, bottom - top);
        int slotWidth = Math.max(12, Math.min(preferredWidth, (availableWidth - horizontalGap * (columns - 1)) / columns));
        int slotHeight = Math.max(10, Math.min(preferredHeight, (availableHeight - verticalGap * (rows - 1)) / rows));
        int gridWidth = slotWidth * columns + horizontalGap * (columns - 1);
        int gridHeight = slotHeight * rows + verticalGap * (rows - 1);
        int startX = page.x + (page.width - gridWidth) / 2;
        int startY = top + Math.max(0, (availableHeight - gridHeight) / 2);
        return new Bounds(startX + column * (slotWidth + horizontalGap),
                startY + row * (slotHeight + verticalGap), slotWidth, slotHeight);
    }

    private Bounds catalogCardBounds(Layout layout, Bounds page) {
        int top = page.y + 60;
        int bottom = layoutContentBottom(layout);
        return new Bounds(page.x + 10, top, Math.max(24, page.width - 20),
                Math.max(46, Math.min(112, bottom - top)));
    }

    private Bounds entryFrameBounds(Layout layout, Bounds page, int horizontalInset, int preferredHeight) {
        int top = page.y + 20;
        int bottom = layoutContentBottom(layout);
        return new Bounds(page.x + horizontalInset, top, Math.max(24, page.width - horizontalInset * 2),
                Math.max(54, Math.min(preferredHeight, bottom - top)));
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
        int y = bounds.y + (bounds.height - font.lineHeight) / 2;
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

    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) { }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) { }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TheLostCamera.MOD_ID, path);
    }

    private enum Mode { HOME, GALLERY, ARCHIVES, IMPORT, CATALOG, ENTRY }
    private enum GalleryFilter {
        ALL("gui.thelostcamera.album.filter_all"),
        NORMAL("gui.thelostcamera.album.filter_normal"),
        RUINS("gui.thelostcamera.album.filter_ruins");
        private final String translationKey;
        GalleryFilter(String translationKey) { this.translationKey = translationKey; }
        GalleryFilter next() { return values()[(ordinal() + 1) % values().length]; }
    }
    private record InventoryPhoto(int slot, PhotoData data) { }
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
    private enum SlotVariant {
        COMPACT("album_slot_compact", 32, 28, 3, 2, 5, 5),
        STANDARD("album_slot", 44, 34, 3, 3, 6, 6),
        LARGE("album_slot_large", 52, 40, 4, 3, 7, 7);

        private final Identifier normal;
        private final Identifier hover;
        private final Identifier empty;
        private final int textureWidth;
        private final int textureHeight;
        private final int border;
        private final int contentInset;
        private final int fillX;
        private final int fillY;

        SlotVariant(String textureName, int textureWidth, int textureHeight,
                    int border, int contentInset, int fillX, int fillY) {
            this.normal = id("textures/gui/" + textureName + ".png");
            this.hover = id("textures/gui/" + textureName + "_hover.png");
            this.empty = id("textures/gui/" + textureName + "_empty.png");
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
            this.border = border;
            this.contentInset = contentInset;
            this.fillX = fillX;
            this.fillY = fillY;
        }

        private Identifier texture(boolean hovered, boolean empty) {
            if (hovered) {
                return hover;
            }
            return empty ? this.empty : normal;
        }
    }
    private record Layout(int x, int y, int width, int height) {
        int pageWidth() { return width / 2 - PAGE_MARGIN_X - 18; }
        Bounds leftPage() { return new Bounds(x + PAGE_MARGIN_X, y + PAGE_TOP, pageWidth(), height - PAGE_TOP - PAGE_BOTTOM); }
        Bounds rightPage() { return new Bounds(x + width / 2 + 18, y + PAGE_TOP, pageWidth(), height - PAGE_TOP - PAGE_BOTTOM); }
    }
}
