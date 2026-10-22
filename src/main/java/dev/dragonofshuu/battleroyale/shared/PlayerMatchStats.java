package dev.dragonofshuu.battleroyale.shared;

import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerTeam;

import java.util.UUID;

/**
 * Shared per-player match stats used by any game mode.
 */
public record PlayerMatchStats(
                UUID playerId,
                String playerName,
                PlayerTeam team,
                String teamName,
                Integer teamColor,
                int kills,
                int deaths,
                int livesLeft) {

        public Component teamDisplayName() {
                if (team != null) {
                        return team.getDisplayName();
                }
                return teamName == null || teamName.isBlank() ? Component.literal("") : Component.literal(teamName);
        }
}
