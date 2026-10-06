package com.levelscraft7.thelostcamera.block.entity;

import com.levelscraft7.thelostcamera.data.PhotoData;
import com.levelscraft7.thelostcamera.data.RuinPhotoData;
import com.levelscraft7.thelostcamera.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class PhotoFrameBlockEntity extends BlockEntity {
    private PhotoData photo;
    private RuinPhotoData ruinPhoto;

    public PhotoFrameBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PHOTO_FRAME.get(), pos, state);
    }

    public boolean hasPhoto() {
        return photo != null;
    }

    public PhotoData photo() {
        return photo;
    }

    public RuinPhotoData ruinPhoto() {
        return ruinPhoto;
    }

    public void setPhoto(PhotoData photo, RuinPhotoData ruinPhoto) {
        this.photo = photo;
        this.ruinPhoto = ruinPhoto;
        sync();
    }

    public void clearPhoto() {
        this.photo = null;
        this.ruinPhoto = null;
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (photo != null) {
            output.store("photo", PhotoData.CODEC, photo);
        }
        if (ruinPhoto != null) {
            output.store("ruin_photo", RuinPhotoData.CODEC, ruinPhoto);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        photo = input.read("photo", PhotoData.CODEC).orElse(null);
        ruinPhoto = input.read("ruin_photo", RuinPhotoData.CODEC).orElse(null);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this, BlockEntity::getUpdateTag);
    }
}


