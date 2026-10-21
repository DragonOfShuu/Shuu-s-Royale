package dev.dragonofshuu.battleroyale.shared;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Team;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared match stats cache. Server updates this, client reads from synced
 * snapshot.
 */
public final class SharedMatchStats {

    private static MatchPhase phase = MatchPhase.IDLE;

    private static final Map<UUID, Integer> KILLS = new HashMap<>();
    private static final Map<UUID, Integer> DEATHS = new HashMap<>();
    private static final Map<UUID, Integer> LIVES = new HashMap<>();

    private static volatile MatchStatsSnapshot clientSnapshot = MatchStatsSnapshot.empty();

    private SharedMatchStats() {
    }

    public static void startMatch(MinecraftServer server, List<ServerPlayer> participants, int startingLives) {
        phase = MatchPhase.RUNNING;
        KILLS.clear();
        DEATHS.clear();
        LIVES.clear();

        for (ServerPlayer player : participants) {
            UUID id = player.getUUID();
            KILLS.put(id, 0);
            DEATHS.put(id, 0);
            LIVES.put(id, startingLives);
        }

        syncAll(server);
    }

    public static void setPhase(MinecraftServer server, MatchPhase newPhase) {
        phase = newPhase;
        syncAll(server);
    }

    public static void clear(MinecraftServer server) {
        phase = MatchPhase.IDLE;
        KILLS.clear();
        DEATHS.clear();
        LIVES.clear();
        syncAll(server);
    }

    public static void ensurePlayerTracked(ServerPlayer player, int defaultLives) {
        UUID id = player.getUUID();
        KILLS.putIfAbsent(id, 0);
        DEATHS.putIfAbsent(id, 0);
        LIVES.putIfAbsent(id, defaultLives);
    }

    public static void setLives(ServerPlayer player, int livesLeft) {
        LIVES.put(player.getUUID(), Math.max(0, livesLeft));
    }

    public static void recordDeath(ServerPlayer victim) {
        UUID victimId = victim.getUUID();
        DEATHS.merge(victimId, 1, Integer::sum);
    }

    public static void recordKillIfPlayer(ServerPlayer killer) {
        KILLS.merge(killer.getUUID(), 1, Integer::sum);
    }

    public static int getKills(UUID playerId) {
        return KILLS.getOrDefault(playerId, 0);
    }

    public static void syncAll(MinecraftServer server) {
        MatchStatsSnapshot snapshot = buildServerSnapshot(server, null);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            SharedUiNetworking.sendStatsSnapshot(player, withLocalPlayer(snapshot, player.getUUID()));
        }
    }

    public static void syncOne(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        MatchStatsSnapshot snapshot = buildServerSnapshot(server, player.getUUID());
        SharedUiNetworking.sendStatsSnapshot(player, snapshot);
    }

    private static MatchStatsSnapshot buildServerSnapshot(MinecraftServer server, UUID localPlayerId) {
        List<PlayerMatchStats> rows = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            Team team = player.getTeam();
            rows.add(new PlayerMatchStats(
                    id,
                    player.getGameProfile().getName(),
                    team == null ? "" : team.getName(),
                    KILLS.getOrDefault(id, 0),
                    DEATHS.getOrDefault(id, 0),
                    LIVES.getOrDefault(id, 0)));
        }

        rows.sort(Comparator
                .comparing(PlayerMatchStats::teamName)
                .thenComparing(Comparator.comparingInt(PlayerMatchStats::kills).reversed())
                .thenComparing(Comparator.comparingInt(PlayerMatchStats::deaths))
                .thenComparing(PlayerMatchStats::playerName, String.CASE_INSENSITIVE_ORDER));

        return new MatchStatsSnapshot(phase, localPlayerId, List.copyOf(rows));
    }

    private static MatchStatsSnapshot withLocalPlayer(MatchStatsSnapshot source, UUID localPlayerId) {
        return new MatchStatsSnapshot(source.phase(), localPlayerId, source.players());
    }

    public static void setClientSnapshot(MatchStatsSnapshot snapshot) {
        clientSnapshot = snapshot;
    }

    public static MatchStatsSnapshot getClientSnapshot() {
        return clientSnapshot;
    }

    public static void clearClientSnapshot() {
        clientSnapshot = MatchStatsSnapshot.empty();
    }
}
