package com.levelscraft7.thelostcamera.ruin;

import com.levelscraft7.thelostcamera.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recognizes generated ruins from their authored ruined NBT signature.
 * No technical block needs to exist in the world.
 */
public final class RuinDetector {
    private static final int SAMPLE_LIMIT = 96;
    private static final int MIN_SAMPLE_SCORE = 18;
    private static final int MIN_FULL_SCORE = 36;
    private static final int CAMERA_PROBE_RADIUS = 4;
    private static final int DEBUG_PROBE_RADIUS = 12;

    private RuinDetector() {
    }

    public static FramedRuin findFramedRuin(ServerPlayer player) {
        int range = ModConfig.RUIN_DETECTION_RANGE.get();
        int radius = ModConfig.RUIN_TARGET_SEARCH_RADIUS.get();
        HitResult hit = player.pick(range, 0.0F, false);
        BlockPos center = BlockPos.containing(hit.getLocation());
        return findNearest(player.level(), player, center, range, radius, CAMERA_PROBE_RADIUS);
    }

    public static FramedRuin findNearestDebug(ServerPlayer player) {
        int range = ModConfig.RUIN_DETECTION_RANGE.get();
        int radius = ModConfig.RUIN_TARGET_SEARCH_RADIUS.get();
        return findNearest(player.level(), player, player.blockPosition(), range, radius, DEBUG_PROBE_RADIUS);
    }

    public static boolean hasFramedRuin(Player player) {
        int range = ModConfig.RUIN_DETECTION_RANGE.get();
        int radius = ModConfig.RUIN_TARGET_SEARCH_RADIUS.get();
        HitResult hit = player.pick(range, 0.0F, false);
        BlockPos center = BlockPos.containing(hit.getLocation());
        return findNearest(player.level(), player, center, range, radius, CAMERA_PROBE_RADIUS) != null;
    }

    private static FramedRuin findNearest(Level level, Player player, BlockPos center, int range, int radius, int probeRadius) {
        double maximumPlayerDistance = (double) range * range;

        FramedRuin best = null;
        BlockPos observedMin = center.offset(-probeRadius, -probeRadius, -probeRadius);
        BlockPos observedMax = center.offset(probeRadius, probeRadius, probeRadius);
        List<BlockPos> observedSolidBlocks = solidBlocksAround(level, observedMin, observedMax);

        for (RuinCatalog.Entry entry : RuinCatalog.entries()) {
            RestorationTemplate template;
            try {
                template = RestorationTemplateLoader.load(entry.id().toString());
            } catch (RuntimeException ignored) {
                continue;
            }

            List<Map.Entry<BlockPos, BlockState>> ruinedSample = sample(new ArrayList<>(template.ruinedBlocks().entrySet()));
            List<Map.Entry<BlockPos, BlockState>> ruinedFull = new ArrayList<>(template.ruinedBlocks().entrySet());
            List<Map.Entry<BlockPos, BlockState>> restoredFull = restoredSignature(template);
            List<Map.Entry<BlockPos, BlockState>> restoredSample = sample(restoredFull);
            for (RestorationRotation rotation : RestorationRotation.values()) {
                best = bestOf(best, scanSignature(level, player, center, radius, observedSolidBlocks,
                        template, rotation, ruinedSample, ruinedFull, false, false,
                        maximumPlayerDistance, entry.id().toString()));
                best = bestOf(best, scanSignature(level, player, center, radius, observedSolidBlocks,
                        template, rotation, restoredSample, restoredFull, true, true,
                        maximumPlayerDistance, entry.id().toString()));
            }
        }
        return best;
    }

    private static FramedRuin scanSignature(
            Level level,
            Player player,
            BlockPos center,
            int radius,
            List<BlockPos> observedSolidBlocks,
            RestorationTemplate template,
            RestorationRotation rotation,
            List<Map.Entry<BlockPos, BlockState>> sample,
            List<Map.Entry<BlockPos, BlockState>> full,
            boolean relativePositions,
            boolean alreadyRestored,
            double maximumPlayerDistance,
            String ruinId
    ) {
        FramedRuin best = null;
        Set<BlockPos> candidates = candidatesFromObservedBlocks(level, center, radius, observedSolidBlocks,
                template, rotation, sample, relativePositions);
        for (BlockPos candidate : candidates) {
            if (player.distanceToSqr(
                    candidate.getX() + 0.5D,
                    candidate.getY() + 0.5D,
                    candidate.getZ() + 0.5D
            ) > maximumPlayerDistance) {
                continue;
            }
            if (level instanceof ServerLevel serverLevel && VirtualRuinRestorationManager.isRestoring(serverLevel, candidate)) {
                continue;
            }

            int sampleScore = score(level, candidate, template, rotation, sample, relativePositions);
            if (sampleScore < MIN_SAMPLE_SCORE) {
                continue;
            }

            int fullScore = score(level, candidate, template, rotation, full, relativePositions);
            if (fullScore < MIN_FULL_SCORE) {
                continue;
            }

            double hitDistance = candidate.distSqr(center);
            FramedRuin found = new FramedRuin(ruinId, candidate.immutable(), rotation,
                    fullScore, hitDistance, alreadyRestored);
            best = bestOf(best, found);
        }
        return best;
    }

