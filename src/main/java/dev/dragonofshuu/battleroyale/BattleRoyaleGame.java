package dev.dragonofshuu.battleroyale;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.border.WorldBorder;

public final class BattleRoyaleGame {

    private static final double MIN_SIZE = 10.0;
    private static final int TICKS_PER_SECOND = 20;
    private static final long MILLIS_PER_TICK = 50L;

    private enum Phase {
        IDLE, WAR, SHRINKING, FINISHED
    }

    private static final ServerBossEvent BAR = new ServerBossEvent(
            Component.literal("Battle Royale"),
            BossEvent.BossBarColor.YELLOW,
            BossEvent.BossBarOverlay.PROGRESS);

    private static Phase phase = Phase.IDLE;
    private static int warTicks;
    private static int shrinkTicks;
    private static int shrinkAmount;
    private static int timer;
    private static double targetSize;

    private BattleRoyaleGame() {
    }

    /** True while the border cycle is active (war or shrinking). */
    public static boolean isRunning() {
        return phase == Phase.WAR || phase == Phase.SHRINKING;
    }

    /**
     * True whenever a game exists, including after the border has finished
     * shrinking.
     */
    public static boolean isActive() {
        return phase != Phase.IDLE;
    }

    public static void start(MinecraftServer server, double centerX, double centerZ,
            int borderSize, int warTime, int shrinkAmount, int shrinkTime) {
        ServerLevel level = server.overworld();
        WorldBorder border = level.getWorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(borderSize);
        border.setWarningBlocks(5);

        BattleRoyaleGame.warTicks = warTime * TICKS_PER_SECOND;
        BattleRoyaleGame.shrinkTicks = shrinkTime * TICKS_PER_SECOND;
        BattleRoyaleGame.shrinkAmount = shrinkAmount;
        BattleRoyaleGame.targetSize = borderSize;

        phase = Phase.WAR;
        timer = warTicks;

        // Drop participants into the arena and hand out starting gear
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || player.isCreative())
                continue;
            PlayerDrop.drop(level, player, centerX, centerZ, borderSize);
            Loadout.giveLoadout(player);
        }

        updateBar();
        addAllPlayers(server);

        broadcast(server, "The battle has begun! Border: " + borderSize
                + " blocks. First shrink in " + warTime + " seconds.");
    }

    public static void stop(MinecraftServer server) {
        resetBorder(server);
        reset();
        broadcast(server, "The game has been stopped.");
    }

    /** Clears game state and hides the boss bar without touching the world. */
    public static void reset() {
        phase = Phase.IDLE;
        BAR.removeAllPlayers();
    }

    public static void resetBorder(MinecraftServer server) {
        WorldBorder border = server.overworld().getWorldBorder();
        border.setSize(border.getAbsoluteMaxSize());
        border.setCenter(0, 0);
    }

    public static void onPlayerLeave(ServerPlayer player) {
        BAR.removePlayer(player);
    }

    public static void tick(MinecraftServer server) {
        if (phase == Phase.IDLE)
            return;

        // Make sure players who joined mid-game can see the bar
        if (server.getTickCount() % TICKS_PER_SECOND == 0) {
            addAllPlayers(server);
        }

        if (phase == Phase.FINISHED)
            return;

        if (--timer <= 0) {
            WorldBorder border = server.overworld().getWorldBorder();
            if (phase == Phase.WAR) {
                beginShrink(server, border);
            } else {
                endShrink(server);
            }
        }

        updateBar();
    }

    private static void beginShrink(MinecraftServer server, WorldBorder border) {
        double current = targetSize;
        targetSize = Math.max(MIN_SIZE, current - shrinkAmount);

        border.lerpSizeBetween(current, targetSize, shrinkTicks * MILLIS_PER_TICK);

        phase = Phase.SHRINKING;
        timer = shrinkTicks;

        broadcast(server, "The border is shrinking to " + (int) targetSize + " blocks!");
    }

    private static void endShrink(MinecraftServer server) {
        if (targetSize <= MIN_SIZE) {
            phase = Phase.FINISHED;
            broadcast(server, "The border has reached its final size of " + (int) MIN_SIZE + " blocks!");
            return;
        }

        phase = Phase.WAR;
        timer = warTicks;
        broadcast(server, "The border has stopped. Next shrink in "
                + (warTicks / TICKS_PER_SECOND) + " seconds.");
    }

    private static void updateBar() {
        switch (phase) {
            case WAR -> {
                BAR.setColor(BossEvent.BossBarColor.YELLOW);
                BAR.setName(Component.literal("Border shrinks in " + formatTime(timer)));
                BAR.setProgress(Mth.clamp((float) timer / warTicks, 0.0F, 1.0F));
            }
            case SHRINKING -> {
                BAR.setColor(BossEvent.BossBarColor.RED);
                BAR.setName(Component.literal("Border shrinking to " + (int) targetSize
                        + " blocks: " + formatTime(timer)));
                BAR.setProgress(Mth.clamp((float) timer / shrinkTicks, 0.0F, 1.0F));
            }
            case FINISHED -> {
                BAR.setColor(BossEvent.BossBarColor.PURPLE);
                BAR.setName(Component.literal("Final border: " + (int) MIN_SIZE + " blocks"));
                BAR.setProgress(1.0F);
            }
            default -> {
            }
        }
    }

    private static void addAllPlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            BAR.addPlayer(player);
        }
    }

    private static String formatTime(int ticks) {
        int seconds = (ticks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    public static void broadcast(MinecraftServer server, String message) {
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("[BR] ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(message).withStyle(ChatFormatting.WHITE)),
                false);
    }
}
