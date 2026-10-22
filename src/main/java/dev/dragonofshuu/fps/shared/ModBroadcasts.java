package dev.dragonofshuu.fps.shared;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class ModBroadcasts {

    public static void broadcast(MinecraftServer server, String message) {
        broadcast(server, message, "FPS", ChatFormatting.DARK_PURPLE);
    }

    public static void broadcast(MinecraftServer server, String message, String prefix, ChatFormatting prefixColor) {
        server.getPlayerList().getPlayers().forEach(player -> {
            broadcastToPlayer(player, message, prefix, prefixColor);
        });
    }

    public static void broadcastToOps(MinecraftServer server, String message) {
        broadcastToOps(server, message, "FPS", ChatFormatting.DARK_PURPLE);
    }

    public static void broadcastToOps(MinecraftServer server, String message, String prefix,
            ChatFormatting prefixColor) {
        server.getPlayerList().getPlayers().forEach(player -> {
            if (server.getPlayerList().isOp(player.getGameProfile())) {
                broadcastToPlayer(player, message, prefix, prefixColor);
            }
        });
    }

    public static void broadcastToPlayer(ServerPlayer player, String message, String prefix,
            ChatFormatting prefixColor) {
        player.sendSystemMessage(
                Component.literal("[" + prefix + "] ").withStyle(prefixColor)
                        .append(Component.literal(message).withStyle(ChatFormatting.WHITE)));
    }
}
