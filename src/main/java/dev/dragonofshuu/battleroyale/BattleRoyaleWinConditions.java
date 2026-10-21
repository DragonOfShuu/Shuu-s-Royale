package dev.dragonofshuu.battleroyale;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Team;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

final class BattleRoyaleWinConditions {

    enum OutcomeType {
        NONE,
        NICE_DEATH,
        TEAM_WIN,
        PLAYER_WIN
    }

    record Outcome(OutcomeType type, Team team, List<ServerPlayer> contenders, ServerPlayer winner) {
        static Outcome none() {
            return new Outcome(OutcomeType.NONE, null, List.of(), null);
        }

        static Outcome niceDeath() {
            return new Outcome(OutcomeType.NICE_DEATH, null, List.of(), null);
        }

        static Outcome teamWin(Team team, List<ServerPlayer> contenders) {
            return new Outcome(OutcomeType.TEAM_WIN, team, contenders, null);
        }

        static Outcome playerWin(ServerPlayer winner) {
            return new Outcome(OutcomeType.PLAYER_WIN, null, List.of(winner), winner);
        }
    }

    private BattleRoyaleWinConditions() {
    }

    static boolean hasWinningConditionPossible(MinecraftServer server, Map<UUID, Integer> livesLeft) {
        List<ServerPlayer> contenders = BattleRoyalePlayers.getContenders(server, livesLeft);
        if (contenders.size() <= 1) {
            return false;
        }

        Set<Team> teams = contenders.stream()
                .map(ServerPlayer::getTeam)
                .collect(Collectors.toSet());

        boolean allOnTeams = !teams.contains(null);
        return !allOnTeams || teams.size() > 1;
    }

    static Outcome evaluate(MinecraftServer server, Map<UUID, Integer> livesLeft, boolean noWinningConditionAtStart) {
        List<ServerPlayer> contenders = BattleRoyalePlayers.getContenders(server, livesLeft);

        if (noWinningConditionAtStart) {
            return contenders.isEmpty() ? Outcome.niceDeath() : Outcome.none();
        }

        if (contenders.isEmpty()) {
            return Outcome.none();
        }

        Set<Team> teams = contenders.stream()
                .map(ServerPlayer::getTeam)
                .collect(Collectors.toSet());
        boolean allOnTeams = !teams.contains(null);

        if (allOnTeams && teams.size() == 1) {
            return Outcome.teamWin(teams.iterator().next(), contenders);
        }

        if (contenders.size() == 1) {
            return Outcome.playerWin(contenders.get(0));
        }

        return Outcome.none();
    }
}
