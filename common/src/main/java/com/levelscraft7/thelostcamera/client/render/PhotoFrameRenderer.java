package com.levelscraft7.thelostcamera.client.render;

import com.levelscraft7.thelostcamera.block.PhotoFrameBlock;
import com.levelscraft7.thelostcamera.block.entity.PhotoFrameBlockEntity;
import com.levelscraft7.thelostcamera.client.photo.ClientPhotoCache;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.UUID;

/** Renders the actual saved photograph on the thin face of the frame. */
public final class PhotoFrameRenderer implements BlockEntityRenderer<PhotoFrameBlockEntity, PhotoFrameRenderer.State> {
    public PhotoFrameRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PhotoFrameBlockEntity blockEntity, State state, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, cameraPos, crumblingOverlay);
        state.facing = blockEntity.getBlockState().getValue(PhotoFrameBlock.FACING);
        state.imageId = blockEntity.hasPhoto() ? blockEntity.photo().imageId() : null;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
                       net.minecraft.client.renderer.state.level.CameraRenderState camera) {
        if (state.imageId == null) {
            return;
        }
        Identifier texture = ClientPhotoCache.getThumbnailTexture(state.imageId);
        if (texture == null) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        float rotation = switch (state.facing) {
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            case EAST -> -90.0F;
            default -> 0.0F;
        };
        poseStack.mulPose(new Matrix4f().rotate(Axis.YP.rotationDegrees(rotation)));
        // The model occupies z=14.5..16: put the print just in front of its inset, toward the viewer.
        poseStack.translate(0.0F, 0.0F, 0.402F);

        float halfWidth = 0.305F;
        float halfHeight = 0.305F;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture), (pose, consumer) -> {
            consumer.addVertex(pose, -halfWidth, halfHeight, 0.0F).setColor(0xFFFFFFFF).setUv(0.0F, 0.0F)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(state.lightCoords).setNormal(pose, 0.0F, 0.0F, -1.0F);
            consumer.addVertex(pose, halfWidth, halfHeight, 0.0F).setColor(0xFFFFFFFF).setUv(1.0F, 0.0F)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(state.lightCoords).setNormal(pose, 0.0F, 0.0F, -1.0F);
            consumer.addVertex(pose, halfWidth, -halfHeight, 0.0F).setColor(0xFFFFFFFF).setUv(1.0F, 1.0F)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(state.lightCoords).setNormal(pose, 0.0F, 0.0F, -1.0F);
            consumer.addVertex(pose, -halfWidth, -halfHeight, 0.0F).setColor(0xFFFFFFFF).setUv(0.0F, 1.0F)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(state.lightCoords).setNormal(pose, 0.0F, 0.0F, -1.0F);
        });
        poseStack.popPose();
    }

    public static final class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        UUID imageId;
    }
}


