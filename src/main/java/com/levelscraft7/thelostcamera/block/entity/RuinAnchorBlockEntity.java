package com.levelscraft7.thelostcamera.block.entity;

import com.levelscraft7.thelostcamera.TheLostCamera;
import com.levelscraft7.thelostcamera.config.ModConfig;
import com.levelscraft7.thelostcamera.network.payload.RestorationShakePayload;
import com.levelscraft7.thelostcamera.registry.ModBlockEntities;
import com.levelscraft7.thelostcamera.registry.ModBlocks;
import com.levelscraft7.thelostcamera.registry.ModItems;
import com.levelscraft7.thelostcamera.ruin.RestorationBlock;
import com.levelscraft7.thelostcamera.ruin.RestorationEntity;
import com.levelscraft7.thelostcamera.ruin.RestorationPattern;
import com.levelscraft7.thelostcamera.ruin.RestorationRotation;
import com.levelscraft7.thelostcamera.ruin.RestorationTemplate;
import com.levelscraft7.thelostcamera.ruin.RestorationTemplateLoader;
import com.levelscraft7.thelostcamera.ruin.RuinCatalog;
import com.levelscraft7.thelostcamera.ruin.RuinSizeClass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Authoritative ruin restorer. The restored vanilla structure NBT is the only
 * source of truth. The ruined NBT is used only to infer the world rotation.
 */
public final class RuinAnchorBlockEntity extends BlockEntity {
    public static final String DEFAULT_RUIN_ID = "thelostcamera:solstice_shrine";
    private static final double EFFECT_RADIUS = 50.0D;
    private static final double EFFECT_RADIUS_SQUARED = EFFECT_RADIUS * EFFECT_RADIUS;
    private static final int INITIAL_QUAKE_TICKS = 18;
    private static final int HINT_CHECK_INTERVAL = 60;
    private static final double HINT_RANGE_SQUARED = 50.0D * 50.0D;
    private static final int BLOCK_FLAGS = Block.UPDATE_CLIENTS
            | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SUPPRESS_DROPS;

    private String ruinId = DEFAULT_RUIN_ID;
    private boolean restored;
    private boolean restoring;
    private int restorationIndex;
    private int stepDelay;
    private int ambientTicker;
    private int patternOrdinal;
    private int targetDurationTicks;
    private int restorationAge;
    private boolean ambientShakeStarted;
    private int resonanceTicks;
    private long restorationSeed;
    private int rotationOrdinal;

    private transient RestorationTemplate restorationTemplate;
    private transient List<RestorationBlock> restorationOrder = List.of();
    private transient List<RestorationEntity> decorativeEntities = List.of();
    private transient List<RestorationEntity> livingEntities = List.of();
    private transient int decorativeEntityIndex;
    private transient boolean runtimePrepared;
    private transient boolean virtualRuntime;
    private transient Set<UUID> hintedPlayers = new HashSet<>();

