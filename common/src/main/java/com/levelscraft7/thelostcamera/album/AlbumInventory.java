package com.levelscraft7.thelostcamera.album;

import com.levelscraft7.thelostcamera.item.PhotographItem;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;

/**
 * Legacy 54-slot container support for albums created by older alpha builds.
 * Current albums use the player-owned server-side library; this class remains for migration compatibility.
 */
public final class AlbumInventory {
    public static final int SIZE = 54;

    private final ItemStack album;
    private final NonNullList<ItemStack> items;

    public AlbumInventory(ItemStack album) {
        this.album = album;
        this.items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        album.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
    }

    public boolean addPhoto(ItemStack photograph) {
        if (!(photograph.getItem() instanceof PhotographItem)) {
            return false;
        }
        for (int slot = 0; slot < items.size(); slot++) {
            if (items.get(slot).isEmpty()) {
                ItemStack archived = photograph.copy();
                archived.setCount(1);
                items.set(slot, archived);
                save();
                return true;
            }
        }
        return false;
    }

    public ItemStack photoAt(int slot) {
        if (slot < 0 || slot >= items.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = items.get(slot);
        return stack.getItem() instanceof PhotographItem ? stack : ItemStack.EMPTY;
    }

    public ItemStack removePhoto(int slot) {
        ItemStack stack = photoAt(slot);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        items.set(slot, ItemStack.EMPTY);
        save();
        return stack.copy();
    }

    public int photoCount() {
        int count = 0;
        for (ItemStack stack : items) {
            if (stack.getItem() instanceof PhotographItem) {
                count++;
            }
        }
        return count;
    }

    public boolean isFull() {
        return photoCount() >= SIZE;
    }

    /** Complete slot-preserving snapshot used by the chest-like gallery. */
    public List<ItemStack> slots() {
        return List.copyOf(items);
    }

    /** Compact list used by progression and tooltips. */
    public List<ItemStack> photographs() {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : items) {
            if (stack.getItem() instanceof PhotographItem) {
                result.add(stack);
            }
        }
        return List.copyOf(result);
    }

    private void save() {
        album.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
    }
}


