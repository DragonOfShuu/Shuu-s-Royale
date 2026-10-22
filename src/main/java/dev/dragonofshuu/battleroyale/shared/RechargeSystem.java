package dev.dragonofshuu.battleroyale.shared;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RechargeSystem {

    public static final long RECHARGE_DELAY_TICKS = 6L * 20L;
    public static final int REGENERATION_DURATION_TICKS = 100;
    public static final int REGENERATION_AMPLIFIER = 2;
    public static final int REGENERATION_REFRESH_THRESHOLD_TICKS = 20;

    private static final Map<UUID, Long> LAST_DAMAGE_TICKS = new HashMap<>();
    private static final Set<UUID> REGEN_ACTIVE = new HashSet<>();

    private RechargeSystem() {
    }

    public static boolean shouldApplyRegen(long elapsedTicks, float currentHealth, float maxHealth) {
        return elapsedTicks >= RECHARGE_DELAY_TICKS && maxHealth > 0.0F && currentHealth < maxHealth;
    }

    public static void onDamage(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }

        LAST_DAMAGE_TICKS.put(player.getUUID(), player.getServer().overworld().getGameTime());
        REGEN_ACTIVE.remove(player.getUUID());
        player.removeEffect(MobEffects.REGENERATION);
    }

    public static void clearPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }

        LAST_DAMAGE_TICKS.remove(player.getUUID());
        REGEN_ACTIVE.remove(player.getUUID());
        player.removeEffect(MobEffects.REGENERATION);
    }

    public static void onRespawn(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }

        LAST_DAMAGE_TICKS.put(player.getUUID(), player.getServer().overworld().getGameTime());
        REGEN_ACTIVE.remove(player.getUUID());
        player.removeEffect(MobEffects.REGENERATION);
    }

    public static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }

        if (!SharedMatchStats.isMatchActive()) {
            clear();
            return;
        }

        long now = server.overworld().getGameTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Long lastDamageTick = LAST_DAMAGE_TICKS.get(player.getUUID());
            if (player.isDeadOrDying() || !player.isAlive() || player.getHealth() >= player.getMaxHealth()) {
                if (REGEN_ACTIVE.remove(player.getUUID())) {
                    player.removeEffect(MobEffects.REGENERATION);
                }
                LAST_DAMAGE_TICKS.remove(player.getUUID());
                continue;
            }

            if (lastDamageTick == null) {
                continue;
            }

            long elapsedTicks = now - lastDamageTick;
            if (shouldApplyRegen(elapsedTicks, player.getHealth(), player.getMaxHealth())) {
                REGEN_ACTIVE.add(player.getUUID());
                MobEffectInstance activeRegen = player.getEffect(MobEffects.REGENERATION);
                boolean needsRefresh = activeRegen == null
                        || activeRegen.getAmplifier() != REGENERATION_AMPLIFIER
                        || activeRegen.getDuration() <= REGENERATION_REFRESH_THRESHOLD_TICKS;
                if (needsRefresh) {
                    player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                            REGENERATION_DURATION_TICKS,
                            REGENERATION_AMPLIFIER,
                            false,
                            false,
                            true));
                }
            } else {
                REGEN_ACTIVE.remove(player.getUUID());
                player.removeEffect(MobEffects.REGENERATION);
            }
        }
    }

    public static void clear() {
        LAST_DAMAGE_TICKS.clear();
        REGEN_ACTIVE.clear();
    }
}