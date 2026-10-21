package dev.dragonofshuu.battleroyale.modes.battleRoyale;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Team;

import java.util.List;
import java.util.stream.Collectors;

public final class BattleRoyaleAnnouncements {

    private BattleRoyaleAnnouncements() {
    }

    public static void showActionBarToAll(MinecraftServer server, String text) {
        Component message = Component.literal(text).withStyle(ChatFormatting.RED);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.displayClientMessage(message, true);
        }
    }

    public static void announceNiceDeath(MinecraftServer server) {
        showTitleToAll(server, Component.literal("Nice Death"), Component.empty());
        BattleRoyaleGame.broadcast(server, "Nice death.");
    }

    public static void announceTeamWin(MinecraftServer server, Team team, List<ServerPlayer> contenders) {
        Component title = Component.literal(team.getName() + " Won the Game!");
        String names = contenders.stream()
                .map(player -> player.getName().getString())
                .collect(Collectors.joining(", "));
        Component subtitle = Component.literal(names);
        showTitleToAll(server, title, subtitle);

        BattleRoyaleGame.broadcast(server, team.getName() + " won the game!");
    }

    public static void announcePlayerWin(MinecraftServer server, ServerPlayer winner) {
        Component title = winner.getName().copy().append(Component.literal(" Won the Game!"));
        showTitleToAll(server, title, Component.empty());

        BattleRoyaleGame.broadcast(server, winner.getName().getString() + " won the game!");
    }

    public static void showTitleToAll(MinecraftServer server, Component title, Component subtitle) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 100, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
    }
}
