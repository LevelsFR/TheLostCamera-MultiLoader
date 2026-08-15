package com.levelscraft7.thelostcamera.ruin;

import com.levelscraft7.thelostcamera.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recognizes generated ruins from their authored ruined/restored NBT signatures.
 * No technical marker block needs to remain in the generated world.
 *
 * <p>Detection deliberately uses three independent signals:</p>
 * <ul>
 *     <li>the authored solid-block geometry,</li>
 *     <li>rarer/distinctive blocks from that exact template,</li>
 *     <li>a sampled set of positions that the authored template expects to be empty.</li>
 * </ul>
 *
 * <p>This prevents ordinary terrain or player builds made from common materials from reaching a small absolute
 * score and being mistaken for a ruin. Prepared signatures and rotations are cached because this detector also runs
 * for the live camera viewfinder.</p>
 */
public final class RuinDetector {
    private static final int SAMPLE_LIMIT = 96;
    private static final int EMPTY_SAMPLE_LIMIT = 96;
    private static final int CAMERA_PROBE_RADIUS = 4;
    private static final int DEBUG_PROBE_RADIUS = 12;

    private static final double MIN_SAMPLE_MATCH_RATIO = 0.60D;
    private static final double MIN_FULL_MATCH_RATIO = 0.72D;
    private static final double MIN_EXACT_MATCH_RATIO = 0.28D;
    private static final double MIN_KEY_MATCH_RATIO = 0.40D;
    private static final double MIN_EMPTY_MATCH_RATIO = 0.60D;

    private static final Map<String, PreparedRuin> PREPARED = new ConcurrentHashMap<>();

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
        if (observedSolidBlocks.isEmpty()) {
            return null;
        }

        for (RuinCatalog.Entry entry : RuinCatalog.entries()) {
            PreparedRuin prepared;
            try {
                prepared = PREPARED.computeIfAbsent(entry.id().toString(), ignored -> prepare(entry));
            } catch (RuntimeException ignored) {
                continue;
            }

            for (RestorationRotation rotation : RestorationRotation.values()) {
                PreparedRotation signatures = prepared.rotations.get(rotation);
                best = bestOf(best, scanSignature(level, player, center, radius, observedSolidBlocks,
                        rotation, signatures.ruinedSample, signatures.ruinedFull, signatures.ruinedKeys,
                        signatures.ruinedEmpty, false, maximumPlayerDistance, prepared.ruinId));
                best = bestOf(best, scanSignature(level, player, center, radius, observedSolidBlocks,
                        rotation, signatures.restoredSample, signatures.restoredFull, signatures.restoredKeys,
                        signatures.restoredEmpty, true, maximumPlayerDistance, prepared.ruinId));
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
            RestorationRotation rotation,
            List<SignatureEntry> sample,
            List<SignatureEntry> full,
            List<SignatureEntry> keyEntries,
            List<EmptyEntry> emptyEntries,
            boolean alreadyRestored,
            double maximumPlayerDistance,
            String ruinId
    ) {
        FramedRuin best = null;
        Set<BlockPos> candidates = candidatesFromObservedBlocks(level, center, radius, observedSolidBlocks, sample);
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

            MatchStats sampleStats = matchStats(level, candidate, sample);
            if (sampleStats.matchRatio() < MIN_SAMPLE_MATCH_RATIO) {
                continue;
            }

            MatchStats keyStats = matchStats(level, candidate, keyEntries);
            int minimumKeyMatches = Math.min(2, keyEntries.size());
            if (!keyEntries.isEmpty()
                    && (keyStats.matches() < minimumKeyMatches || keyStats.matchRatio() < MIN_KEY_MATCH_RATIO)) {
                continue;
            }

            MatchStats fullStats = matchStats(level, candidate, full);
            if (fullStats.matchRatio() < MIN_FULL_MATCH_RATIO
                    || fullStats.exactRatio() < MIN_EXACT_MATCH_RATIO) {
                continue;
            }

            double emptyRatio = emptyMatchRatio(level, candidate, emptyEntries);
            if (!emptyEntries.isEmpty() && emptyRatio < MIN_EMPTY_MATCH_RATIO) {
                continue;
            }

            int confidence = confidenceScore(fullStats, keyStats, emptyRatio);
            double hitDistance = candidate.distSqr(center);
            FramedRuin found = new FramedRuin(ruinId, candidate.immutable(), rotation,
                    confidence, hitDistance, alreadyRestored);
            best = bestOf(best, found);
        }
        return best;
    }

