package dev.dragonofshuu.battleroyale.shared;

import dev.dragonofshuu.battleroyale.BattleRoyale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Shared client HUD rendering for match stats.
 */
@Mod.EventBusSubscriber(modid = BattleRoyale.MOD_ID, value = Dist.CLIENT)
public final class SharedHudOverlay {

    private static boolean resultsScreenOpened;

    private SharedHudOverlay() {
    }

    @SubscribeEvent
    public static void onRenderGlobalOverlay(RenderGuiOverlayEvent.Post event) {
        if (!"minecraft:crosshair".equals(event.getOverlay().id().toString())) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }

        MatchStatsSnapshot snapshot = SharedMatchStats.getClientSnapshot();
        handleFinalResultsPopup(mc, snapshot);
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
        int x = event.getWindow().getGuiScaledWidth() / 2 - 91;
        int y = event.getWindow().getGuiScaledHeight() - 59;
        gui.drawString(font, text, x + 1, y + 1, 0x333333, false);
        gui.drawString(font, text, x, y, 0xFFFFFF, false);

        if (snapshot.timerSeconds() >= 0) {
            String timer = formatTimer(snapshot.timerSeconds());
            int width = font.width(timer);
            int timerX = (event.getWindow().getGuiScaledWidth() - width) / 2;
            int timerY = 8;
            gui.drawString(font, timer, timerX + 1, timerY + 1, 0x333333, false);
            gui.drawString(font, timer, timerX, timerY, 0xFFFFFF, false);
        }

        handleFinalResultsPopup(mc, snapshot);
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

        MatchLeaderboardPanelRenderer.renderCenteredPanel(
                gui,
                font,
                event.getWindow().getGuiScaledWidth(),
                event.getWindow().getGuiScaledHeight(),
                snapshot,
                "Match Results");

        handleFinalResultsPopup(mc, snapshot);
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

    private static String formatTimer(int totalSeconds) {
        int safe = Math.max(0, totalSeconds);
        int minutes = safe / 60;
        int seconds = safe % 60;
        return String.format("Time Left: %d:%02d", minutes, seconds);
    }

    private static void handleFinalResultsPopup(Minecraft mc, MatchStatsSnapshot snapshot) {
        boolean shouldShow = snapshot.showFinalResults() && "arena_timed".equals(snapshot.modeId())
                && snapshot.phase() == MatchPhase.POST_GAME;

        if (!shouldShow) {
            resultsScreenOpened = false;
            return;
        }

        if (!resultsScreenOpened) {
            mc.setScreen(new MatchResultsScreen());
            resultsScreenOpened = true;
        }
    }
}
