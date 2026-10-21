package dev.dragonofshuu.battleroyale;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.scores.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class BattleRoyaleGame {

    private static final double MIN_SIZE = 10.0;
    private static final int TICKS_PER_SECOND = 20;
    private static final long MILLIS_PER_TICK = 50L;

    private enum Phase {
        IDLE, WAR, SHRINKING, FINISHED, ENDING
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
    private static int postWinResetTicks;
    private static boolean noWinningConditionAtStart;

    private static final String ELIMINATED_TAG = "br_eliminated";
    private static final Map<UUID, Integer> LIVES_LEFT = new HashMap<>();
    private static final Map<UUID, Loadout.LoadoutClass> PLAYER_LOADOUTS = new HashMap<>();

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
            int borderSize, int warTime, int shrinkAmount, int shrinkTime, int lives) {
        ServerLevel level = server.overworld();
        WorldBorder border = level.getWorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(borderSize);
        border.setWarningBlocks(5);

        LIVES_LEFT.clear();
        PLAYER_LOADOUTS.clear();

        BattleRoyaleGame.warTicks = warTime * TICKS_PER_SECOND;
        BattleRoyaleGame.shrinkTicks = shrinkTime * TICKS_PER_SECOND;
        BattleRoyaleGame.shrinkAmount = shrinkAmount;
        BattleRoyaleGame.targetSize = borderSize;

        phase = Phase.WAR;
        timer = warTicks;
        postWinResetTicks = 0;
        noWinningConditionAtStart = false;

        // Drop participants into the arena and hand out starting gear
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || player.isCreative())
                continue;
            player.removeTag(ELIMINATED_TAG);
            LIVES_LEFT.put(player.getUUID(), lives);
            Loadout.LoadoutClass loadoutClass = randomLoadoutClass();
            PLAYER_LOADOUTS.put(player.getUUID(), loadoutClass);

            clearForFreshLoadout(player);
            Loadout.giveLoadout(player, loadoutClass);
            PlayerDrop.drop(level, player, centerX, centerZ, borderSize);
        }

        updateBar();
        addAllPlayers(server);

        broadcast(server, "The battle has begun! Border: " + borderSize
                + " blocks. First shrink in " + warTime + " seconds. Lives: " + lives + ".");

        if (!hasWinningConditionPossible(server)) {
            noWinningConditionAtStart = true;
            showActionBarToAll(server, "No Winning Condition Possible: Only One Player/Team");
        }
    }

    public static void stop(MinecraftServer server) {
        restoreEliminatedPlayers(server);
        resetBorder(server);
        reset();
        server.getPlayerList().getPlayers().forEach(BattleRoyaleGame::clearForFreshLoadout);
        broadcast(server, "The game has been stopped.");
    }

    /** Clears game state and hides the boss bar without touching the world. */
    public static void reset() {
        phase = Phase.IDLE;
        BAR.removeAllPlayers();
        LIVES_LEFT.clear();
        PLAYER_LOADOUTS.clear();
        postWinResetTicks = 0;
        noWinningConditionAtStart = false;
    }

    public static void resetBorder(MinecraftServer server) {
        WorldBorder border = server.overworld().getWorldBorder();
        border.setSize(border.getAbsoluteMaxSize());
        border.setCenter(0, 0);
    }

    public static void onPlayerLeave(ServerPlayer player) {
        BAR.removePlayer(player);
    }

    public static void onPlayerDeath(ServerPlayer player) {
        if (!isActive() || phase == Phase.ENDING || player.isSpectator() || player.isCreative()) {
            return;
        }

        UUID id = player.getUUID();
        if (!LIVES_LEFT.containsKey(id)) {
            return;
        }

        int livesLeft = Math.max(0, LIVES_LEFT.get(id) - 1);
        LIVES_LEFT.put(id, livesLeft);

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        if (noWinningConditionAtStart) {
            if (livesLeft <= 0 && getContenders(server).isEmpty()) {
                announceNiceDeathAndReset(server);
            }
            return;
        }

        if (livesLeft <= 0) {
            checkForWinner(server);
        }
    }

    public static void onPlayerRespawn(ServerPlayer player) {
        if (!isActive() || phase == Phase.ENDING || player.isCreative()) {
            return;
        }

        UUID id = player.getUUID();
        Integer livesLeft = LIVES_LEFT.get(id);
        if (livesLeft == null) {
            return;
        }

        if (livesLeft > 0) {
            respawnToDrop(player);
            player.sendSystemMessage(Component.literal("Lives remaining: " + livesLeft)
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }

        eliminatePlayer(player);
    }

    public static void tick(MinecraftServer server) {
        if (phase == Phase.IDLE)
            return;

        if (phase == Phase.ENDING) {
            if (--postWinResetTicks <= 0) {
                resetArenaAndGame(server);
            }
            return;
        }

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

    private static Loadout.LoadoutClass randomLoadoutClass() {
        Loadout.LoadoutClass[] classes = Loadout.LoadoutClass.values();
        return classes[(int) (Math.random() * classes.length)];
    }

    private static void respawnToDrop(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        ServerLevel level = server.overworld();
        WorldBorder border = level.getWorldBorder();
        clearForFreshLoadout(player);

        Loadout.LoadoutClass loadoutClass = PLAYER_LOADOUTS.computeIfAbsent(
                player.getUUID(), key -> randomLoadoutClass());
        Loadout.giveLoadout(player, loadoutClass);
        PlayerDrop.drop(level, player, border.getCenterX(), border.getCenterZ(), border.getSize());
    }

    private static void clearForFreshLoadout(ServerPlayer player) {
        player.getInventory().clearContent();
        player.containerMenu.broadcastChanges();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().eat(20, 20.0F);
        player.clearFire();
        player.removeAllEffects();
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
    }

    private static void eliminatePlayer(ServerPlayer player) {
        player.addTag(ELIMINATED_TAG);
        player.setGameMode(GameType.SPECTATOR);
        player.sendSystemMessage(Component.literal("You are out of lives and now spectating.")
                .withStyle(ChatFormatting.RED));
    }

    private static void restoreEliminatedPlayers(MinecraftServer server) {
        ServerLevel level = server.overworld();
        WorldBorder border = level.getWorldBorder();
        double centerX = border.getCenterX();
        double centerZ = border.getCenterZ();
        int safeY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mth.floor(centerX), Mth.floor(centerZ)) + 1;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.getTags().contains(ELIMINATED_TAG)) {
                continue;
            }

            player.removeTag(ELIMINATED_TAG);
            player.setGameMode(GameType.SURVIVAL);
            player.teleportTo(level, centerX, safeY, centerZ, player.getYRot(), player.getXRot());
        }
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

    private static boolean hasWinningConditionPossible(MinecraftServer server) {
        List<ServerPlayer> contenders = getContenders(server);
        if (contenders.size() <= 1) {
            return false;
        }

        Set<Team> teams = contenders.stream()
                .map(ServerPlayer::getTeam)
                .collect(Collectors.toSet());

        boolean allOnTeams = !teams.contains(null);
        if (allOnTeams && teams.size() <= 1) {
            return false;
        }
        return true;
    }

    private static void checkForWinner(MinecraftServer server) {
        if (!isActive() || phase == Phase.ENDING) {
            return;
        }

        List<ServerPlayer> contenders = getContenders(server);
        if (contenders.isEmpty()) {
            return;
        }

        Set<Team> teams = contenders.stream()
                .map(ServerPlayer::getTeam)
                .collect(Collectors.toSet());
        boolean allOnTeams = !teams.contains(null);

        if (allOnTeams && teams.size() == 1) {
            Team winningTeam = teams.iterator().next();
            announceTeamWin(server, winningTeam, contenders);
            return;
        }

        if (contenders.size() == 1) {
            announcePlayerWin(server, contenders.get(0));
        }
    }

    private static List<ServerPlayer> getContenders(MinecraftServer server) {
        List<ServerPlayer> contenders = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Integer livesLeft = LIVES_LEFT.get(player.getUUID());
            if (livesLeft == null || livesLeft <= 0) {
                continue;
            }
            if (!player.isAlive()) {
                continue;
            }
            if (player.isCreative() || player.isSpectator()) {
                continue;
            }
            contenders.add(player);
        }
        return contenders;
    }

    private static void announceNiceDeathAndReset(MinecraftServer server) {
        showTitleToAll(server, Component.literal("Nice Death"), Component.empty());
        broadcast(server, "Nice death.");
        phase = Phase.ENDING;
        postWinResetTicks = 5 * TICKS_PER_SECOND;
    }

    private static void announceTeamWin(MinecraftServer server, Team team, List<ServerPlayer> contenders) {
        Component title = Component.literal(team.getName() + " Won the Game!");
        String names = contenders.stream()
                .map(player -> player.getName().getString())
                .collect(Collectors.joining(", "));
        Component subtitle = Component.literal(names);
        showTitleToAll(server, title, subtitle);

        broadcast(server, team.getName() + " won the game!");
        phase = Phase.ENDING;
        postWinResetTicks = 5 * TICKS_PER_SECOND;
    }

    private static void announcePlayerWin(MinecraftServer server, ServerPlayer winner) {
        Component title = winner.getName().copy().append(Component.literal(" Won the Game!"));
        showTitleToAll(server, title, Component.empty());

        broadcast(server, winner.getName().getString() + " won the game!");
        phase = Phase.ENDING;
        postWinResetTicks = 5 * TICKS_PER_SECOND;
    }

    private static void showTitleToAll(MinecraftServer server, Component title, Component subtitle) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 100, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
    }

    private static void showActionBarToAll(MinecraftServer server, String text) {
        Component message = Component.literal(text).withStyle(ChatFormatting.RED);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.displayClientMessage(message, true);
        }
    }

    private static void resetArenaAndGame(MinecraftServer server) {
        if (ArenaSnapshot.isBusy()) {
            return;
        }

        if (isActive()) {
            stop(server);
        }

        if (!ArenaSnapshot.hasBackup()) {
            broadcast(server, "No arena backup exists to restore.");
            return;
        }

        ArenaSnapshot.beginRestore(server);
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
