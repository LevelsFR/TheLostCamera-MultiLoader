package com.levelscraft7.thelostcamera.network;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.function.Consumer;

/** Common-side bridge for opening client screens without linking common item classes to client classes. */
public final class ClientUiBridge {
    private static Consumer<ItemStack> photoScreenOpener = stack -> {
    };
    private static Consumer<InteractionHand> albumScreenOpener = hand -> {
    };

    private ClientUiBridge() {
    }

    public static void installPhotoScreenOpener(Consumer<ItemStack> opener) {
        photoScreenOpener = Objects.requireNonNull(opener);
    }

    public static void installAlbumScreenOpener(Consumer<InteractionHand> opener) {
        albumScreenOpener = Objects.requireNonNull(opener);
    }

    public static void openPhoto(ItemStack stack) {
        photoScreenOpener.accept(stack.copy());
    }

    public static void openAlbum(InteractionHand hand) {
        albumScreenOpener.accept(hand);
    }
}


