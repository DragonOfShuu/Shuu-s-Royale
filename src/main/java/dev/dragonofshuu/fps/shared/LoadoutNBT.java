package dev.dragonofshuu.fps.shared;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

public class LoadoutNBT {
    public static CompoundTag loadedAssaultRifle(Optional<CompoundTag> nbt) {
        CompoundTag tag = nbt.orElse(new CompoundTag());
        tag.putInt("AmmoCount", 40);
        return tag;
    }

    public static CompoundTag loadedPistol(Optional<CompoundTag> nbt) {
        CompoundTag tag = nbt.orElse(new CompoundTag());
        tag.putInt("AmmoCount", 16);
        return tag;
    }

    public static CompoundTag loadedMiniGun(Optional<CompoundTag> nbt) {
        CompoundTag tag = nbt.orElse(new CompoundTag());
        tag.putInt("AmmoCount", 100);
        return tag;
    }

    public static CompoundTag loadedHeavyRifle(Optional<CompoundTag> nbt) {
        CompoundTag tag = nbt.orElse(new CompoundTag());
        tag.putInt("AmmoCount", 4);
        return tag;
    }

    public static CompoundTag loadedShotgun(Optional<CompoundTag> nbt) {
        CompoundTag tag = nbt.orElse(new CompoundTag());
        tag.putInt("AmmoCount", 8);
        return tag;
    }

    public static CompoundTag loadedBazooka(Optional<CompoundTag> nbt) {
        CompoundTag tag = nbt.orElse(new CompoundTag());
        tag.putInt("AmmoCount", 1);
        return tag;
    }

    public static CompoundTag addMediumScope(CompoundTag tag) {
        return addMediumScope(Optional.of(tag));
    }

    public static CompoundTag addShortScope(CompoundTag tag) {
        return addShortScope(Optional.of(tag));
    }

    public static CompoundTag addShortScope(Optional<CompoundTag> originalTag) {
        CompoundTag tag = originalTag.orElse(new CompoundTag());
        CompoundTag attachments = tag.getCompound("Attachments");
        CompoundTag shortScope = NBTifyItem("cgm:short_scope");
        attachments.put("Scope", shortScope);
        tag.put("Attachments", attachments);
        return tag;
    }

    public static CompoundTag addMediumScope(Optional<CompoundTag> originalTag) {
        CompoundTag tag = originalTag.orElse(new CompoundTag());
        CompoundTag attachments = tag.getCompound("Attachments");
        CompoundTag mediumScope = NBTifyItem("cgm:medium_scope");
        attachments.put("Scope", mediumScope);
        tag.put("Attachments", attachments);
        return tag;
    }

    public static CompoundTag addLongScope(CompoundTag tag) {
        return addLongScope(Optional.of(tag));
    }

    public static CompoundTag addLongScope(Optional<CompoundTag> originalTag) {
        CompoundTag tag = originalTag.orElse(new CompoundTag());
        CompoundTag attachments = tag.getCompound("Attachments");
        CompoundTag longScope = NBTifyItem("cgm:long_scope");
        attachments.put("Scope", longScope);
        tag.put("Attachments", attachments);
        return tag;
    }

    public static CompoundTag addTacticalStock(CompoundTag tag) {
        return addTacticalStock(Optional.of(tag));
    }

    public static CompoundTag addTacticalStock(Optional<CompoundTag> originalTag) {
        CompoundTag tag = originalTag.orElse(new CompoundTag());
        CompoundTag attachments = tag.getCompound("Attachments");
        CompoundTag tacticalStock = NBTifyItem("cgm:tactical_stock");
        attachments.put("Stock", tacticalStock);
        tag.put("Attachments", attachments);
        return tag;
    }

    public static CompoundTag NBTifyItem(String name) {
        ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(name)), 1);
        return stack.serializeNBT();
    }
}
