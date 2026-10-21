package dev.dragonofshuu.battleroyale.shared;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import dev.dragonofshuu.battleroyale.BattleRoyale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Shared client HUD rendering for match stats.
 */
@Mod.EventBusSubscriber(modid = BattleRoyale.MOD_ID, value = Dist.CLIENT)
public final class SharedHudOverlay {

    private static final int TAB_LEFT = 16;
    private static final int TAB_TOP = 20;
    private static final int TAB_WIDTH = 260;
    private static final int ROW_HEIGHT = 10;

    private SharedHudOverlay() {
    }

    @SubscribeEvent
    public static void onRenderHealthOverlay(RenderGuiOverlayEvent.Post event) {
        if (!"minecraft:player_health".equals(event.getOverlay().id().toString())) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }

        MatchStatsSnapshot snapshot = SharedMatchStats.getClientSnapshot();
        if (snapshot.phase() != MatchPhase.RUNNING) {
            return;
        }

        PlayerMatchStats local = findLocal(snapshot);
        if (local == null) {
            return;
        }

        Font font = mc.font;
        GuiGraphics gui = event.getGuiGraphics();
        String text = "Kills: " + local.kills();
        // int x = 10;
        int x = event.getWindow().getGuiScaledWidth() / 2 - 91;
        int y = event.getWindow().getGuiScaledHeight() - 59;
        gui.drawString(font, text, x + 1, y + 1, 0x333333, false);
        gui.drawString(font, text, x, y, 0xFFFFFF, false);
    }

    @SubscribeEvent
    public static void onRenderTabLeaderboard(RenderGuiOverlayEvent.Pre event) {
        if (!"minecraft:player_list".equals(event.getOverlay().id().toString())) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !mc.options.keyPlayerList.isDown()) {
            return;
        }

        MatchStatsSnapshot snapshot = SharedMatchStats.getClientSnapshot();
        if (snapshot.phase() == MatchPhase.IDLE) {
            return;
        }

        event.setCanceled(true);

        GuiGraphics gui = event.getGuiGraphics();
        Font font = mc.font;

        List<String> lines = buildLeaderboardLines(snapshot);
        int height = 8 + lines.size() * ROW_HEIGHT;
        int right = TAB_LEFT + TAB_WIDTH;
        int bottom = TAB_TOP + height;

        gui.fill(TAB_LEFT - 2, TAB_TOP - 2, right + 2, bottom + 2, 0xAA000000);

        int y = TAB_TOP;
        for (String line : lines) {
            gui.drawString(font, line, TAB_LEFT, y, 0xFFFFFF, false);
            y += ROW_HEIGHT;
        }
    }

    private static PlayerMatchStats findLocal(MatchStatsSnapshot snapshot) {
        if (snapshot.localPlayerId() == null) {
            return null;
        }
        for (PlayerMatchStats row : snapshot.players()) {
            if (snapshot.localPlayerId().equals(row.playerId())) {
                return row;
            }
        }
        return null;
    }

    private static List<String> buildLeaderboardLines(MatchStatsSnapshot snapshot) {
        List<String> lines = new ArrayList<>();
        lines.add(Component.literal("Leaderboard")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .getString());
        lines.add("Name                 K  D  L");

        Map<String, List<PlayerMatchStats>> grouped = new LinkedHashMap<>();
        for (PlayerMatchStats player : snapshot.players()) {
            String teamKey = player.teamName().isBlank() ? "Outstanding Players" : "Team: " + player.teamName();
            grouped.computeIfAbsent(teamKey, key -> new ArrayList<>()).add(player);
        }

        Comparator<PlayerMatchStats> sorter = Comparator
                .comparingInt(PlayerMatchStats::kills).reversed()
                .thenComparingInt(PlayerMatchStats::deaths)
                .thenComparing(PlayerMatchStats::playerName, String.CASE_INSENSITIVE_ORDER);

        for (Map.Entry<String, List<PlayerMatchStats>> entry : grouped.entrySet()) {
            entry.getValue().sort(sorter);

            lines.add(entry.getKey());
            for (PlayerMatchStats player : entry.getValue()) {
                String name = trimToWidth(player.playerName(), 18);
                lines.add(String.format("%-18s %2d %2d %2d", name,
                        player.kills(), player.deaths(), player.livesLeft()));
            }
        }

        return lines;
    }

    private static String trimToWidth(String value, int maxChars) {
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(0, maxChars - 1)) + "~";
    }

    public void drawContentBox(GuiGraphics graphics, Font font, int startX, int startY, int boxWidth, int boxHeight,
            ResourceLocation textureLocation, String textContent, OptionalInt backgroundColor) {

        // 1. Draw the background panel (semi-transparent dark background)
        // Colors use the ARGB format: Alpha, Red, Green, Blue (e.g., 0xCC151515 for
        // translucent dark grey)
        graphics.fill(startX, startY, startX + boxWidth, startY + boxHeight, backgroundColor.orElse(0x00000000));

        // Optional: Draw a thin border outline around your box
        // graphics.renderOutline(startX, startY, boxWidth, boxHeight, 0xFF555555);

        // 2. Calculate coordinates and render the image/icon on the left
        int padding = 6;
        int imageSize = boxHeight - (padding * 2); // Keeps the image neatly bounded inside the box height
        int imageX = startX + padding;
        int imageY = startY + padding;

        // Note on version differences:
        // - For Minecraft 1.21.2+: graphics.blit(RenderType::guiTextured,
        // textureLocation, imageX, imageY, 0.0f, 0.0f, imageSize, imageSize, imageSize,
        // imageSize);
        // - For Minecraft 1.20 – 1.21.1: graphics.blit(textureLocation, imageX, imageY,
        // 0, 0, imageSize, imageSize, imageSize, imageSize);
        graphics.blit(
                textureLocation,
                imageX, imageY,
                0.0f, 0.0f,
                imageSize, imageSize,
                imageSize, imageSize);

        // 3. Draw the text on the right side of the image
        int textX = imageX + imageSize + padding;

        // Vertically center the text (Minecraft's standard font height is 8–9 pixels)
        int textY = startY + (boxHeight - 8) / 2;
        int textColor = 0xFFFFFFFF; // Pure white text (ARGB format)

        // Render the text with a drop shadow (true flag at the end)
        graphics.drawString(font, textContent, textX, textY, textColor, true);
    }
}
