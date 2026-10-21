package dev.dragonofshuu.battleroyale.shared;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class MatchLeaderboardPanelRenderer {

    private static final int PANEL_PADDING = 12;
    private static final int ROW_HEIGHT = 10;
    private static final int MIN_COLUMN_CHAR_WIDTH = 3;
    private static final int COLUMN_GAP = 12;

    private static final Comparator<PlayerMatchStats> PLAYER_SORTER = Comparator
            .comparingInt(PlayerMatchStats::kills).reversed()
            .thenComparingInt(PlayerMatchStats::deaths)
            .thenComparing(PlayerMatchStats::playerName, String.CASE_INSENSITIVE_ORDER);

    private static final Comparator<GroupEntry> GROUP_SORTER = Comparator
            .comparingInt(GroupEntry::totalKills).reversed()
            .thenComparing(GroupEntry::name, String.CASE_INSENSITIVE_ORDER);

    private MatchLeaderboardPanelRenderer() {
    }

    static PanelBounds centeredPanelBounds(int screenWidth, int screenHeight) {
        int panelWidth = Math.min(360, screenWidth - 40);
        int panelHeight = Math.min(220, screenHeight - 40);
        int panelX = (screenWidth - panelWidth) / 2;
        int panelY = (screenHeight - panelHeight) / 2;
        return new PanelBounds(panelX, panelY, panelWidth, panelHeight);
    }

    static void renderCenteredPanel(GuiGraphics guiGraphics, Font font,
            int screenWidth, int screenHeight,
            MatchStatsSnapshot snapshot, String title) {
        PanelBounds panel = centeredPanelBounds(screenWidth, screenHeight);
        ColumnLayout columns = columnLayout(font, panel);

        guiGraphics.fill(panel.x(), panel.y(), panel.x() + panel.width(), panel.y() + panel.height(), 0xCC111111);
        guiGraphics.fill(panel.x(), panel.y(), panel.x() + panel.width(), panel.y() + 1, 0xFFFFFFFF);
        guiGraphics.fill(panel.x(), panel.y() + panel.height() - 1,
                panel.x() + panel.width(), panel.y() + panel.height(), 0xFFFFFFFF);
        guiGraphics.fill(panel.x(), panel.y(), panel.x() + 1, panel.y() + panel.height(), 0xFFFFFFFF);
        guiGraphics.fill(panel.x() + panel.width() - 1, panel.y(),
                panel.x() + panel.width(), panel.y() + panel.height(), 0xFFFFFFFF);

        String titleText = Component.literal(title)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .getString();
        guiGraphics.drawString(font, titleText, columns.nameX(), panel.y() + PANEL_PADDING, 0xFFFFFF, false);

        int headerY = panel.y() + PANEL_PADDING + 12;
        guiGraphics.drawString(font, "Name", columns.nameX(), headerY, 0xAAAAAA, false);
        drawRightAligned(guiGraphics, font, "K", columns.killsRight(), headerY, 0xAAAAAA);
        drawRightAligned(guiGraphics, font, "D", columns.deathsRight(), headerY, 0xAAAAAA);
        drawRightAligned(guiGraphics, font, "L", columns.livesRight(), headerY, 0xAAAAAA);

        int y = panel.y() + PANEL_PADDING + 24;
        int maxY = panel.y() + panel.height() - PANEL_PADDING;

        for (RenderLine line : buildLines(snapshot)) {
            if (y + ROW_HEIGHT > maxY) {
                guiGraphics.drawString(font, "...", columns.nameX(), y, 0xFFFFFF, false);
                break;
            }

            if (line.backgroundColor() != null) {
                guiGraphics.fill(panel.x() + PANEL_PADDING - 2, y - 1,
                        panel.x() + panel.width() - PANEL_PADDING + 2, y + ROW_HEIGHT - 1,
                        line.backgroundColor());
            }

            if (line.columns() == null) {
                guiGraphics.drawString(font, line.text(), columns.nameX() + line.indent(), y, line.color(), false);
            } else {
                int availableNameWidth = columns.killsLeft() - COLUMN_GAP - (columns.nameX() + line.indent());
                String name = trimToWidth(font, line.text(), availableNameWidth);
                guiGraphics.drawString(font, name, columns.nameX() + line.indent(), y, line.color(), false);
                drawRightAligned(guiGraphics, font, line.columns().kills(), columns.killsRight(), y, line.color());
                drawRightAligned(guiGraphics, font, line.columns().deaths(), columns.deathsRight(), y, line.color());
                drawRightAligned(guiGraphics, font, line.columns().lives(), columns.livesRight(), y, line.color());
            }

            y += ROW_HEIGHT;
        }
    }

    private static List<RenderLine> buildLines(MatchStatsSnapshot snapshot) {
        Map<String, List<PlayerMatchStats>> teams = new HashMap<>();
        List<PlayerMatchStats> soloPlayers = new ArrayList<>();

        for (PlayerMatchStats player : snapshot.players()) {
            if (player.teamName().isBlank()) {
                soloPlayers.add(player);
            } else {
                teams.computeIfAbsent(player.teamName(), key -> new ArrayList<>()).add(player);
            }
        }

        List<GroupEntry> groups = new ArrayList<>();
        for (Map.Entry<String, List<PlayerMatchStats>> entry : teams.entrySet()) {
            List<PlayerMatchStats> players = entry.getValue();
            players.sort(PLAYER_SORTER);
            int totalKills = players.stream().mapToInt(PlayerMatchStats::kills).sum();
            groups.add(GroupEntry.team(entry.getKey(), players, totalKills));
        }

        for (PlayerMatchStats solo : soloPlayers) {
            groups.add(GroupEntry.outstandingPlayer(solo));
        }

        groups.sort(GROUP_SORTER);

        List<RenderLine> lines = new ArrayList<>();
        for (GroupEntry group : groups) {
            if (group.isTeam()) {
                Integer rgb = TeamColors.getTeamColor(group.name());
                int color = rgb == null ? 0xFFE0E0E0 : TeamColors.toArgb(rgb, 255);
                Integer bg = rgb == null ? TeamColors.toArgb(0x3A3A3A, 120) : TeamColors.toArgb(rgb, 120);
                lines.add(new RenderLine(group.name() + " (" + group.totalKills() + " K)", color, 0, bg, null));
                for (PlayerMatchStats player : group.players()) {
                    lines.add(playerLine(player, 10));
                }
            } else {
                lines.add(playerLine(group.players().get(0), 0));
            }
        }

        return lines;
    }

    private static RenderLine playerLine(PlayerMatchStats player, int indent) {
        return new RenderLine(
                player.playerName(),
                0xFFFFFFFF,
                indent,
                null,
                new RenderColumns(
                        Integer.toString(player.kills()),
                        Integer.toString(player.deaths()),
                        formatLives(player.livesLeft())));
    }

    private static String formatLives(int lives) {
        return lives < 0 ? "INF" : Integer.toString(lives);
    }

    private static ColumnLayout columnLayout(Font font, PanelBounds panel) {
        int minColumnWidth = font.width("0".repeat(MIN_COLUMN_CHAR_WIDTH));
        int livesRight = panel.x() + panel.width() - PANEL_PADDING;
        int deathsRight = livesRight - minColumnWidth - COLUMN_GAP;
        int killsRight = deathsRight - minColumnWidth - COLUMN_GAP;
        int killsLeft = killsRight - minColumnWidth;
        int nameX = panel.x() + PANEL_PADDING;
        return new ColumnLayout(nameX, killsLeft, killsRight, deathsRight, livesRight);
    }

    private static void drawRightAligned(GuiGraphics guiGraphics, Font font, String value, int rightX, int y,
            int color) {
        guiGraphics.drawString(font, value, rightX - font.width(value), y, color, false);
    }

    private static String trimToWidth(Font font, String value, int maxWidth) {
        if (maxWidth <= 0 || font.width(value) <= maxWidth) {
            return value;
        }

        String ellipsis = "~";
        int ellipsisWidth = font.width(ellipsis);
        if (ellipsisWidth > maxWidth) {
            return "";
        }

        int cut = value.length();
        while (cut > 0 && font.width(value.substring(0, cut)) + ellipsisWidth > maxWidth) {
            cut--;
        }
        return value.substring(0, cut) + ellipsis;
    }

    record PanelBounds(int x, int y, int width, int height) {
    }

    private record ColumnLayout(int nameX, int killsLeft, int killsRight, int deathsRight, int livesRight) {
    }

    private record RenderColumns(String kills, String deaths, String lives) {
    }

    private record RenderLine(String text, int color, int indent, Integer backgroundColor, RenderColumns columns) {
    }

    private record GroupEntry(String name, List<PlayerMatchStats> players, int totalKills, boolean isTeam) {
        static GroupEntry team(String name, List<PlayerMatchStats> players, int totalKills) {
            return new GroupEntry(name, players, totalKills, true);
        }

        static GroupEntry outstandingPlayer(PlayerMatchStats player) {
            return new GroupEntry(player.playerName(), List.of(player), player.kills(), false);
        }
    }
}
