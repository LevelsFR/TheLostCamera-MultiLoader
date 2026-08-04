package com.levelscraft7.thelostcamera.ruin;

import com.levelscraft7.thelostcamera.TheLostCamera;
import com.levelscraft7.thelostcamera.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Loads the authored structure NBTs directly, without a hand-written Java mirror. */
public final class RestorationTemplateLoader {
    private static final Map<String, RestorationTemplate> CACHE = new ConcurrentHashMap<>();

    private RestorationTemplateLoader() {
    }

    public static RestorationTemplate load(String ruinId) {
        return CACHE.computeIfAbsent(ruinId, RestorationTemplateLoader::loadUncached);
    }

    private static RestorationTemplate loadUncached(String ruinId) {
        Identifier id = Identifier.parse(ruinId);
        ParsedTemplate ruined = read(id, "ruined", false);
        ParsedTemplate restored = read(id, "restored", true);
        if (ruined.sizeX != restored.sizeX || ruined.sizeY != restored.sizeY || ruined.sizeZ != restored.sizeZ) {
            throw new IllegalStateException("Ruin/restored structure sizes differ for " + ruinId);
        }
        BlockPos ruinedAnchor = ruined.anchor == null ? restored.anchor : ruined.anchor;

        RestorationRotation restoredAlignment = inferRestoredAlignment(ruinId, ruined, restored);
        List<RestorationBlock> targets = new ArrayList<>();
        for (var entry : restored.blocks.entrySet()) {
            BlockPos templatePos = entry.getKey();
            ParsedBlock authored = entry.getValue();
            if (templatePos.equals(restored.anchor) || authored.state.is(Blocks.STRUCTURE_VOID)) {
                continue;
            }

            int relativeX = templatePos.getX() - restored.anchor.getX();
            int relativeY = templatePos.getY() - restored.anchor.getY();
            int relativeZ = templatePos.getZ() - restored.anchor.getZ();
            BlockPos alignedRelative = new BlockPos(
                    restoredAlignment.rotateX(relativeX, relativeZ),
                    relativeY,
                    restoredAlignment.rotateZ(relativeX, relativeZ)
            );
            BlockPos matchingRuinedPos = ruinedAnchor.offset(
                    alignedRelative.getX(),
                    alignedRelative.getY(),
                    alignedRelative.getZ()
            );
            BlockState sourceState = sourceStateForClear(ruined.blocks.get(matchingRuinedPos));
            if (authored.state.isAir() && sourceState == null) {
                continue;
            }

            BlockState targetState = authored.state.rotate(restoredAlignment.vanillaRotation());
            targets.add(new RestorationBlock(
                    alignedRelative.getX(),
                    alignedRelative.getY(),
                    alignedRelative.getZ(),
                    targetState,
                    targetState.isAir() ? sourceState : null,
                    authored.blockEntityTag == null ? null : authored.blockEntityTag.copy()
            ));
        }

        List<RestorationEntity> entities = new ArrayList<>();
        for (int index = 0; index < restored.entities.size(); index++) {
            ParsedEntity entity = restored.entities.get(index);
            double alignedX = restoredAlignment.rotateX(entity.x - restored.anchor.getX(), entity.z - restored.anchor.getZ());
            double alignedZ = restoredAlignment.rotateZ(entity.x - restored.anchor.getX(), entity.z - restored.anchor.getZ());
            entities.add(new RestorationEntity(
                    index,
                    alignedX,
                    entity.y - restored.anchor.getY(),
                    alignedZ,
                    entity.yaw + restoredAlignment.yawOffset(),
                    entity.pitch,
                    rotateEntityTag(entity.tag, restoredAlignment),
                    entity.living
            ));
        }

        Map<BlockPos, BlockState> ruinedStates = new HashMap<>();
        ruined.blocks.forEach((pos, block) -> {
            if (!pos.equals(ruinedAnchor)
                    && !block.state.isAir()
                    && !block.state.is(Blocks.STRUCTURE_VOID)) {
                ruinedStates.put(pos, block.state);
            }
        });

        return new RestorationTemplate(
                restored.sizeX,
                restored.sizeY,
                restored.sizeZ,
                ruinedAnchor,
                restored.anchor,
                Map.copyOf(ruinedStates),
                List.copyOf(targets),
                List.copyOf(entities)
        );
    }

