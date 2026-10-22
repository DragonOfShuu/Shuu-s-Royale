package dev.dragonofshuu.fps.modes.arenaTimed;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;

final class ArenaTimedAnnouncements {

    private ArenaTimedAnnouncements() {
    }

    static void announceWinnersLosersAndDraws(MinecraftServer server, Set<UUID> winners, Set<UUID> draws) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean isDraw = draws.contains(player.getUUID());
            boolean isWinner = winners.contains(player.getUUID());
            String label = isDraw ? "DRAW" : (isWinner ? "WINNER" : "LOST");
            ChatFormatting color = isDraw ? ChatFormatting.YELLOW
                    : (isWinner ? ChatFormatting.GOLD : ChatFormatting.RED);
            Component title = Component.literal(label)
                    .withStyle(color, ChatFormatting.BOLD);
            Component subtitle = Component.empty();

            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 40, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
    }

    static void broadcast(MinecraftServer server, String message) {
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("[AT] ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(message).withStyle(ChatFormatting.WHITE)),
                false);
    }
}
