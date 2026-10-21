package dev.dragonofshuu.battleroyale;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

/**
 * Starting gear handed to every participant when the game begins.
 */
public final class Loadout {

    private static final Logger LOGGER = LogUtils.getLogger();

    private Loadout() {
    }

    public static void giveLoadout(ServerPlayer player) {
        // Add Crayfish's Gun Mod weapons and ammo here, one line per item, e.g.:
        // give(player, "modid:item_name", 1);
    }

    /**
     * Gives an item by registry ID (e.g. "minecraft:arrow").
     * Looks the item up at runtime, so the gun mod is not a compile-time
     * dependency.
     */
    private static void give(ServerPlayer player, String itemId, int count) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);

        if (item == null || item == Items.AIR) {
            LOGGER.warn("Loadout item '{}' does not exist; skipping", itemId);
            return;
        }

        ItemStack stack = new ItemStack(item, count);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false); // Inventory full; drop what's left at their feet
        }
    }
}
