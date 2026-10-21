package dev.dragonofshuu.battleroyale.shared;

import java.util.UUID;

/**
 * Shared per-player match stats used by any game mode.
 */
public record PlayerMatchStats(
        UUID playerId,
        String playerName,
        String teamName,
        int kills,
        int deaths,
        int livesLeft) {
}
