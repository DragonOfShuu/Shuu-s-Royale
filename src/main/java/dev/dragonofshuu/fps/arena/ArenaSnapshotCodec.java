package dev.dragonofshuu.fps.arena;

import com.mojang.serialization.DynamicOps;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

final class ArenaSnapshotCodec {

    private ArenaSnapshotCodec() {
    }

    static CompoundTag encodeChunk(ChunkPos pos, ArenaSnapshot.ChunkData data) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", pos.x);
        tag.putInt("Z", pos.z);

        ListTag sections = new ListTag();
        DynamicOps<Tag> ops = NbtOps.INSTANCE;
        for (PalettedContainer<BlockState> section : data.sections()) {
            sections.add(ArenaSnapshot.SECTION_CODEC.encodeStart(ops, section)
                    .getOrThrow(false, ArenaSnapshot.LOGGER::error));
        }
        tag.put("Sections", sections);

        ListTag blockEntities = new ListTag();
        for (CompoundTag blockEntity : data.blockEntities().values()) {
            blockEntities.add(blockEntity.copy());
        }
        tag.put("BlockEntities", blockEntities);

        return tag;
    }

    static ArenaSnapshot.ChunkData decodeChunk(CompoundTag tag) {
        ListTag sectionTags = tag.getList("Sections", Tag.TAG_COMPOUND);

        @SuppressWarnings("unchecked")
        PalettedContainer<BlockState>[] sections = new PalettedContainer[sectionTags.size()];
        for (int i = 0; i < sectionTags.size(); i++) {
            sections[i] = ArenaSnapshot.SECTION_CODEC.parse(NbtOps.INSTANCE, sectionTags.getCompound(i))
                    .resultOrPartial(ArenaSnapshot.LOGGER::error)
                    .orElse(null);
        }

        Map<net.minecraft.core.BlockPos, CompoundTag> blockEntities = new HashMap<>();
        ListTag beTags = tag.getList("BlockEntities", Tag.TAG_COMPOUND);
        for (int i = 0; i < beTags.size(); i++) {
            CompoundTag beTag = beTags.getCompound(i);
            blockEntities.put(BlockEntity.getPosFromTag(beTag), beTag);
        }

        return new ArenaSnapshot.ChunkData(sections, blockEntities);
    }

    static void writeTag(Path path, CompoundTag tag) throws IOException {
        try (OutputStream out = Files.newOutputStream(path)) {
            NbtIo.writeCompressed(tag, out);
        }
    }

    static CompoundTag readTag(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return NbtIo.readCompressed(in);
        }
    }

    static int currentDataVersion() {
        return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
    }
}
