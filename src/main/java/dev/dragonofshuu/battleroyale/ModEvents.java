package dev.dragonofshuu.battleroyale;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.phys.Vec3;
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
                                .then(Commands.argument("borderSize", IntegerArgumentType.integer(11, 59_999_968))
                                        .then(Commands.argument("warTime", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("shrinkAmount", IntegerArgumentType.integer(1))
                                                        .then(Commands
                                                                .argument("shrinkTime", IntegerArgumentType.integer(1))
                                                                .executes(ModEvents::startGame))))))
                        .then(Commands.literal("stop")
                                .executes(ModEvents::stopGame))
                        .then(Commands.literal("backup")
                                .then(Commands.argument("size", IntegerArgumentType.integer(11, MAX_BACKUP_SIZE))
                                        .executes(ModEvents::backupArena)))
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

        if (BattleRoyaleGame.isRunning()) {
            source.sendFailure(Component.literal("A game is already running."));
            return 0;
        }
        if (ArenaSnapshot.isBusy()) {
            source.sendFailure(Component.literal("Wait for the current backup or restore to finish."));
            return 0;
        }

        Vec3 pos = source.getPosition();
        BattleRoyaleGame.start(
                source.getServer(),
                pos.x, pos.z,
                IntegerArgumentType.getInteger(ctx, "borderSize"),
                IntegerArgumentType.getInteger(ctx, "warTime"),
                IntegerArgumentType.getInteger(ctx, "shrinkAmount"),
                IntegerArgumentType.getInteger(ctx, "shrinkTime"));
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

        if (BattleRoyaleGame.isRunning()) {
            source.sendFailure(Component.literal("Stop the current game before making a backup."));
            return 0;
        }
        if (ArenaSnapshot.isBusy()) {
            source.sendFailure(Component.literal("A backup or restore is already in progress."));
            return 0;
        }

        Vec3 pos = source.getPosition();
        ArenaSnapshot.beginCapture(source.getServer(), pos.x, pos.z,
                IntegerArgumentType.getInteger(ctx, "size"));
        return 1;
    }

    private static int resetArena(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        if (ArenaSnapshot.isBusy()) {
            source.sendFailure(Component.literal("A backup or restore is already in progress."));
            return 0;
        }
        if (!ArenaSnapshot.hasBackup()) {
            source.sendFailure(Component.literal("No arena backup exists. Use /br backup <size> first."));
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
            PlayerDrop.onDeath(player);
        }
    }
}
