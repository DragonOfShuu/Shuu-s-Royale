package dev.dragonofshuu.battleroyale;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BattleRoyale.MOD_ID)
public final class ModEvents {

    private static final int MAX_BACKUP_SIZE = 4096;

    private ModEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("br")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("start")
                    .executes(ModEvents::startGame))
                        .then(Commands.literal("stop")
                                .executes(ModEvents::stopGame))
                        .then(Commands.literal("backup")
                    .executes(ModEvents::backupArena))
                .then(Commands.literal("settings")
                    .then(Commands.literal("warTime")
                        .then(Commands.argument("value", IntegerArgumentType.integer(1))
                            .executes(ModEvents::setWarTime)))
                    .then(Commands.literal("shrinkAmount")
                        .then(Commands.argument("value", IntegerArgumentType.integer(1))
                            .executes(ModEvents::setShrinkAmount)))
                    .then(Commands.literal("shrinkTime")
                        .then(Commands.argument("value", IntegerArgumentType.integer(1))
                            .executes(ModEvents::setShrinkTime)))
                    .then(Commands.literal("lives")
                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                            .executes(ModEvents::setLives))))
                .then(Commands.literal("set")
                    .then(Commands.literal("center")
                        .then(Commands.argument("size", IntegerArgumentType.integer(11, MAX_BACKUP_SIZE))
                            .executes(ModEvents::setCenter))))
                .then(Commands.literal("init")
                    .then(Commands.argument("size", IntegerArgumentType.integer(11, MAX_BACKUP_SIZE))
                        .executes(ModEvents::initArena)
                        .then(Commands.argument("warTime", IntegerArgumentType.integer(1))
                            .then(Commands.argument("shrinkAmount", IntegerArgumentType.integer(1))
                                .then(Commands.argument("shrinkTime", IntegerArgumentType.integer(1))
                                    .executes(ModEvents::initArenaWithSettings)
                                    .then(Commands.argument("lives", IntegerArgumentType.integer(0))
                                        .executes(ModEvents::initArenaWithSettings)))))))
                        .then(Commands.literal("reset")
                                .executes(ModEvents::resetArena)));
    }

    private static boolean requireOverworld(CommandSourceStack source) {
        if (source.getLevel() != source.getServer().overworld()) {
            source.sendFailure(Component.literal("This command must be run in the Overworld."));
            return false;
        }
        return true;
    }

    private static int startGame(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!requireOverworld(source))
            return 0;

        if (!ArenaSnapshot.hasArenaConfig()) {
            source.sendFailure(Component.literal("Arena center/size not set. Use /br set center <size> or /br init <size>."));
            return 0;
        }
        if (!ArenaSnapshot.hasGameSettings()) {
            source.sendFailure(Component.literal(
                    "Game settings not set. Use /br settings <warTime|shrinkAmount|shrinkTime|lives> <value> or /br init <size> <warTime> <shrinkAmount> <shrinkTime> [lives]."));
            return 0;
        }

        if (BattleRoyaleGame.isRunning()) {
            source.sendFailure(Component.literal("A game is already running."));
            return 0;
        }
        if (ArenaSnapshot.isBusy()) {
            source.sendFailure(Component.literal("Wait for the current backup or restore to finish."));
            return 0;
        }

        BattleRoyaleGame.start(
                source.getServer(),
                ArenaSnapshot.getCenterX(), ArenaSnapshot.getCenterZ(),
                ArenaSnapshot.getSize(),
            ArenaSnapshot.getWarTime(),
            ArenaSnapshot.getShrinkAmount(),
            ArenaSnapshot.getShrinkTime(),
            ArenaSnapshot.getLives());
        return 1;
    }

    private static int getLivesArg(CommandContext<CommandSourceStack> ctx) {
        try {
            return IntegerArgumentType.getInteger(ctx, "lives");
        } catch (IllegalArgumentException ignored) {
            return 5;
        }
    }

    private static int setWarTime(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ArenaSnapshot.setWarTime(source.getServer(), IntegerArgumentType.getInteger(ctx, "value"));
        source.sendSuccess(() -> Component.literal("War time set."), true);
        return 1;
    }

    private static int setShrinkAmount(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ArenaSnapshot.setShrinkAmount(source.getServer(), IntegerArgumentType.getInteger(ctx, "value"));
        source.sendSuccess(() -> Component.literal("Shrink amount set."), true);
        return 1;
    }

    private static int setShrinkTime(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ArenaSnapshot.setShrinkTime(source.getServer(), IntegerArgumentType.getInteger(ctx, "value"));
        source.sendSuccess(() -> Component.literal("Shrink time set."), true);
        return 1;
    }

    private static int setLives(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ArenaSnapshot.setLives(source.getServer(), IntegerArgumentType.getInteger(ctx, "value"));
        source.sendSuccess(() -> Component.literal("Lives set."), true);
        return 1;
    }

    private static int stopGame(CommandContext<CommandSourceStack> ctx) {
        if (!BattleRoyaleGame.isActive()) {
            ctx.getSource().sendFailure(Component.literal("No game is running."));
            return 0;
        }
        BattleRoyaleGame.stop(ctx.getSource().getServer());
        return 1;
    }

    private static int backupArena(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!requireOverworld(source))
            return 0;

        if (!ArenaSnapshot.hasArenaConfig()) {
            source.sendFailure(Component.literal("Arena center/size not set. Use /br set center <size> or /br init <size>."));
            return 0;
        }

        if (BattleRoyaleGame.isRunning()) {
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

    private static int setCenter(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!requireOverworld(source))
            return 0;

        if (ArenaSnapshot.isBusy()) {
            source.sendFailure(Component.literal("A backup or restore is already in progress."));
            return 0;
        }

        int size = IntegerArgumentType.getInteger(ctx, "size");
        var pos = source.getPosition();
        ArenaSnapshot.setArenaConfig(source.getServer(), pos.x, pos.z, size);
        source.sendSuccess(() -> Component.literal("Arena center set to "
                + String.format("%.1f, %.1f", pos.x, pos.z) + " with size " + size + "."), true);
        return 1;
    }

    private static int initArena(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!requireOverworld(source))
            return 0;

        if (BattleRoyaleGame.isRunning()) {
            source.sendFailure(Component.literal("Stop the current game before initializing the arena."));
            return 0;
        }
        if (ArenaSnapshot.isBusy()) {
            source.sendFailure(Component.literal("A backup or restore is already in progress."));
            return 0;
        }

        int size = IntegerArgumentType.getInteger(ctx, "size");
        var pos = source.getPosition();
        ArenaSnapshot.setArenaConfig(source.getServer(), pos.x, pos.z, size);
        ArenaSnapshot.beginCapture(source.getServer(), pos.x, pos.z, size);
        source.sendSuccess(() -> Component.literal("Arena center set and backup initialization started."), true);
        return 1;
    }

    private static int initArenaWithSettings(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!requireOverworld(source))
            return 0;

        if (BattleRoyaleGame.isRunning()) {
            source.sendFailure(Component.literal("Stop the current game before initializing the arena."));
            return 0;
        }
        if (ArenaSnapshot.isBusy()) {
            source.sendFailure(Component.literal("A backup or restore is already in progress."));
            return 0;
        }

        int size = IntegerArgumentType.getInteger(ctx, "size");
        int warTime = IntegerArgumentType.getInteger(ctx, "warTime");
        int shrinkAmount = IntegerArgumentType.getInteger(ctx, "shrinkAmount");
        int shrinkTime = IntegerArgumentType.getInteger(ctx, "shrinkTime");
        int lives = getLivesArg(ctx);

        var pos = source.getPosition();
        ArenaSnapshot.setArenaConfig(source.getServer(), pos.x, pos.z, size);
        ArenaSnapshot.setGameSettings(source.getServer(), warTime, shrinkAmount, shrinkTime, lives);
        ArenaSnapshot.beginCapture(source.getServer(), pos.x, pos.z, size);

        source.sendSuccess(() -> Component.literal("Arena center, settings, and backup initialization started."), true);
        return 1;
    }

    private static int resetArena(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        if (ArenaSnapshot.isBusy()) {
            source.sendFailure(Component.literal("A backup or restore is already in progress."));
            return 0;
        }
        if (!ArenaSnapshot.hasBackup()) {
            source.sendFailure(Component.literal("No arena backup exists. Use /br backup first."));
            return 0;
        }

        if (BattleRoyaleGame.isActive()) {
            BattleRoyaleGame.stop(source.getServer());
        }
        ArenaSnapshot.beginRestore(source.getServer());
        return 1;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            MinecraftServer server = event.getServer();
            ArenaSnapshot.tick(server);
            BattleRoyaleGame.tick(server);
            PlayerDrop.tick(server);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ArenaSnapshot.loadFromDisk(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // Don't let the server shut down halfway through writing a backup
        ArenaSnapshot.awaitPendingIo();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        // Static state survives between singleplayer worlds in the same session, so
        // clear it.
        BattleRoyaleGame.reset();
        ArenaSnapshot.clear();
        PlayerDrop.clear();
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerDrop.onLogin(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BattleRoyaleGame.onPlayerLeave(player);
            PlayerDrop.onLogout(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BattleRoyaleGame.onPlayerRespawn(player);
            PlayerDrop.onRespawn(player);
        }
    }

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && PlayerDrop.isDropping(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && PlayerDrop.isDropping(player)
                && (event.getSource().is(DamageTypes.FALL) || event.getSource().is(DamageTypes.FLY_INTO_WALL))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BattleRoyaleGame.onPlayerDeath(player);
            PlayerDrop.onDeath(player);
        }
    }
}
