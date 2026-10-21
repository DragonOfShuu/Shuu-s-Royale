package dev.dragonofshuu.battleroyale;

import dev.dragonofshuu.battleroyale.arena.ArenaSnapshot;
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

    private ModEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BrCommands.register(event);
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
