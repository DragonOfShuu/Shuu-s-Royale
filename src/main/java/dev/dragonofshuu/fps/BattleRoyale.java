package dev.dragonofshuu.fps;

import dev.dragonofshuu.fps.shared.SharedUiNetworking;
import net.minecraftforge.fml.common.Mod;

@Mod(BattleRoyale.MOD_ID)
public class BattleRoyale {
    public static final String MOD_ID = "shuusfps";

    public BattleRoyale() {
        SharedUiNetworking.register();
        // Event handlers are registered via @Mod.EventBusSubscriber in ModEvents.
    }
}
