package dev.dragonofshuu.battleroyale.commands;

import com.mojang.brigadier.context.CommandContext;
import dev.dragonofshuu.battleroyale.arena.ArenaSnapshot;
import dev.dragonofshuu.battleroyale.modes.arenaTimed.ArenaTimedGame;
import dev.dragonofshuu.battleroyale.modes.battleRoyale.BattleRoyaleGame;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;

public final class ShuufpsCommands {

    private ShuufpsCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("shuufps")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("backup")
                                .executes(ShuufpsCommands::backupArena))
                        .then(Commands.literal("reset")
                                .executes(ShuufpsCommands::resetArena)));
    }

    private static boolean requireOverworld(CommandSourceStack source) {
        if (source.getLevel() != source.getServer().overworld()) {
            source.sendFailure(Component.literal("This command must be run in the Overworld."));
            return false;
        }
        return true;
    }

    private static int backupArena(CommandContext<CommandSourceStack> ctx) {
        return backupArena(ctx.getSource());
    }

    public static int backupArena(CommandSourceStack source) {
        if (!requireOverworld(source)) {
            return 0;
        }
        if (!ArenaSnapshot.hasArenaConfig()) {
            source.sendFailure(
                    Component.literal("Arena center/size not set. Use /br set center <size> or /br init <size>."));
            return 0;
        }
        if (BattleRoyaleGame.isRunning() || ArenaTimedGame.isRunning()) {
            source.sendFailure(Component.literal("Stop the current game before making a backup."));
            return 0;
        }
        if (ArenaSnapshot.isBusy()) {
            source.sendFailure(Component.literal("A backup or restore is already in progress."));
            return 0;
        }

        ArenaSnapshot.beginCapture(source.getServer(), ArenaSnapshot.getCenterX(), ArenaSnapshot.getCenterZ(),
                ArenaSnapshot.getSize());
        return 1;
    }

    private static int resetArena(CommandContext<CommandSourceStack> ctx) {
        return resetArena(ctx.getSource());
    }

    public static int resetArena(CommandSourceStack source) {
        if (ArenaSnapshot.isBusy()) {
            source.sendFailure(Component.literal("A backup or restore is already in progress."));
            return 0;
        }
        if (!ArenaSnapshot.hasBackup()) {
            source.sendFailure(Component.literal("No arena backup exists. Use /shuufps backup first."));
            return 0;
        }

        if (BattleRoyaleGame.isActive()) {
            BattleRoyaleGame.stop(source.getServer());
        }
        if (ArenaTimedGame.isActive()) {
            ArenaTimedGame.stop(source.getServer());
        }

        ArenaSnapshot.beginRestore(source.getServer());
        return 1;
    }
}