    public RuinAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RUIN_ANCHOR.get(), pos, state);
    }

    public static RuinAnchorBlockEntity virtual(ServerLevel level, BlockPos pos, String ruinId) {
        RuinAnchorBlockEntity anchor = new RuinAnchorBlockEntity(pos, ModBlocks.RUIN_ANCHOR.get().defaultBlockState());
        anchor.setLevel(level);
        anchor.ruinId = ruinId;
        anchor.restored = false;
        anchor.restoring = false;
        anchor.virtualRuntime = true;
        return anchor;
    }

    public RestorationRotation currentRotationForPersistence() {
        return currentRotation();
    }

    public static void tickVirtual(ServerLevel level, RuinAnchorBlockEntity anchor) {
        tick(level, anchor.worldPosition, Blocks.AIR.defaultBlockState(), anchor);
    }

    public static boolean restoreSilently(ServerLevel level, BlockPos pos, String ruinId, RestorationRotation forcedRotation) {
        RuinAnchorBlockEntity anchor = virtual(level, pos, ruinId);
        try {
            anchor.restorationTemplate = RestorationTemplateLoader.load(ruinId);
        } catch (RuntimeException exception) {
            TheLostCamera.LOGGER.error("Unable to load silent restoration template {}", ruinId, exception);
            return false;
        }

        RestorationRotation rotation = forcedRotation == null
                ? anchor.inferRotation(level, anchor.restorationTemplate)
                : forcedRotation;
        anchor.rotationOrdinal = rotation.ordinal();
        anchor.patternOrdinal = 0;
        anchor.restorationSeed = 0L;
        anchor.restorationIndex = 0;
        anchor.targetDurationTicks = 1;

        if (!anchor.ensureRuntimePrepared(level)) {
            return false;
        }

        anchor.enforceFinalTemplate(level);
        anchor.clearAuthoredEntities(level);
        for (RestorationEntity decoration : anchor.decorativeEntities) {
            anchor.spawnAuthoredEntity(level, decoration, rotation, false);
        }
        for (RestorationEntity living : anchor.livingEntities) {
            anchor.spawnAuthoredEntity(level, living, rotation, false);
        }
        return true;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, RuinAnchorBlockEntity anchor) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        anchor.tickDiscoveryHint(serverLevel);
        anchor.tickLingeringResonance(serverLevel);

        if (!anchor.restoring || anchor.restored) {
            return;
        }

        if (!anchor.ensureRuntimePrepared(serverLevel)) {
            anchor.abortRestoration();
            return;
        }
        if (anchor.restorationOrder.isEmpty()) {
            anchor.finishRestoration(serverLevel);
            return;
        }

        anchor.restorationAge++;
        anchor.ambientTicker++;
        anchor.playAmbientPulse(serverLevel);
        if (anchor.restorationAge % 24 == 0) {
            anchor.sendShake(serverLevel, 10, 0.24F);
        }

        if (!anchor.ambientShakeStarted && anchor.restorationAge >= INITIAL_QUAKE_TICKS) {
            anchor.ambientShakeStarted = true;
            int remaining = Math.max(40, anchor.targetDurationTicks - anchor.restorationAge);
            anchor.sendShake(serverLevel, remaining, ModConfig.RESTORATION_AMBIENT_SHAKE_INTENSITY.get().floatValue());
        }

        if (anchor.stepDelay > 0) {
            anchor.stepDelay--;
            anchor.setChanged();
            return;
        }

        RestorationPattern pattern = anchor.currentPattern();
        int currentWave = pattern.wave(anchor.restorationOrder.get(anchor.restorationIndex));
        int budget = anchor.dynamicBlockBudget();
        int processed = 0;

        while (processed < budget && anchor.restorationIndex < anchor.restorationOrder.size()) {
            RestorationBlock change = anchor.restorationOrder.get(anchor.restorationIndex);
            if (pattern.wave(change) != currentWave) {
                break;
            }
            boolean detailedEffects = processed < 8 || serverLevel.getRandom().nextFloat() < 0.08F;
            anchor.materializeBlock(serverLevel, pos, change, detailedEffects, false);
            anchor.restorationIndex++;
            processed++;
        }

        boolean waveComplete = anchor.restorationIndex >= anchor.restorationOrder.size()
                || pattern.wave(anchor.restorationOrder.get(anchor.restorationIndex)) != currentWave;
        if (waveComplete) {
            anchor.spawnDecorationsThroughWave(serverLevel, currentWave, true);
        }

        if (anchor.restorationIndex >= anchor.restorationOrder.size()) {
            anchor.finishRestoration(serverLevel);
        } else if (waveComplete) {
            anchor.stepDelay = ModConfig.RESTORATION_WAVE_PAUSE_TICKS.get();
            anchor.sendShake(serverLevel, 10, 0.34F);
        }

        anchor.setChanged();
    }

    public boolean beginRestoration() {
        return beginRestoration(null);
    }

    public boolean beginRestoration(RestorationRotation forcedRotation) {
        if (restored || restoring || level == null || level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        try {
            restorationTemplate = RestorationTemplateLoader.load(ruinId);
        } catch (RuntimeException exception) {
            TheLostCamera.LOGGER.error("Unable to load restoration template {}", ruinId, exception);
            return false;
        }

        restoring = true;
        restorationIndex = 0;
        stepDelay = INITIAL_QUAKE_TICKS;
        ambientTicker = 0;
        restorationAge = 0;
        ambientShakeStarted = false;
        patternOrdinal = level.getRandom().nextInt(RestorationPattern.values().length);
        restorationSeed = level.getRandom().nextLong();
        RestorationRotation rotation = forcedRotation == null ? inferRotation(serverLevel, restorationTemplate) : forcedRotation;
        rotationOrdinal = rotation.ordinal();
        if (forcedRotation != null) {
            TheLostCamera.LOGGER.debug("Using photographed {} rotation for {} at {}", forcedRotation, ruinId, worldPosition);
        }
        runtimePrepared = false;

        if (!ensureRuntimePrepared(serverLevel)) {
            abortRestoration();
            return false;
        }

        targetDurationTicks = calculateTargetDuration(countInitialDifferences(serverLevel));
        setChanged();

        sendShake(serverLevel, INITIAL_QUAKE_TICKS, ModConfig.RESTORATION_INITIAL_SHAKE_INTENSITY.get().floatValue());
        serverLevel.playSound(
                null,
                worldPosition,
                SoundEvents.END_PORTAL_FRAME_FILL,
                SoundSource.BLOCKS,
                3.4F,
                0.22F
        );
        serverLevel.playSound(
                null,
                worldPosition,
                SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.AMBIENT,
                3.2F,
                0.18F
        );
        serverLevel.sendParticles(
                ParticleTypes.CLOUD,
                worldPosition.getX() + 0.5D,
                worldPosition.getY() + 0.8D,
                worldPosition.getZ() + 0.5D,
                54,
                2.8D,
                0.6D,
                2.8D,
                0.09D
        );
        return true;
    }

    private boolean ensureRuntimePrepared(ServerLevel level) {
        if (runtimePrepared) {
            return true;
        }

        try {
            restorationTemplate = RestorationTemplateLoader.load(ruinId);
            RestorationRotation rotation = currentRotation();

            List<RestorationBlock> rotatedTargets = new ArrayList<>(restorationTemplate.restoredTargets().size());
            for (RestorationBlock target : restorationTemplate.restoredTargets()) {
                rotatedTargets.add(rotate(target, rotation));
            }
            restorationOrder = currentPattern().order(rotatedTargets, restorationSeed);
            restorationIndex = Math.max(0, Math.min(restorationIndex, restorationOrder.size()));

            List<RestorationEntity> decorations = new ArrayList<>();
            List<RestorationEntity> living = new ArrayList<>();
            for (RestorationEntity entity : restorationTemplate.restoredEntities()) {
                (entity.living() ? living : decorations).add(entity);
            }
            Comparator<RestorationEntity> byWave = Comparator
                    .comparingInt((RestorationEntity entity) -> entityWave(entity, rotation))
                    .thenComparingInt(RestorationEntity::index);
            decorations.sort(byWave);
            living.sort(Comparator.comparingInt(RestorationEntity::index));
            decorativeEntities = List.copyOf(decorations);
            livingEntities = List.copyOf(living);

            // A reload during the animation must never duplicate authored entities.
            clearAuthoredEntities(level);
            decorativeEntityIndex = 0;
            int completedWave = completedWave();
            if (completedWave != Integer.MIN_VALUE) {
                spawnDecorationsThroughWave(level, completedWave, false);
            }

            if (targetDurationTicks <= 0) {
                targetDurationTicks = calculateTargetDuration(restorationOrder.size());
            }
            runtimePrepared = true;
            return true;
        } catch (RuntimeException exception) {
            TheLostCamera.LOGGER.error("Unable to prepare restoration {} at {}", ruinId, worldPosition, exception);
            return false;
        }
    }

    private RestorationBlock rotate(RestorationBlock target, RestorationRotation rotation) {
        return new RestorationBlock(
                rotation.rotateX(target.x(), target.z()),
                target.y(),
                rotation.rotateZ(target.x(), target.z()),
                target.state().rotate(rotation.vanillaRotation()),
                target.sourceState() == null ? null : target.sourceState().rotate(rotation.vanillaRotation()),
                target.blockEntityTag() == null ? null : target.blockEntityTag().copy()
        );
    }

    private RestorationRotation inferRotation(ServerLevel level, RestorationTemplate template) {
        RestorationRotation best = RestorationRotation.NONE;
        int bestScore = Integer.MIN_VALUE;

        for (RestorationRotation candidate : RestorationRotation.values()) {
            int score = 0;
            for (var entry : template.ruinedBlocks().entrySet()) {
                BlockPos templatePos = entry.getKey();
                if (templatePos.equals(template.ruinedAnchor())) {
                    continue;
                }
                int relativeX = templatePos.getX() - template.ruinedAnchor().getX();
                int relativeY = templatePos.getY() - template.ruinedAnchor().getY();
                int relativeZ = templatePos.getZ() - template.ruinedAnchor().getZ();
                BlockPos worldPos = worldPosition.offset(
                        candidate.rotateX(relativeX, relativeZ),
                        relativeY,
                        candidate.rotateZ(relativeX, relativeZ)
                );
                BlockState expected = entry.getValue().rotate(candidate.vanillaRotation());
                BlockState actual = level.getBlockState(worldPos);
                if (actual.equals(expected)) {
                    score += 4;
                } else if (actual.is(expected.getBlock())) {
                    score += 1;
                } else if (!actual.isAir() || !expected.isAir()) {
                    score -= 1;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        TheLostCamera.LOGGER.debug("Inferred {} rotation for {} at {} with score {}", best, ruinId, worldPosition, bestScore);
        return best;
    }

    private RestorationPattern currentPattern() {
        RestorationPattern[] patterns = RestorationPattern.values();
        return patterns[Math.floorMod(patternOrdinal, patterns.length)];
    }

    private RestorationRotation currentRotation() {
        RestorationRotation[] rotations = RestorationRotation.values();
        return rotations[Math.floorMod(rotationOrdinal, rotations.length)];
    }

    private int dynamicBlockBudget() {
        int remainingBlocks = restorationOrder.size() - restorationIndex;
        int remainingTicks = Math.max(1, targetDurationTicks - restorationAge);
        int required = (remainingBlocks + remainingTicks - 1) / remainingTicks;
        if (remainingBlocks > 48 && level != null && level.getRandom().nextFloat() < 0.18F) {
            required++;
        }
        return Math.max(1, Math.min(ModConfig.RESTORATION_MAX_BLOCKS_PER_TICK.get(), required));
    }

    private int calculateTargetDuration(int blockCount) {
        int minimum = ModConfig.RESTORATION_MIN_DURATION_TICKS.get();
        int maximum = Math.max(minimum, ModConfig.RESTORATION_MAX_DURATION_TICKS.get());
        RuinSizeClass sizeClass = RuinCatalog.find(ruinId)
                .map(RuinCatalog.Entry::size)
                .orElse(RuinSizeClass.SMALL);
        int authoredTarget = sizeClass.targetDurationTicks();
        int densityAdjustment = (int) Math.round(Math.sqrt(Math.max(1, blockCount)) * 1.25D);
        int scaled = authoredTarget + densityAdjustment;
        return Math.max(minimum, Math.min(maximum, scaled));
    }

    private int countInitialDifferences(ServerLevel level) {
        int differences = 0;
        for (RestorationBlock target : restorationOrder) {
            BlockPos worldTarget = worldPosition.offset(target.x(), target.y(), target.z());
            if (!level.getBlockState(worldTarget).equals(target.state()) || target.hasBlockEntityData()) {
                differences++;
            }
        }
        return Math.max(1, differences);
    }

    private void materializeBlock(ServerLevel level, BlockPos anchorPos, RestorationBlock change,
                                  boolean detailedEffects, boolean finalPass) {
        BlockPos target = anchorPos.offset(change.x(), change.y(), change.z());
        BlockState previousState = level.getBlockState(target);
        BlockState targetState = change.state();
        if (targetState.isAir() && change.requiresSourceMatch() && !matchesSource(previousState, change.sourceState())) {
            return;
        }
        boolean stateChanged = !previousState.equals(targetState);

        if (!targetState.isAir() && !targetState.getCollisionShape(level, target).isEmpty()) {
            protectPlayers(level, target);
        }

        if (stateChanged && detailedEffects && !previousState.isAir()) {
            playRemovalEffects(level, target, previousState);
        }

        if (stateChanged) {
            level.removeBlockEntity(target);
            level.setBlock(target, targetState, BLOCK_FLAGS);
        }

        if (finalPass && change.hasBlockEntityData() && !targetState.isAir()) {
            restoreBlockEntity(level, target, targetState, change.blockEntityTag());
        } else if (targetState.isAir()) {
            level.removeBlockEntity(target);
        }

        if (stateChanged && detailedEffects && !targetState.isAir()) {
            playPlacementEffects(level, target, targetState);
        }

        if (finalPass && !targetState.isAir()) {
            level.sendBlockUpdated(target, previousState, targetState, Block.UPDATE_ALL);
        }
    }

    private boolean matchesSource(BlockState actual, BlockState expected) {
        return actual.equals(expected) || actual.is(expected.getBlock());
    }

    private void restoreBlockEntity(ServerLevel level, BlockPos target, BlockState targetState, CompoundTag authoredTag) {
        CompoundTag tag = authoredTag.copy();
        tag.putInt("x", target.getX());
        tag.putInt("y", target.getY());
        tag.putInt("z", target.getZ());
        level.removeBlockEntity(target);
        BlockEntity restoredBlockEntity = BlockEntity.loadStatic(target, targetState, tag, level.registryAccess());
        if (restoredBlockEntity == null) {
            TheLostCamera.LOGGER.warn("Unable to restore block entity for {} at {}", ruinId, target);
            return;
        }
        level.setBlockEntity(restoredBlockEntity);
        restoredBlockEntity.setChanged();
        level.sendBlockUpdated(target, targetState, targetState, Block.UPDATE_CLIENTS);
    }

    private void playRemovalEffects(ServerLevel level, BlockPos target, BlockState previousState) {
        level.levelEvent(2001, target, Block.getId(previousState));
        level.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, previousState),
                target.getX() + 0.5D,
                target.getY() + 0.5D,
                target.getZ() + 0.5D,
                18,
                0.38D,
                0.38D,
                0.38D,
                0.11D
        );
        level.playSound(
                null,
                target,
                previousState.getSoundType().getBreakSound(),
                SoundSource.BLOCKS,
                0.72F,
                0.72F + level.getRandom().nextFloat() * 0.18F
        );
    }

    private void playPlacementEffects(ServerLevel level, BlockPos target, BlockState targetState) {
        level.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, targetState),
                target.getX() + 0.5D,
                target.getY() - 0.10D,
                target.getZ() + 0.5D,
                22,
                0.42D,
                1.10D,
                0.42D,
                0.13D
        );
        level.playSound(
                null,
                target,
                targetState.getSoundType().getPlaceSound(),
                SoundSource.BLOCKS,
                0.88F,
                0.58F + level.getRandom().nextFloat() * 0.30F
        );
    }

    private void protectPlayers(ServerLevel level, BlockPos target) {
        AABB blockBounds = new AABB(target);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, blockBounds)) {
            int safeY = findSafePlayerY(level, target.above());
            player.teleportTo(target.getX() + 0.5D, safeY + 0.05D, target.getZ() + 0.5D);
        }
    }

    private int findSafePlayerY(ServerLevel level, BlockPos start) {
        BlockPos.MutableBlockPos cursor = start.mutable();
        int ceiling = Math.min(level.getMaxY() - 2, start.getY() + 12);
        while (cursor.getY() <= ceiling) {
            if (level.getBlockState(cursor).isAir() && level.getBlockState(cursor.above()).isAir()) {
                return cursor.getY();
            }
            cursor.move(0, 1, 0);
        }
        return Math.min(level.getMaxY() - 2, start.getY() + 12);
    }

    private void spawnDecorationsThroughWave(ServerLevel level, int wave, boolean effects) {
        RestorationRotation rotation = currentRotation();
        while (decorativeEntityIndex < decorativeEntities.size()) {
            RestorationEntity entity = decorativeEntities.get(decorativeEntityIndex);
            if (entityWave(entity, rotation) > wave) {
                break;
            }
            spawnAuthoredEntity(level, entity, rotation, effects);
            decorativeEntityIndex++;
        }
    }

    private int entityWave(RestorationEntity entity, RestorationRotation rotation) {
        RestorationBlock marker = new RestorationBlock(
                rotation.rotateX((int) Math.floor(entity.x()), (int) Math.floor(entity.z())),
                (int) Math.floor(entity.y()),
                rotation.rotateZ((int) Math.floor(entity.x()), (int) Math.floor(entity.z())),
                Blocks.AIR.defaultBlockState(),
                null,
                null
        );
        return currentPattern().wave(marker);
    }

    private int completedWave() {
        if (restorationOrder.isEmpty() || restorationIndex <= 0) {
            return Integer.MIN_VALUE;
        }

        RestorationPattern pattern = currentPattern();
        int lastProcessedIndex = Math.min(restorationIndex - 1, restorationOrder.size() - 1);
        int lastProcessedWave = pattern.wave(restorationOrder.get(lastProcessedIndex));
        boolean waveStillInProgress = restorationIndex < restorationOrder.size()
                && pattern.wave(restorationOrder.get(restorationIndex)) == lastProcessedWave;
        if (!waveStillInProgress) {
            return lastProcessedWave;
        }

        for (int index = lastProcessedIndex; index >= 0; index--) {
            int candidateWave = pattern.wave(restorationOrder.get(index));
            if (candidateWave != lastProcessedWave) {
                return candidateWave;
            }
        }
        return Integer.MIN_VALUE;
    }

    private void spawnAuthoredEntity(ServerLevel level, RestorationEntity authored,
                                     RestorationRotation rotation, boolean effects) {
        CompoundTag tag = authored.tag().copy();
        tag.remove("UUID");
        tag.remove("UUIDMost");
        tag.remove("UUIDLeast");

        String entityId = tag.getStringOr("id", "minecraft:pig");
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(entityId));
        if (type == null) {
            TheLostCamera.LOGGER.warn("Unknown entity {} in restored structure {}", entityId, ruinId);
            return;
        }

        double rotatedX = rotation.rotateX(authored.x(), authored.z());
        double rotatedZ = rotation.rotateZ(authored.x(), authored.z());
        double worldX = worldPosition.getX() + rotatedX;
        double worldY = worldPosition.getY() + authored.y();
        double worldZ = worldPosition.getZ() + rotatedZ;
        Entity entity = EntityType.loadEntityRecursive(
                type,
                tag,
                level,
                EntitySpawnReason.STRUCTURE,
                loaded -> {
                    loaded.setUUID(UUID.randomUUID());
                    return loaded;
                }
        );
        if (entity == null) {
            TheLostCamera.LOGGER.warn("Unable to restore entity {} for {}", entityId, ruinId);
            return;
        }

        float transformedYaw = entity.rotate(rotation.vanillaRotation());
        entity.snapTo(worldX, worldY, worldZ, transformedYaw, entity.getXRot());
        entity.addTag(authoredEntityTag());
        level.addFreshEntityWithPassengers(entity);
        if (effects) {
            level.sendParticles(
                    ParticleTypes.END_ROD,
                    worldX,
                    worldY + 0.5D,
                    worldZ,
                    10,
                    0.25D,
                    0.35D,
                    0.25D,
                    0.03D
            );
        }
    }

    private AABB restorationBounds() {
        RestorationRotation rotation = currentRotation();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        int[] xs = {0, restorationTemplate.sizeX() - 1};
        int[] ys = {0, restorationTemplate.sizeY() - 1};
        int[] zs = {0, restorationTemplate.sizeZ() - 1};
        for (int x : xs) {
            for (int y : ys) {
                for (int z : zs) {
                    int relativeX = x - restorationTemplate.restoredAnchor().getX();
                    int relativeY = y - restorationTemplate.restoredAnchor().getY();
                    int relativeZ = z - restorationTemplate.restoredAnchor().getZ();
                    int transformedX = worldPosition.getX() + rotation.rotateX(relativeX, relativeZ);
                    int transformedY = worldPosition.getY() + relativeY;
                    int transformedZ = worldPosition.getZ() + rotation.rotateZ(relativeX, relativeZ);
                    minX = Math.min(minX, transformedX);
                    minY = Math.min(minY, transformedY);
                    minZ = Math.min(minZ, transformedZ);
                    maxX = Math.max(maxX, transformedX);
                    maxY = Math.max(maxY, transformedY);
                    maxZ = Math.max(maxZ, transformedZ);
                }
            }
        }
        return new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
    }

    private void clearAuthoredEntities(ServerLevel level) {
        AABB bounds = restorationBounds();
        String authoredTag = authoredEntityTag();
        List<Entity> entities = level.getEntitiesOfClass(
                Entity.class,
                bounds,
                entity -> entity.entityTags().contains(authoredTag)
        );
        for (Entity entity : entities) {
            entity.discard();
        }
    }

    private String authoredEntityTag() {
        return "tlc_restored_" + Integer.toUnsignedString(
                (ruinId + "@" + worldPosition.getX() + "," + worldPosition.getY() + "," + worldPosition.getZ()).hashCode(),
                36
        );
    }

    private void enforceFinalTemplate(ServerLevel level) {
        // First remove every position authored as air.
        for (RestorationBlock target : restorationOrder) {
            if (target.state().isAir()) {
                materializeBlock(level, worldPosition, target, false, true);
            }
        }
        // Then place supports and normal blocks bottom-up.
        restorationOrder.stream()
                .filter(target -> !target.state().isAir())
                .sorted(Comparator.comparingInt(RestorationBlock::y))
                .forEach(target -> materializeBlock(level, worldPosition, target, false, false));
        // A second pass repairs any property changed by neighbour physics.
        restorationOrder.stream()
                .filter(target -> !target.state().isAir())
                .sorted(Comparator.comparingInt(RestorationBlock::y))
                .forEach(target -> materializeBlock(level, worldPosition, target, false, false));
        // Authored inventories, loot tables, text and other BlockEntity data are applied once, at the end.
        for (RestorationBlock target : restorationOrder) {
            if (target.hasBlockEntityData() && !target.state().isAir()) {
                BlockPos worldTarget = worldPosition.offset(target.x(), target.y(), target.z());
                restoreBlockEntity(level, worldTarget, target.state(), target.blockEntityTag());
            }
        }
    }

    private void finishRestoration(ServerLevel level) {
        enforceFinalTemplate(level);

        // Replace only entities previously authored by this exact restoration. Player and world entities are untouched.
        clearAuthoredEntities(level);
        RestorationRotation rotation = currentRotation();
        for (RestorationEntity decoration : decorativeEntities) {
            spawnAuthoredEntity(level, decoration, rotation, false);
        }
        for (RestorationEntity living : livingEntities) {
            spawnAuthoredEntity(level, living, rotation, false);
        }

        AABB climaxBounds = restorationBounds();

        restoring = false;
        restored = true;
        restorationIndex = restorationOrder.size();
        stepDelay = 0;
        runtimePrepared = false;
        restorationTemplate = null;
        restorationOrder = List.of();
        decorativeEntities = List.of();
        livingEntities = List.of();
        decorativeEntityIndex = 0;
        ambientShakeStarted = false;
        resonanceTicks = 140;
        setChanged();

        playRestorationClimax(level, climaxBounds);
        sendShake(level, 28, 1.05F);
    }

    private void playRestorationClimax(ServerLevel level, AABB bounds) {
        double x = (bounds.minX + bounds.maxX) * 0.5D;
        double baseY = bounds.minY;
        double centreY = (bounds.minY + bounds.maxY) * 0.5D;
        double topY = bounds.maxY - 0.5D;
        double z = (bounds.minZ + bounds.maxZ) * 0.5D;

        level.sendParticles(ParticleTypes.END_ROD, x, centreY, z, 42, 1.4D, 1.2D, 1.4D, 0.09D);
        level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE,
                SoundSource.BLOCKS, 3.4F, 0.62F);

        switch (ruinId) {
            case "thelostcamera:solstice_shrine" -> {
                level.sendParticles(ParticleTypes.FLAME, x, baseY + 5.5D, z, 54, 2.2D, 1.4D, 2.2D, 0.035D);
                level.sendParticles(ParticleTypes.ENCHANT, x, centreY, z, 72, 2.8D, 2.0D, 2.8D, 0.18D);
                level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE,
                        SoundSource.BLOCKS, 4.0F, 1.45F);
            }
            case "thelostcamera:orma_homestead" -> {
                level.sendParticles(ParticleTypes.FLAME, x + 3.0D, baseY + 3.0D, z - 1.0D,
                        36, 0.55D, 0.75D, 0.55D, 0.025D);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, baseY + 3.0D, z,
                        28, 3.0D, 1.5D, 3.0D, 0.03D);
                level.playSound(null, worldPosition,
                        Blocks.CAMPFIRE.defaultBlockState().getSoundType().getPlaceSound(),
                        SoundSource.BLOCKS, 2.2F, 0.84F);
            }
            case "thelostcamera:frontier_watchtower" -> {
                level.sendParticles(ParticleTypes.CLOUD, x, topY, z,
                        48, 1.8D, 0.45D, 1.8D, 0.08D);
                level.sendParticles(ParticleTypes.FLAME, x, topY + 0.5D, z,
                        30, 0.75D, 0.80D, 0.75D, 0.03D);
                level.playSound(null, worldPosition, SoundEvents.END_PORTAL_FRAME_FILL,
                        SoundSource.BLOCKS, 3.0F, 0.74F);
            }
            case "thelostcamera:maia_pyramid" -> {
                level.sendParticles(ParticleTypes.COMPOSTER, x, baseY + 2.5D, z,
                        54, 3.8D, 1.4D, 3.8D, 0.08D);
                level.sendParticles(ParticleTypes.ENCHANT, x, centreY, z,
                        72, 2.2D, 2.2D, 2.2D, 0.16D);
                level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE,
                        SoundSource.BLOCKS, 3.4F, 1.18F);
            }
            case "thelostcamera:watchers_waystone" -> {
                level.sendParticles(ParticleTypes.WAX_ON, x, topY - 0.5D, z,
                        36, 1.2D, 1.8D, 1.2D, 0.08D);
                level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE,
                        SoundSource.BLOCKS, 2.4F, 0.82F);
            }
            case "thelostcamera:watchers_sacred_basin" -> {
                level.sendParticles(ParticleTypes.SPLASH, x, baseY + 3.0D, z,
                        72, 3.8D, 0.8D, 3.8D, 0.10D);
                level.sendParticles(ParticleTypes.END_ROD, x, centreY, z,
                        46, 2.4D, 1.8D, 2.4D, 0.045D);
                level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE,
                        SoundSource.BLOCKS, 3.2F, 1.58F);
            }
            default -> {
            }
        }
    }

    private void abortRestoration() {
        restoring = false;
        restorationOrder = List.of();
        decorativeEntities = List.of();
        livingEntities = List.of();
        restorationTemplate = null;
        runtimePrepared = false;
        setChanged();
    }

    private void playAmbientPulse(ServerLevel level) {
        if (ambientTicker % 30 == 0) {
            level.playSound(
                    null,
                    worldPosition,
                    SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.AMBIENT,
                    3.2F,
                    0.26F + level.getRandom().nextFloat() * 0.16F
            );
        }
        if (ambientTicker % 71 == 0) {
            level.playSound(
                    null,
                    worldPosition,
                    SoundEvents.BEACON_AMBIENT,
                    SoundSource.AMBIENT,
                    3.0F,
                    0.46F
            );
        }
    }

    private void tickLingeringResonance(ServerLevel level) {
        if (resonanceTicks <= 0) {
            return;
        }
        resonanceTicks--;
        if (resonanceTicks % 36 == 0) {
            level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.AMBIENT, 2.8F, 0.34F + level.getRandom().nextFloat() * 0.08F);
        }
        if (resonanceTicks == 1) {
            level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.AMBIENT, 2.6F, 0.72F);
        }
        setChanged();
    }

    private void tickDiscoveryHint(ServerLevel level) {
        if (restored || restoring) {
            return;
        }
        long phase = level.getGameTime() + worldPosition.getX() * 31L + worldPosition.getZ() * 17L;
        if (Math.floorMod(phase, HINT_CHECK_INTERVAL) != 0L) {
            return;
        }

        Vec3 anchorCentre = Vec3.atCenterOf(worldPosition);
        for (ServerPlayer player : level.players()) {
            if (hintedPlayers.contains(player.getUUID()) || hasAnyLostCameraItem(player)) {
                continue;
            }
            if (player.distanceToSqr(anchorCentre.x, anchorCentre.y, anchorCentre.z) > HINT_RANGE_SQUARED) {
                continue;
            }

            Vec3 direction = anchorCentre.subtract(player.getEyePosition()).normalize();
            if (player.getLookAngle().dot(direction) < 0.25D) {
                continue;
            }

            hintedPlayers.add(player.getUUID());
            player.sendSystemMessage(Component.translatable("message.thelostcamera.ruin_proximity_hint"));
        }
    }

    private static boolean hasAnyLostCameraItem(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (isLostCameraItem(player.getItemInHand(hand))) {
                return true;
            }
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (isLostCameraItem(player.getInventory().getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLostCameraItem(ItemStack stack) {
        return stack.getItem() == ModItems.LOST_CAMERA.get()
                || stack.getItem() == ModItems.PHOTO_ALBUM.get()
                || stack.getItem() == ModItems.PHOTOGRAPH.get()
                || stack.getItem() == ModItems.PHOTOGRAPHIC_PLATE.get();
    }

    private void sendShake(ServerLevel level, int durationTicks, float intensity) {
        if (intensity <= 0.0F) {
            return;
        }

        for (ServerPlayer player : level.players()) {
            double distance = player.distanceToSqr(
                    worldPosition.getX() + 0.5D,
                    worldPosition.getY() + 0.5D,
                    worldPosition.getZ() + 0.5D
            );
            if (distance <= EFFECT_RADIUS_SQUARED) {
                double falloff = 1.0D - Math.sqrt(distance) / EFFECT_RADIUS;
                float adjustedIntensity = (float) (intensity * (0.15D + 0.85D * Math.max(0.0D, falloff)));
                PacketDistributor.sendToPlayer(
                        player,
                        new RestorationShakePayload(durationTicks, adjustedIntensity)
                );
            }
        }
    }

    public String getRuinId() {
        return ruinId;
    }

    public boolean isRestored() {
        return restored;
    }

    public boolean isRestoring() {
        return restoring;
    }

    @Override
    public void setChanged() {
        if (!virtualRuntime) {
            super.setChanged();
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("ruin_id", ruinId);
        output.putBoolean("restored", restored);
        output.putBoolean("restoring", restoring);
        output.putInt("restoration_index", restorationIndex);
        output.putInt("step_delay", stepDelay);
        output.putInt("ambient_ticker", ambientTicker);
        output.putInt("pattern_ordinal", patternOrdinal);
        output.putInt("target_duration_ticks", targetDurationTicks);
        output.putInt("restoration_age", restorationAge);
        output.putBoolean("ambient_shake_started", ambientShakeStarted);
        output.putLong("restoration_seed", restorationSeed);
        output.putInt("resonance_ticks", resonanceTicks);
        output.putInt("rotation_ordinal", rotationOrdinal);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ruinId = input.getStringOr("ruin_id", DEFAULT_RUIN_ID);
        restored = input.getBooleanOr("restored", false);
        restoring = input.getBooleanOr("restoring", false);
        restorationIndex = input.getIntOr("restoration_index", 0);
        stepDelay = input.getIntOr("step_delay", 0);
        ambientTicker = input.getIntOr("ambient_ticker", 0);
        patternOrdinal = input.getIntOr("pattern_ordinal", 0);
        targetDurationTicks = input.getIntOr("target_duration_ticks", 0);
        restorationAge = input.getIntOr("restoration_age", 0);
        ambientShakeStarted = input.getBooleanOr("ambient_shake_started", false);
        restorationSeed = input.getLongOr("restoration_seed", 0L);
        resonanceTicks = input.getIntOr("resonance_ticks", 0);
        rotationOrdinal = input.getIntOr("rotation_ordinal", 0);
        restorationTemplate = null;
        restorationOrder = List.of();
        decorativeEntities = List.of();
        livingEntities = List.of();
        decorativeEntityIndex = 0;
        runtimePrepared = false;
        virtualRuntime = false;
        hintedPlayers = new HashSet<>();
    }
}
