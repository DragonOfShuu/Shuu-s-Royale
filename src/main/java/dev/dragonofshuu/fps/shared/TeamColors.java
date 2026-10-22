package dev.dragonofshuu.fps.shared;

import net.minecraft.ChatFormatting;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;

import java.util.Locale;

/**
 * Shared team-color resolver for UI and equipment tinting.
 */
public final class TeamColors {

    private TeamColors() {
    }

    public static Integer getTeamColor(Team team) {
        if (team == null) {
            return null;
        }

        if (team instanceof PlayerTeam playerTeam) {
            ChatFormatting color = playerTeam.getColor();
            if (color != null && color.getColor() != null) {
                return color.getColor();
            }
        }

        return getTeamColor(team.getName());
    }

    public static Integer getTeamColor(String teamNameRaw) {
        if (teamNameRaw == null || teamNameRaw.isBlank()) {
            return null;
        }

        String teamName = teamNameRaw.toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');

        if (teamName.contains("white"))
            return 0xF9FFFE;
        if (teamName.contains("orange"))
            return 0xF9801D;
        if (teamName.contains("magenta"))
            return 0xC74EBD;
        if (teamName.contains("light_blue"))
            return 0x3AB3DA;
        if (teamName.contains("yellow"))
            return 0xFED83D;
        if (teamName.contains("lime"))
            return 0x80C71F;
        if (teamName.contains("pink"))
            return 0xF38BAA;
        if (teamName.contains("gray"))
            return 0x474F52;
        if (teamName.contains("light_gray") || teamName.contains("silver"))
            return 0x9D9D97;
        if (teamName.contains("cyan"))
            return 0x169C9C;
        if (teamName.contains("purple"))
            return 0x8932B8;
        if (teamName.contains("blue"))
            return 0x3C44AA;
        if (teamName.contains("brown"))
            return 0x835432;
        if (teamName.contains("green"))
            return 0x5E7C16;
        if (teamName.contains("red"))
            return 0xB02E26;
        if (teamName.contains("black"))
            return 0x1D1D21;

        return null;
    }

    public static int toArgb(int rgb, int alpha) {
        int clampedAlpha = Math.max(0, Math.min(255, alpha));
        return (clampedAlpha << 24) | (rgb & 0x00FFFFFF);
    }
}