    private static FramedRuin bestOf(FramedRuin current, FramedRuin candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null
                || candidate.score > current.score
                || (candidate.score == current.score && candidate.hitDistance < current.hitDistance)) {
            return candidate;
        }
        return current;
    }

    private static List<BlockPos> solidBlocksAround(Level level, BlockPos min, BlockPos max) {
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.getBlockState(pos).isAir()) {
                result.add(pos.immutable());
            }
        }
        return result;
    }

    private static Set<BlockPos> candidatesFromObservedBlocks(
            Level level,
            BlockPos center,
            int radius,
            List<BlockPos> observedSolidBlocks,
            RestorationTemplate template,
            RestorationRotation rotation,
            List<Map.Entry<BlockPos, BlockState>> sample,
            boolean relativePositions
    ) {
        Set<BlockPos> candidates = new HashSet<>();
        for (BlockPos observed : observedSolidBlocks) {
            BlockState actual = level.getBlockState(observed);
            for (Map.Entry<BlockPos, BlockState> sampleEntry : sample) {
                BlockState expected = sampleEntry.getValue().rotate(rotation.vanillaRotation());
                if (!actual.equals(expected) && !actual.is(expected.getBlock())) {
                    continue;
                }

                BlockPos templatePos = sampleEntry.getKey();
                int relativeX = relativePositions ? templatePos.getX() : templatePos.getX() - template.ruinedAnchor().getX();
                int relativeY = relativePositions ? templatePos.getY() : templatePos.getY() - template.ruinedAnchor().getY();
                int relativeZ = relativePositions ? templatePos.getZ() : templatePos.getZ() - template.ruinedAnchor().getZ();
                BlockPos candidate = observed.offset(
                        -rotation.rotateX(relativeX, relativeZ),
                        -relativeY,
                        -rotation.rotateZ(relativeX, relativeZ)
                );
                if (Math.abs(candidate.getX() - center.getX()) <= radius
                        && Math.abs(candidate.getY() - center.getY()) <= radius
                        && Math.abs(candidate.getZ() - center.getZ()) <= radius) {
                    candidates.add(candidate.immutable());
                }
            }
        }
        return candidates;
    }

    private static List<Map.Entry<BlockPos, BlockState>> restoredSignature(RestorationTemplate template) {
        List<Map.Entry<BlockPos, BlockState>> result = new ArrayList<>();
        for (RestorationBlock target : template.restoredTargets()) {
            if (!target.state().isAir()) {
                result.add(Map.entry(new BlockPos(target.x(), target.y(), target.z()), target.state()));
            }
        }
        return result;
    }

    private static List<Map.Entry<BlockPos, BlockState>> sample(List<Map.Entry<BlockPos, BlockState>> entries) {
        entries = new ArrayList<>(entries);
        entries.sort(Comparator
                .<Map.Entry<BlockPos, BlockState>>comparingInt(entry -> entry.getValue().getBlock().hashCode())
                .thenComparingInt(entry -> entry.getKey().getY())
                .thenComparingInt(entry -> entry.getKey().getX())
                .thenComparingInt(entry -> entry.getKey().getZ()));
        if (entries.size() <= SAMPLE_LIMIT) {
            return entries;
        }

        List<Map.Entry<BlockPos, BlockState>> result = new ArrayList<>(SAMPLE_LIMIT);
        double stride = (double) entries.size() / SAMPLE_LIMIT;
        for (int index = 0; index < SAMPLE_LIMIT; index++) {
            result.add(entries.get(Math.min(entries.size() - 1, (int) Math.floor(index * stride))));
        }
        return result;
    }

    private static int score(
            Level level,
            BlockPos worldAnchor,
            RestorationTemplate template,
            RestorationRotation rotation,
            List<Map.Entry<BlockPos, BlockState>> entries,
            boolean relativePositions
    ) {
        int score = 0;
        for (Map.Entry<BlockPos, BlockState> entry : entries) {
            BlockPos templatePos = entry.getKey();
            int relativeX = relativePositions ? templatePos.getX() : templatePos.getX() - template.ruinedAnchor().getX();
            int relativeY = relativePositions ? templatePos.getY() : templatePos.getY() - template.ruinedAnchor().getY();
            int relativeZ = relativePositions ? templatePos.getZ() : templatePos.getZ() - template.ruinedAnchor().getZ();
            BlockPos worldPos = worldAnchor.offset(
                    rotation.rotateX(relativeX, relativeZ),
                    relativeY,
                    rotation.rotateZ(relativeX, relativeZ)
            );
            BlockState expected = entry.getValue().rotate(rotation.vanillaRotation());
            BlockState actual = level.getBlockState(worldPos);
            if (actual.equals(expected)) {
                score += 4;
            } else if (actual.is(expected.getBlock())) {
                score += 2;
            } else if (actual.isAir()) {
                score -= 1;
            } else {
                score -= 2;
            }
        }
        return score;
    }

    public record FramedRuin(String ruinId, BlockPos anchorPos, RestorationRotation rotation,
                             int score, double hitDistance, boolean alreadyRestored) {
    }
}
