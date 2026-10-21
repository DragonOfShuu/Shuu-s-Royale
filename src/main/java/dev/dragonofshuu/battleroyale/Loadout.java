package dev.dragonofshuu.battleroyale;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;
import net.minecraftforge.items.wrapper.RangedWrapper;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Starting gear handed to every participant when the game begins.
 */
public final class Loadout {

    private static final Logger LOGGER = LogUtils.getLogger();

    private Loadout() {
    }

    public enum LoadoutClass {
        MACHINE,
        SNIPER,
        BOMBER,
        CLOSE_COMBAT
    }

    public static void giveLoadout(ServerPlayer player, LoadoutClass loadoutClass) {
        giveBaseLoadout(player);

        switch (loadoutClass) {
            case MACHINE -> giveMachineClassLoadout(player);
            case SNIPER -> giveSniperClassLoadout(player);
            case BOMBER -> giveBomberClassLoadout(player);
            case CLOSE_COMBAT -> giveCloseCombatClassLoadout(player);
        }

        giveTouchUps(player);
    }

    private static void giveBaseLoadout(ServerPlayer player) {
        String[] baseItems = {
                "cgm:assault_rifle",
                "cgm:pistol"
        };
        for (String itemId : baseItems) {
            give(player, itemId, 1, false);
        }

        String[] baseOutsideHotbar = {
                "cgm:basic_bullet",
                "cgm:advanced_bullet",
        };
        for (String itemId : baseOutsideHotbar) {
            giveOutsideHotbar(player, itemId, 128, false);
        }
        String[] baseAttachments = {
                "cgm:short_scope",
                "cgm:medium_scope",
                "cgm:long_scope",
                "cgm:silencer",
                "cgm:light_grip"
        };
        for (String itemId : baseAttachments) {
            giveOutsideHotbar(player, itemId, 1, false);
        }
    }

    private static void giveTouchUps(ServerPlayer player) {
        var inventory = player.getInventory();

        inventory.add(7, new ItemStack(Items.TNT, 32));
        inventory.add(8, new ItemStack(Items.FLINT_AND_STEEL));
    }

    /**
     * They have 4 random classes that they could get: machine, sniper, bomber, and
     * close
     * combat.
     */
    public static void giveMachineClassLoadout(ServerPlayer player) {
        String[] machineClassItems = {
                "cgm:mini_gun",
        };
        for (String itemId : machineClassItems) {
            give(player, itemId, 1, false);
        }

        String[] machineClassOutsideHotbar = {
                "cgm:basic_bullet"
        };
        for (String itemId : machineClassOutsideHotbar) {
            giveOutsideHotbar(player, itemId, 128, false);
        }
    }

    public static void giveSniperClassLoadout(ServerPlayer player) {
        String[] sniperClassItems = {
                "cgm:heavy_rifle"
        };
        for (String itemId : sniperClassItems) {
            give(player, itemId, 1, false);
        }

        String[] sniperClassOutsideHotbar = {
                "cgm:long_scope"
        };
        for (String itemId : sniperClassOutsideHotbar) {
            giveOutsideHotbar(player, itemId, 1, false);
        }
    }

    public static void giveCloseCombatClassLoadout(ServerPlayer player) {
        String[] closeCombatClassItems = {
                "cgm:shotgun"
        };
        for (String itemId : closeCombatClassItems) {
            give(player, itemId, 1, false);
        }

        String[] closeCombatClassOutsideHotbar = {
                "cgm:shell"
        };
        for (String itemId : closeCombatClassOutsideHotbar) {
            giveOutsideHotbar(player, itemId, 64, false);
        }
    }

    public static void giveBomberClassLoadout(ServerPlayer player) {
        String[] bomberClassItems = {
                "cgm:bazooka"
        };
        for (String itemId : bomberClassItems) {
            give(player, itemId, 1, false);
        }

        String[] bomberClassOutsideHotbar = {
                "cgm:missile"
        };
        for (String itemId : bomberClassOutsideHotbar) {
            giveOutsideHotbar(player, itemId, 20, false);
        }
    }

    /**
     * Gives an item by registry ID (e.g. "minecraft:arrow").
     * Looks the item up at runtime, so the gun mod is not a compile-time
     * dependency.
     */
    private static void give(ServerPlayer player, String itemId, int count, boolean canDrop) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);

        if (item == null || item == Items.AIR) {
            LOGGER.warn("Loadout item '{}' does not exist; skipping", itemId);
            return;
        }

        var inventory = player.getInventory();

        ItemStack stack = new ItemStack(item, count);
        if (!inventory.add(stack) && canDrop && !player.level().isClientSide()) {
            player.drop(stack, false); // Inventory full; drop what's left at their feet
        }
    }

    private static void giveOutsideHotbar(ServerPlayer player, String itemId, int count, boolean canDrop) {
        var inventory = player.getInventory();
        IItemHandler mainInventory = new RangedWrapper(new PlayerMainInvWrapper(inventory), 9, 36);

        ResourceLocation id = ResourceLocation.tryParse(itemId);
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) {
            LOGGER.warn("Loadout item '{}' does not exist; skipping", itemId);
            return;
        }
        ItemStack stack = new ItemStack(item, count);
        ItemStack remainder = stack.copy();

        // Attempt to insert the item into the main inventory slots
        for (int i = 0; i < mainInventory.getSlots(); i++) {
            remainder = mainInventory.insertItem(i, remainder, false);
            if (remainder.isEmpty()) {
                break; // Successfully inserted everything
            }
        }

        // If the main inventory is full, drop the remaining items on the ground
        if (!remainder.isEmpty() && canDrop && !player.level().isClientSide()) {
            player.drop(remainder, false);
        }
    }
}
