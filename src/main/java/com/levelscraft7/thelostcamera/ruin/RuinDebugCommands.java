package com.levelscraft7.thelostcamera.ruin;

import com.levelscraft7.thelostcamera.album.PlayerAlbumStorage;
import com.levelscraft7.thelostcamera.network.ServerPayloadHandlers;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class RuinDebugCommands {
    private RuinDebugCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("thelostcamera")
                .requires(RuinDebugCommands::isAdmin)
                .then(Commands.literal("scan_ruin")
                        .executes(context -> scan(context.getSource())))
                .then(Commands.literal("restore_nearest")
                        .executes(context -> restoreNearest(context.getSource())))
                .then(Commands.literal("validate_ruins")
                        .executes(context -> validate(context.getSource())))
                .then(Commands.literal("album")
                        .then(Commands.literal("clear")
                                .executes(context -> clearAlbum(context.getSource(), context.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> clearAlbum(context.getSource(),
                                                EntityArgument.getPlayer(context, "player")))))));
    }

    private static int scan(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        RuinDetector.FramedRuin ruin = RuinDetector.findFramedRuin(player);
        if (ruin == null) {
            source.sendFailure(Component.literal("No The Lost Camera ruin signature framed."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "Framed " + ruin.ruinId()
                        + " at " + format(ruin.anchorPos())
                        + " rotation=" + ruin.rotation()
                        + " score=" + ruin.score()
        ), false);
        return 1;
    }

    private static int restoreNearest(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = (ServerLevel) player.level();
        RuinDetector.FramedRuin ruin = RuinDetector.findNearestDebug(player);
        if (ruin == null) {
            source.sendFailure(Component.literal("No nearby The Lost Camera ruin signature found."));
            return 0;
        }

        if (!VirtualRuinRestorationManager.begin(level, ruin.anchorPos(), ruin.ruinId(), ruin.rotation())) {
            source.sendFailure(Component.literal("Unable to start restoration for " + ruin.ruinId() + " at " + format(ruin.anchorPos())));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "Started virtual restoration for " + ruin.ruinId() + " at " + format(ruin.anchorPos())
        ), true);
        return 1;
    }

    private static int validate(CommandSourceStack source) {
        int loaded = 0;
        for (RuinCatalog.Entry entry : RuinCatalog.entries()) {
            try {
                RestorationTemplate template = RestorationTemplateLoader.load(entry.id().toString());
                source.sendSuccess(() -> Component.literal(
                        entry.id() + " OK: ruinedBlocks=" + template.ruinedBlocks().size()
                                + ", restoredTargets=" + template.restoredTargets().size()
                                + ", restoredEntities=" + template.restoredEntities().size()
                ), false);
                loaded++;
            } catch (RuntimeException exception) {
                source.sendFailure(Component.literal(entry.id() + " FAILED: " + exception.getMessage()));
            }
        }
        int loadedCount = loaded;
        source.sendSuccess(() -> Component.literal(
                "Validated " + loadedCount + " / " + RuinCatalog.entries().size()
                        + " ruins. Active virtual restorations=" + VirtualRuinRestorationManager.activeCount()
        ), false);
        return loaded;
    }

    private static int clearAlbum(CommandSourceStack source, ServerPlayer target) {
        int cleared = PlayerAlbumStorage.clear(target);
        String targetName = target.getName().getString();
        if (cleared < 0) {
            source.sendFailure(Component.literal("Failed to clear The Lost Camera album for " + targetName + "."));
            return 0;
        }

        ServerPayloadHandlers.sendSnapshot(target);
        source.sendSuccess(() -> Component.literal(
                "Cleared " + cleared + " archived photos and reset album discovery progress for "
                        + targetName + "."
        ), true);
        return cleared;
    }

    private static boolean isAdmin(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayer();
            return player == null || source.getServer().getPlayerList().isOp(new NameAndId(player.getGameProfile()));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String format(net.minecraft.core.BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