    private static int confidenceScore(MatchStats full, MatchStats keys, double emptyRatio) {
        double keyRatio = keys.total() <= 0 ? 1.0D : keys.matchRatio();
        return (int) Math.round(
                full.matchRatio() * 1_000.0D
                        + full.exactRatio() * 250.0D
                        + keyRatio * 250.0D
                        + emptyRatio * 200.0D
        );
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
            List<SignatureEntry> sample
    ) {
        Set<BlockPos> candidates = new HashSet<>();
        for (BlockPos observed : observedSolidBlocks) {
            BlockState actual = level.getBlockState(observed);
            for (SignatureEntry sampleEntry : sample) {
                BlockState expected = sampleEntry.expectedState;
                if (!actual.equals(expected) && !actual.is(expected.getBlock())) {
                    continue;
                }

                BlockPos candidate = observed.offset(
                        -sampleEntry.relativeX,
                        -sampleEntry.relativeY,
                        -sampleEntry.relativeZ
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

    private static PreparedRuin prepare(RuinCatalog.Entry entry) {
        RestorationTemplate template = RestorationTemplateLoader.load(entry.id().toString());
        List<Map.Entry<BlockPos, BlockState>> ruinedFull = new ArrayList<>(template.ruinedBlocks().entrySet());
        List<Map.Entry<BlockPos, BlockState>> ruinedSample = sample(ruinedFull);
        List<Map.Entry<BlockPos, BlockState>> ruinedKeys = distinctiveEntries(ruinedFull);

        List<Map.Entry<BlockPos, BlockState>> restoredFull = restoredSignature(template);
        List<Map.Entry<BlockPos, BlockState>> restoredSample = sample(restoredFull);
        List<Map.Entry<BlockPos, BlockState>> restoredKeys = distinctiveEntries(restoredFull);

        Set<BlockPos> ruinedSolidRelative = relativeSolidPositions(ruinedFull, template.ruinedAnchor(), RestorationRotation.NONE);
        Set<BlockPos> restoredSolidRelative = new HashSet<>();
        for (Map.Entry<BlockPos, BlockState> restored : restoredFull) {
            restoredSolidRelative.add(restored.getKey().immutable());
        }

        List<BlockPos> ruinedEmpty = emptySample(
                template.sizeX(), template.sizeY(), template.sizeZ(),
                template.ruinedAnchor(), RestorationRotation.NONE, ruinedSolidRelative
        );
        List<BlockPos> restoredEmpty = emptySample(
                template.sizeX(), template.sizeY(), template.sizeZ(),
                template.restoredAnchor(), template.restoredAlignment(), restoredSolidRelative
        );

        Map<RestorationRotation, PreparedRotation> rotations = new EnumMap<>(RestorationRotation.class);
        for (RestorationRotation rotation : RestorationRotation.values()) {
            rotations.put(rotation, new PreparedRotation(
                    prepareEntries(ruinedSample, template.ruinedAnchor(), rotation, false),
                    prepareEntries(ruinedFull, template.ruinedAnchor(), rotation, false),
                    prepareEntries(ruinedKeys, template.ruinedAnchor(), rotation, false),
                    prepareEmptyEntries(ruinedEmpty, rotation),
                    prepareEntries(restoredSample, BlockPos.ZERO, rotation, true),
                    prepareEntries(restoredFull, BlockPos.ZERO, rotation, true),
                    prepareEntries(restoredKeys, BlockPos.ZERO, rotation, true),
                    prepareEmptyEntries(restoredEmpty, rotation)
            ));
        }
        return new PreparedRuin(entry.id().toString(), Map.copyOf(rotations));
    }

    private static Set<BlockPos> relativeSolidPositions(
            List<Map.Entry<BlockPos, BlockState>> entries,
            BlockPos anchor,
            RestorationRotation alignment
    ) {
        Set<BlockPos> result = new HashSet<>();
        for (Map.Entry<BlockPos, BlockState> entry : entries) {
            BlockPos pos = entry.getKey();
            int relativeX = pos.getX() - anchor.getX();
            int relativeY = pos.getY() - anchor.getY();
            int relativeZ = pos.getZ() - anchor.getZ();
            result.add(new BlockPos(
                    alignment.rotateX(relativeX, relativeZ),
                    relativeY,
                    alignment.rotateZ(relativeX, relativeZ)
            ));
        }
        return result;
    }

    private static List<BlockPos> emptySample(
            int sizeX,
            int sizeY,
            int sizeZ,
            BlockPos anchor,
            RestorationRotation alignment,
            Set<BlockPos> alignedSolidRelative
    ) {
        List<BlockPos> empty = new ArrayList<>();
        for (int y = 0; y < sizeY; y++) {
            for (int x = 0; x < sizeX; x++) {
                for (int z = 0; z < sizeZ; z++) {
                    BlockPos templatePos = new BlockPos(x, y, z);
                    if (templatePos.equals(anchor)) {
                        continue;
                    }
                    int relativeX = x - anchor.getX();
                    int relativeY = y - anchor.getY();
                    int relativeZ = z - anchor.getZ();
                    BlockPos aligned = new BlockPos(
                            alignment.rotateX(relativeX, relativeZ),
                            relativeY,
                            alignment.rotateZ(relativeX, relativeZ)
                    );
                    if (!alignedSolidRelative.contains(aligned)) {
                        empty.add(aligned);
                    }
                }
            }
        }
        return samplePositions(empty, EMPTY_SAMPLE_LIMIT);
    }

    private static List<EmptyEntry> prepareEmptyEntries(List<BlockPos> positions, RestorationRotation rotation) {
        List<EmptyEntry> result = new ArrayList<>(positions.size());
        for (BlockPos relative : positions) {
            result.add(new EmptyEntry(
                    rotation.rotateX(relative.getX(), relative.getZ()),
                    relative.getY(),
                    rotation.rotateZ(relative.getX(), relative.getZ())
            ));
        }
        return List.copyOf(result);
    }

    private static List<SignatureEntry> prepareEntries(
            List<Map.Entry<BlockPos, BlockState>> entries,
            BlockPos anchor,
            RestorationRotation rotation,
            boolean relativePositions
    ) {
        List<SignatureEntry> prepared = new ArrayList<>(entries.size());
        for (Map.Entry<BlockPos, BlockState> entry : entries) {
            BlockPos templatePos = entry.getKey();
            int relativeX = relativePositions ? templatePos.getX() : templatePos.getX() - anchor.getX();
            int relativeY = relativePositions ? templatePos.getY() : templatePos.getY() - anchor.getY();
            int relativeZ = relativePositions ? templatePos.getZ() : templatePos.getZ() - anchor.getZ();
            prepared.add(new SignatureEntry(
                    rotation.rotateX(relativeX, relativeZ),
                    relativeY,
                    rotation.rotateZ(relativeX, relativeZ),
                    entry.getValue().rotate(rotation.vanillaRotation())
            ));
        }
        return List.copyOf(prepared);
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

    private static List<Map.Entry<BlockPos, BlockState>> distinctiveEntries(
            List<Map.Entry<BlockPos, BlockState>> entries
    ) {
        if (entries.isEmpty()) {
            return List.of();
        }

        Map<Block, Integer> frequencies = new HashMap<>();
        for (Map.Entry<BlockPos, BlockState> entry : entries) {
            frequencies.merge(entry.getValue().getBlock(), 1, Integer::sum);
        }

        int maximumFrequency = Math.max(8, entries.size() / 20);
        List<Map.Entry<BlockPos, BlockState>> result = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : entries) {
            if (frequencies.getOrDefault(entry.getValue().getBlock(), Integer.MAX_VALUE) <= maximumFrequency) {
                result.add(entry);
            }
        }

        if (result.size() >= 2) {
            return sample(result);
        }

        List<Map.Entry<BlockPos, BlockState>> fallback = new ArrayList<>(entries);
        fallback.sort(Comparator
                .comparingInt((Map.Entry<BlockPos, BlockState> candidate) ->
                        frequencies.getOrDefault(candidate.getValue().getBlock(), Integer.MAX_VALUE))
                .thenComparingInt(candidate -> candidate.getKey().getY())
                .thenComparingInt(candidate -> candidate.getKey().getX())
                .thenComparingInt(candidate -> candidate.getKey().getZ()));
        return List.copyOf(fallback.subList(0, Math.min(8, fallback.size())));
    }

    private static List<Map.Entry<BlockPos, BlockState>> sample(List<Map.Entry<BlockPos, BlockState>> entries) {
        entries = new ArrayList<>(entries);
        entries.sort(Comparator
                .<Map.Entry<BlockPos, BlockState>>comparingInt(entry -> entry.getValue().getBlock().hashCode())
                .thenComparingInt(entry -> entry.getKey().getY())
                .thenComparingInt(entry -> entry.getKey().getX())
                .thenComparingInt(entry -> entry.getKey().getZ()));
        if (entries.size() <= SAMPLE_LIMIT) {
            return List.copyOf(entries);
        }

        List<Map.Entry<BlockPos, BlockState>> result = new ArrayList<>(SAMPLE_LIMIT);
        double stride = (double) entries.size() / SAMPLE_LIMIT;
        for (int index = 0; index < SAMPLE_LIMIT; index++) {
            result.add(entries.get(Math.min(entries.size() - 1, (int) Math.floor(index * stride))));
        }
        return List.copyOf(result);
    }

    private static List<BlockPos> samplePositions(List<BlockPos> positions, int limit) {
        if (positions.size() <= limit) {
            return List.copyOf(positions);
        }
        List<BlockPos> result = new ArrayList<>(limit);
        double stride = (double) positions.size() / limit;
        for (int index = 0; index < limit; index++) {
            result.add(positions.get(Math.min(positions.size() - 1, (int) Math.floor(index * stride))).immutable());
        }
        return List.copyOf(result);
    }

    private static MatchStats matchStats(Level level, BlockPos worldAnchor, List<SignatureEntry> entries) {
        if (entries.isEmpty()) {
            return MatchStats.EMPTY;
        }
        int exact = 0;
        int block = 0;
        for (SignatureEntry entry : entries) {
            BlockPos worldPos = worldAnchor.offset(entry.relativeX, entry.relativeY, entry.relativeZ);
            BlockState actual = level.getBlockState(worldPos);
            if (actual.equals(entry.expectedState)) {
                exact++;
            } else if (actual.is(entry.expectedState.getBlock())) {
                block++;
            }
        }
        return new MatchStats(exact, block, entries.size());
    }

    private static double emptyMatchRatio(Level level, BlockPos worldAnchor, List<EmptyEntry> entries) {
        if (entries.isEmpty()) {
            return 1.0D;
        }
        int empty = 0;
        for (EmptyEntry entry : entries) {
            BlockPos worldPos = worldAnchor.offset(entry.relativeX, entry.relativeY, entry.relativeZ);
            if (level.getBlockState(worldPos).isAir()) {
                empty++;
            }
        }
        return (double) empty / entries.size();
    }

    public record FramedRuin(String ruinId, BlockPos anchorPos, RestorationRotation rotation,
                             int score, double hitDistance, boolean alreadyRestored) {
    }

    private record PreparedRuin(String ruinId, Map<RestorationRotation, PreparedRotation> rotations) {
    }

    private record PreparedRotation(
            List<SignatureEntry> ruinedSample,
            List<SignatureEntry> ruinedFull,
            List<SignatureEntry> ruinedKeys,
            List<EmptyEntry> ruinedEmpty,
            List<SignatureEntry> restoredSample,
            List<SignatureEntry> restoredFull,
            List<SignatureEntry> restoredKeys,
            List<EmptyEntry> restoredEmpty
    ) {
    }

    private record SignatureEntry(int relativeX, int relativeY, int relativeZ, BlockState expectedState) {
    }

    private record EmptyEntry(int relativeX, int relativeY, int relativeZ) {
    }

    private record MatchStats(int exactMatches, int blockMatches, int total) {
        private static final MatchStats EMPTY = new MatchStats(0, 0, 0);

        int matches() {
            return exactMatches + blockMatches;
        }

        double matchRatio() {
            return total <= 0 ? 1.0D : (double) matches() / total;
        }

        double exactRatio() {
            return total <= 0 ? 1.0D : (double) exactMatches / total;
        }
    }
}
