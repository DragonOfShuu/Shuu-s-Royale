package dev.dragonofshuu.battleroyale.modes.arenaTimed;

import dev.dragonofshuu.battleroyale.arena.ArenaSafety;
import dev.dragonofshuu.battleroyale.arena.ArenaSnapshot;
import dev.dragonofshuu.battleroyale.shared.Loadout;
import dev.dragonofshuu.battleroyale.shared.MatchPhase;
import dev.dragonofshuu.battleroyale.shared.PlayerMatchStats;
import dev.dragonofshuu.battleroyale.shared.SharedMatchStats;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.border.WorldBorder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ArenaTimedGame {

    private static final int TICKS_PER_SECOND = 20;
    private static final int POST_TITLE_TICKS = 3 * TICKS_PER_SECOND;
    private static final int POST_RESULTS_TICKS = 6 * TICKS_PER_SECOND;

    private enum Phase {
        IDLE,
        RUNNING,
        RESULTS_DELAY,
        RESULTS_SHOWN,
        ENDING
    }

    private static Phase phase = Phase.IDLE;
    private static int timerTicks;
    private static int phaseTicks;

    private static final Map<UUID, Loadout.LoadoutClass> PLAYER_LOADOUTS = new HashMap<>();

    private ArenaTimedGame() {
    }

    public static boolean isRunning() {
        return phase == Phase.RUNNING;
    }

    public static boolean isActive() {
        return phase != Phase.IDLE;
    }

    public static void start(MinecraftServer server, double centerX, double centerZ,
            int borderSize, int durationSeconds) {
        ServerLevel level = server.overworld();
        WorldBorder border = level.getWorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(borderSize);
        border.setWarningBlocks(5);

        PLAYER_LOADOUTS.clear();

        timerTicks = durationSeconds * TICKS_PER_SECOND;
        phaseTicks = 0;
        phase = Phase.RUNNING;
        ArenaSafety.setAllPlayersSurvival(server);

        ArenaTimedPlayers.prepareParticipants(server, level, centerX, centerZ, borderSize, PLAYER_LOADOUTS);

        SharedMatchStats.startMatch(server, "arena_timed", eligiblePlayers(server), -1);
        SharedMatchStats.setTimerSeconds(server, durationSeconds);

        ArenaTimedAnnouncements.broadcast(server,
                "Arena Timed started. Border: " + borderSize + " blocks. Duration: " + durationSeconds + "s.");
    }

    public static void stop(MinecraftServer server) {
        ArenaSafety.clearGroundItems(server);
        ArenaSafety.clearPlayerInventories(server);
        ArenaSafety.setAllPlayersAdventure(server);
        resetBorder(server);
        SharedMatchStats.clear(server);
        reset();
        ArenaTimedAnnouncements.broadcast(server, "Arena Timed stopped.");
    }

    public static void reset() {
        phase = Phase.IDLE;
        timerTicks = 0;
        phaseTicks = 0;
        PLAYER_LOADOUTS.clear();
    }

    public static void resetBorder(MinecraftServer server) {
        WorldBorder border = server.overworld().getWorldBorder();
        border.setSize(border.getAbsoluteMaxSize());
        border.setCenter(0, 0);
    }

    public static void onPlayerDeath(ServerPlayer player) {
        if (!isActive()) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server != null) {
            SharedMatchStats.syncAll(server);
        }
    }

    public static void onPlayerRespawn(ServerPlayer player) {
        if (!isActive() || phase == Phase.ENDING) {
            return;
        }
        ArenaTimedPlayers.respawnToDrop(player, PLAYER_LOADOUTS);
    }

    public static void tick(MinecraftServer server) {
        if (phase == Phase.IDLE) {
            return;
        }

        if (phase == Phase.RUNNING) {
            if (--timerTicks <= 0) {
                finishByTimer(server);
                return;
            }

            if (server.getTickCount() % TICKS_PER_SECOND == 0) {
                SharedMatchStats.setTimerSeconds(server, timerTicks / TICKS_PER_SECOND);
            }
            return;
        }

        if (phase == Phase.RESULTS_DELAY) {
            if (++phaseTicks >= POST_TITLE_TICKS) {
                phase = Phase.RESULTS_SHOWN;
                phaseTicks = 0;
                SharedMatchStats.setShowFinalResults(server, true);
            }
            return;
        }

        if (phase == Phase.RESULTS_SHOWN) {
            if (++phaseTicks >= POST_RESULTS_TICKS) {
                phase = Phase.ENDING;
                resetArenaAndGame(server);
            }
        }
    }

    private static void finishByTimer(MinecraftServer server) {
        Set<UUID> winners = determineWinners(server);
        ArenaTimedAnnouncements.announceWinnersAndLosers(server, winners);

        phase = Phase.RESULTS_DELAY;
        phaseTicks = 0;

        SharedMatchStats.setTimerSeconds(server, 0);
        SharedMatchStats.setPhase(server, MatchPhase.POST_GAME);
        SharedMatchStats.setShowFinalResults(server, false);
    }

    private static Set<UUID> determineWinners(MinecraftServer server) {
        List<PlayerMatchStats> rows = SharedMatchStats.getServerRows(server);
        Map<String, Integer> teamKills = new HashMap<>();
        int topSoloKills = Integer.MIN_VALUE;
        String topTeam = null;

        for (PlayerMatchStats row : rows) {
            if (row.teamName().isBlank()) {
                topSoloKills = Math.max(topSoloKills, row.kills());
            } else {
                int total = teamKills.getOrDefault(row.teamName(), 0) + row.kills();
                teamKills.put(row.teamName(), total);
                if (topTeam == null || total > teamKills.getOrDefault(topTeam, Integer.MIN_VALUE)) {
                    topTeam = row.teamName();
                }
            }
        }

        int topTeamKills = topTeam == null ? Integer.MIN_VALUE : teamKills.getOrDefault(topTeam, Integer.MIN_VALUE);
        int bestScore = Math.max(topSoloKills, topTeamKills);

        Set<UUID> winners = new HashSet<>();
        for (PlayerMatchStats row : rows) {
            if (row.teamName().isBlank()) {
                if (row.kills() == bestScore) {
                    winners.add(row.playerId());
                }
            } else if (teamKills.getOrDefault(row.teamName(), Integer.MIN_VALUE) == bestScore) {
                winners.add(row.playerId());
            }
        }

        return winners;
    }

    private static List<ServerPlayer> eligiblePlayers(MinecraftServer server) {
        return server.getPlayerList().getPlayers().stream()
                .filter(player -> !player.isSpectator() && !player.isCreative())
                .toList();
    }

    private static void resetArenaAndGame(MinecraftServer server) {
        if (ArenaSnapshot.isBusy()) {
            return;
        }

        ArenaSafety.clearGroundItems(server);
        ArenaSafety.clearPlayerInventories(server);
        ArenaSafety.setAllPlayersAdventure(server);

        if (isActive()) {
            stop(server);
        }

        if (!ArenaSnapshot.hasBackup()) {
            ArenaTimedAnnouncements.broadcast(server, "No arena backup exists to restore.");
            return;
        }

        ArenaSnapshot.beginRestore(server);
    }
}
