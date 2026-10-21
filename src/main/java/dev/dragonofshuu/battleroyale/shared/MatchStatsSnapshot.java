package dev.dragonofshuu.battleroyale.shared;

import java.util.List;
import java.util.UUID;

/**
 * Immutable snapshot synced to clients for shared HUD/leaderboard UI.
 */
public record MatchStatsSnapshot(
        MatchPhase phase,
        String modeId,
        int timerSeconds,
        boolean showFinalResults,
        UUID localPlayerId,
        List<PlayerMatchStats> players) {

    public static MatchStatsSnapshot empty() {
        return new MatchStatsSnapshot(MatchPhase.IDLE, "", -1, false, null, List.of());
    }
}
