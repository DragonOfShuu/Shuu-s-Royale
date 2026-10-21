package dev.dragonofshuu.battleroyale.modes.arenaTimed;

import dev.dragonofshuu.battleroyale.shared.Loadout;
import dev.dragonofshuu.battleroyale.shared.PlayerDrop;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.border.WorldBorder;

import java.util.Map;
import java.util.UUID;

final class ArenaTimedPlayers {

    private ArenaTimedPlayers() {
    }

    static void prepareParticipants(MinecraftServer server, ServerLevel level,
            double centerX, double centerZ, int borderSize,
            Map<UUID, Loadout.LoadoutClass> playerLoadouts) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || player.isCreative()) {
                continue;
            }

            Loadout.LoadoutClass loadoutClass = randomLoadoutClass();
            playerLoadouts.put(player.getUUID(), loadoutClass);

            clearForFreshLoadout(player);
            Loadout.giveLoadout(player, loadoutClass);
            PlayerDrop.drop(level, player, centerX, centerZ, borderSize);
        }
    }

    static void respawnToDrop(ServerPlayer player, Map<UUID, Loadout.LoadoutClass> playerLoadouts) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        ServerLevel level = server.overworld();
        WorldBorder border = level.getWorldBorder();
        clearForFreshLoadout(player);

        Loadout.LoadoutClass loadoutClass = playerLoadouts.computeIfAbsent(
                player.getUUID(), key -> randomLoadoutClass());
        Loadout.giveLoadout(player, loadoutClass);
        PlayerDrop.drop(level, player, border.getCenterX(), border.getCenterZ(), border.getSize());
    }

    static void clearForFreshLoadout(ServerPlayer player) {
        player.getInventory().clearContent();
        player.containerMenu.broadcastChanges();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().eat(20, 20.0F);
        player.clearFire();
        player.removeAllEffects();
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
    }

    private static Loadout.LoadoutClass randomLoadoutClass() {
        Loadout.LoadoutClass[] classes = Loadout.LoadoutClass.values();
        return classes[(int) (Math.random() * classes.length)];
    }
}
