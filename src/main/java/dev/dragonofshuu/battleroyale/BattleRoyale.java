package dev.dragonofshuu.battleroyale;

import dev.dragonofshuu.battleroyale.shared.SharedUiNetworking;
import net.minecraftforge.fml.common.Mod;

@Mod(BattleRoyale.MOD_ID)
public class BattleRoyale {
    public static final String MOD_ID = "shuubattleroyale";

    public BattleRoyale() {
        SharedUiNetworking.register();
        // Event handlers are registered via @Mod.EventBusSubscriber in ModEvents.
    }
}