    private static BlockState sourceStateForClear(ParsedBlock block) {
        if (block == null || block.state.isAir() || block.state.is(Blocks.STRUCTURE_VOID)) {
            return null;
        }
        return block.state;
    }

    private static RestorationRotation inferRestoredAlignment(String ruinId, ParsedTemplate ruined, ParsedTemplate restored) {
        BlockPos ruinedAnchor = ruined.anchor == null ? restored.anchor : ruined.anchor;
        Set<BlockPos> ruinedSolidPositions = new HashSet<>();
        Map<BlockPos, BlockState> ruinedSolidStates = new HashMap<>();
        ruined.blocks.forEach((pos, block) -> {
            if (!pos.equals(ruinedAnchor) && !block.state.isAir() && !block.state.is(Blocks.STRUCTURE_VOID)) {
                BlockPos relative = pos.subtract(ruinedAnchor);
                ruinedSolidPositions.add(relative);
                ruinedSolidStates.put(relative, block.state);
            }
        });

        RestorationRotation best = RestorationRotation.NONE;
        int bestScore = Integer.MIN_VALUE;
        for (RestorationRotation candidate : RestorationRotation.values()) {
            int score = 0;
            for (var entry : restored.blocks.entrySet()) {
                BlockPos pos = entry.getKey();
                ParsedBlock block = entry.getValue();
                if (pos.equals(restored.anchor) || block.state.isAir() || block.state.is(Blocks.STRUCTURE_VOID)) {
                    continue;
                }
                int relativeX = pos.getX() - restored.anchor.getX();
                int relativeY = pos.getY() - restored.anchor.getY();
                int relativeZ = pos.getZ() - restored.anchor.getZ();
                BlockPos aligned = new BlockPos(
                        candidate.rotateX(relativeX, relativeZ),
                        relativeY,
                        candidate.rotateZ(relativeX, relativeZ)
                );
                BlockState ruinedState = ruinedSolidStates.get(aligned);
                if (ruinedState != null && ruinedState.is(block.state.getBlock())) {
                    score += 3;
                } else if (ruinedSolidPositions.contains(aligned)) {
                    score += 1;
                } else {
                    score -= 1;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        if (best != RestorationRotation.NONE) {
            TheLostCamera.LOGGER.info("Aligned restored template for {} with {} so ruined/restored directions match", ruinId, best);
        }
        return best;
    }

    private static CompoundTag rotateEntityTag(CompoundTag tag, RestorationRotation rotation) {
        CompoundTag rotated = tag.copy();
        if (rotation == RestorationRotation.NONE) {
            return rotated;
        }
        ListTag entityRotation = rotated.getListOrEmpty("Rotation");
        if (entityRotation.size() >= 1) {
            entityRotation.set(0, FloatTag.valueOf(entityRotation.getFloatOr(0, 0.0F) + rotation.yawOffset()));
            rotated.put("Rotation", entityRotation);
        }
        return rotated;
    }

    private static ParsedTemplate read(Identifier id, String stateFolder, boolean requireAnchor) {
        String resourcePath = "/data/" + id.getNamespace() + "/structure/" + stateFolder + "/" + id.getPath() + ".nbt";
        try (InputStream input = RestorationTemplateLoader.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("Missing structure resource " + resourcePath);
            }
            CompoundTag root = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
            return parse(root, id + " (" + stateFolder + ")", requireAnchor);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read structure resource " + resourcePath, exception);
        }
    }

    private static ParsedTemplate parse(CompoundTag root, String debugName, boolean requireAnchor) {
        ListTag size = root.getListOrEmpty("size");
        if (size.size() < 3) {
            throw new IllegalStateException("Invalid size in " + debugName);
        }
        int sizeX = size.getIntOr(0, 0);
        int sizeY = size.getIntOr(1, 0);
        int sizeZ = size.getIntOr(2, 0);

        List<BlockState> palette = new ArrayList<>();
        for (CompoundTag stateTag : root.getListOrEmpty("palette").compoundStream().toList()) {
            palette.add(parseBlockState(stateTag));
        }

        Map<BlockPos, ParsedBlock> blocks = new HashMap<>();
        BlockPos anchor = null;
        for (CompoundTag blockTag : root.getListOrEmpty("blocks").compoundStream().toList()) {
            BlockPos pos = readBlockPos(blockTag.getListOrEmpty("pos"));
            int paletteIndex = blockTag.getIntOr("state", -1);
            if (paletteIndex < 0 || paletteIndex >= palette.size()) {
                throw new IllegalStateException("Invalid palette index in " + debugName + " at " + pos);
            }
            BlockState state = palette.get(paletteIndex);
            CompoundTag blockEntityTag = blockTag.getCompound("nbt").map(CompoundTag::copy).orElse(null);
            blocks.put(pos, new ParsedBlock(state, blockEntityTag));
            if (state.is(ModBlocks.RUIN_ANCHOR.get())) {
                anchor = pos;
            }
        }
        if (anchor == null && requireAnchor) {
            throw new IllegalStateException("No ruin anchor found in " + debugName);
        }

        List<ParsedEntity> entities = new ArrayList<>();
        for (CompoundTag entityTag : root.getListOrEmpty("entities").compoundStream().toList()) {
            ListTag pos = entityTag.getListOrEmpty("pos");
            CompoundTag nbt = entityTag.getCompoundOrEmpty("nbt").copy();
            double x = pos.getDoubleOr(0, 0.0D);
            double y = pos.getDoubleOr(1, 0.0D);
            double z = pos.getDoubleOr(2, 0.0D);
            ListTag rotation = nbt.getListOrEmpty("Rotation");
            float yaw = rotation.getFloatOr(0, 0.0F);
            float pitch = rotation.getFloatOr(1, 0.0F);
            String entityId = nbt.getStringOr("id", "minecraft:pig");
            var type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(entityId));
            boolean living = type != null
                    && !"minecraft:armor_stand".equals(entityId)
                    && LivingEntity.class.isAssignableFrom(type.getBaseClass());
            entities.add(new ParsedEntity(x, y, z, yaw, pitch, nbt, living));
        }

        return new ParsedTemplate(sizeX, sizeY, sizeZ, anchor, blocks, entities);
    }

    private static BlockState parseBlockState(CompoundTag stateTag) {
        String blockName = stateTag.getStringOr("Name", "minecraft:air");
        Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockName));
        BlockState state = block.defaultBlockState();
        CompoundTag properties = stateTag.getCompoundOrEmpty("Properties");
        for (String propertyName : properties.keySet()) {
            Property<?> property = block.getStateDefinition().getProperty(propertyName);
            if (property == null) {
                TheLostCamera.LOGGER.warn("Unknown property {} for block {} in restoration template", propertyName, blockName);
                continue;
            }
            state = applyProperty(state, property, properties.getStringOr(propertyName, ""));
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
    }

    private static BlockPos readBlockPos(ListTag pos) {
        return new BlockPos(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0));
    }

    private record ParsedTemplate(
            int sizeX,
            int sizeY,
            int sizeZ,
            BlockPos anchor,
            Map<BlockPos, ParsedBlock> blocks,
            List<ParsedEntity> entities
    ) {
    }

    private record ParsedBlock(BlockState state, CompoundTag blockEntityTag) {
    }

    private record ParsedEntity(
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            CompoundTag tag,
            boolean living
    ) {
    }
}
