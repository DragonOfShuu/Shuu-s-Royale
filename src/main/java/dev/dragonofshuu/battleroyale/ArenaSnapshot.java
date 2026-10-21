package dev.dragonofshuu.battleroyale;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Captures the blocks of an arena area, saves them to disk, and restores them
 * later.
 * Capture and restore are spread across ticks; disk I/O runs on a background
 * thread.
 */
public final class ArenaSnapshot {

    private static final Logger LOGGER = LogUtils.getLogger();

    private enum State {
        EMPTY, CAPTURING, SAVING, LOADING, READY, RESTORING
    }

    private static final int CAPTURE_CHUNKS_PER_TICK = 4;
    private static final int RESTORE_CHUNKS_PER_TICK = 2;

    // Update clients, skip neighbor shape updates (prevents cascading physics),
    // never drop items.
    private static final int RESTORE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SUPPRESS_DROPS;

    private static final String BACKUP_FOLDER = "battleroyale_backup";
    private static final String META_FILE = "meta.dat";
    private static final String CONFIG_FILE = "arena_config.dat";

    // Same codec vanilla uses to store chunk sections in region files.
    private static final Codec<PalettedContainer<BlockState>> SECTION_CODEC = PalettedContainer.codecRW(
            Block.BLOCK_STATE_REGISTRY, BlockState.CODEC,
            PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState());

    private record ChunkData(PalettedContainer<BlockState>[] sections,
            Map<BlockPos, CompoundTag> blockEntities) {
    }

    private record Backup(double centerX, double centerZ, int size, Map<Long, ChunkData> chunks) {
    }

    private static final Map<Long, ChunkData> SAVED = new HashMap<>();
    private static final ArrayDeque<ChunkPos> QUEUE = new ArrayDeque<>();

    private static State state = State.EMPTY;
    private static int totalChunks;
    private static int lastReportedStep;
    private static double centerX;
    private static double centerZ;
    private static int size;
    private static boolean arenaConfigured;
    private static int warTime;
    private static int shrinkAmount;
    private static int shrinkTime;
    private static int lives;
    private static boolean gameSettingsConfigured;
    private static CompletableFuture<?> pendingIo = CompletableFuture.completedFuture(null);

    private ArenaSnapshot() {
    }

    public static boolean isBusy() {
        return state == State.CAPTURING || state == State.SAVING
                || state == State.LOADING || state == State.RESTORING;
    }

    public static boolean hasBackup() {
        return state == State.READY;
    }

    public static boolean hasArenaConfig() {
        return arenaConfigured;
    }

    public static boolean hasGameSettings() {
        return gameSettingsConfigured;
    }

    public static double getCenterX() {
        return centerX;
    }

    public static double getCenterZ() {
        return centerZ;
    }

    public static int getSize() {
        return size;
    }

    public static int getWarTime() {
        return warTime;
    }

    public static int getShrinkAmount() {
        return shrinkAmount;
    }

    public static int getShrinkTime() {
        return shrinkTime;
    }

    public static int getLives() {
        return lives;
    }

    public static void setArenaConfig(MinecraftServer server, double cx, double cz, int arenaSize) {
        centerX = cx;
        centerZ = cz;
        size = arenaSize;
        arenaConfigured = true;
        saveConfigAsync(server);
    }

    public static void setWarTime(MinecraftServer server, int value) {
        warTime = value;
        gameSettingsConfigured = true;
        saveConfigAsync(server);
    }

    public static void setShrinkAmount(MinecraftServer server, int value) {
        shrinkAmount = value;
        gameSettingsConfigured = true;
        saveConfigAsync(server);
    }

    public static void setShrinkTime(MinecraftServer server, int value) {
        shrinkTime = value;
        gameSettingsConfigured = true;
        saveConfigAsync(server);
    }

    public static void setLives(MinecraftServer server, int value) {
        lives = value;
        gameSettingsConfigured = true;
        saveConfigAsync(server);
    }

    public static void setGameSettings(MinecraftServer server, int warTime, int shrinkAmount, int shrinkTime,
            int lives) {
        ArenaSnapshot.warTime = warTime;
        ArenaSnapshot.shrinkAmount = shrinkAmount;
        ArenaSnapshot.shrinkTime = shrinkTime;
        ArenaSnapshot.lives = lives;
        gameSettingsConfigured = true;
        saveConfigAsync(server);
    }

    public static void clear() {
        clearBackupState();
        arenaConfigured = false;
        gameSettingsConfigured = false;
    }

    private static void clearBackupState() {
        SAVED.clear();
        QUEUE.clear();
        state = State.EMPTY;
    }

    // ------------------------------------------------------------------
    // Capture / restore jobs
    // ------------------------------------------------------------------

    public static void beginCapture(MinecraftServer server, double cx, double cz, int arenaSize) {
        clearBackupState();
        centerX = cx;
        centerZ = cz;
        size = arenaSize;
        arenaConfigured = true;

        double half = arenaSize / 2.0;
        int minChunkX = SectionPos.blockToSectionCoord(Mth.floor(cx - half));
        int maxChunkX = SectionPos.blockToSectionCoord(Mth.floor(cx + half));
        int minChunkZ = SectionPos.blockToSectionCoord(Mth.floor(cz - half));
        int maxChunkZ = SectionPos.blockToSectionCoord(Mth.floor(cz + half));

        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                QUEUE.add(new ChunkPos(x, z));
            }
        }

        startJob(State.CAPTURING);
        BattleRoyaleGame.broadcast(server, "Backing up arena (" + totalChunks + " chunks)...");
    }

    public static void beginRestore(MinecraftServer server) {
        QUEUE.clear();
        for (long key : SAVED.keySet()) {
            QUEUE.add(new ChunkPos(key));
        }

        startJob(State.RESTORING);
        BattleRoyaleGame.broadcast(server, "Restoring arena (" + totalChunks + " chunks)...");
    }

    private static void startJob(State newState) {
        totalChunks = QUEUE.size();
        lastReportedStep = 0;
        state = newState;
    }

    public static void tick(MinecraftServer server) {
        if (state != State.CAPTURING && state != State.RESTORING)
            return;

        ServerLevel level = server.overworld();
        int budget = state == State.CAPTURING ? CAPTURE_CHUNKS_PER_TICK : RESTORE_CHUNKS_PER_TICK;

        for (int i = 0; i < budget && !QUEUE.isEmpty(); i++) {
            ChunkPos pos = QUEUE.poll();
            if (state == State.CAPTURING) {
                captureChunk(level, pos);
            } else {
                restoreChunk(level, pos);
            }
        }

        reportProgress(server);

        if (QUEUE.isEmpty()) {
            finish(server, level);
        }
    }

    private static void captureChunk(ServerLevel level, ChunkPos pos) {
        LevelChunk chunk = level.getChunk(pos.x, pos.z);
        LevelChunkSection[] sections = chunk.getSections();

        @SuppressWarnings("unchecked")
        PalettedContainer<BlockState>[] copies = new PalettedContainer[sections.length];
        for (int i = 0; i < sections.length; i++) {
            copies[i] = sections[i].getStates().copy();
        }

        Map<BlockPos, CompoundTag> blockEntities = new HashMap<>();
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            blockEntities.put(blockEntity.getBlockPos().immutable(), blockEntity.saveWithFullMetadata());
        }

        SAVED.put(pos.toLong(), new ChunkData(copies, blockEntities));
    }

    private static void restoreChunk(ServerLevel level, ChunkPos pos) {
        ChunkData data = SAVED.get(pos.toLong());
        if (data == null)
            return;

        LevelChunk chunk = level.getChunk(pos.x, pos.z);
        LevelChunkSection[] sections = chunk.getSections();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int baseX = pos.getMinBlockX();
        int baseZ = pos.getMinBlockZ();
        int sectionCount = Math.min(sections.length, data.sections().length);

        // Restore block states, only touching blocks that actually changed
        for (int i = 0; i < sectionCount; i++) {
            PalettedContainer<BlockState> saved = data.sections()[i];
            if (saved == null)
                continue; // Section failed to load from disk; leave it alone

            LevelChunkSection current = sections[i];
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));

            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState wanted = saved.get(x, y, z);
                        if (current.getBlockState(x, y, z) != wanted) {
                            cursor.set(baseX + x, baseY + y, baseZ + z);
                            level.setBlock(cursor, wanted, RESTORE_FLAGS);
                        }
                    }
                }
            }
        }

        // Restore block entity data (chest contents, sign text, etc.)
        for (Map.Entry<BlockPos, CompoundTag> entry : data.blockEntities().entrySet()) {
            BlockPos bePos = entry.getKey();
            BlockEntity blockEntity = level.getBlockEntity(bePos);
            if (blockEntity != null) {
                blockEntity.load(entry.getValue().copy());
                blockEntity.setChanged();
                BlockState blockState = level.getBlockState(bePos);
                level.sendBlockUpdated(bePos, blockState, blockState, Block.UPDATE_CLIENTS);
            }
        }
    }

    private static void finish(MinecraftServer server, ServerLevel level) {
        if (state == State.CAPTURING) {
            saveAsync(server);
            return;
        }

        clearLeftoverEntities(level);

        // WorldBorder border = level.getWorldBorder();
        // border.setCenter(centerX, centerZ);
        // border.setSize(size);

        state = State.READY;
        BattleRoyaleGame.broadcast(server, "Arena restored.");
    }

    private static void clearLeftoverEntities(ServerLevel level) {
        double half = size / 2.0 + 16;
        AABB area = new AABB(
                centerX - half, level.getMinBuildHeight(), centerZ - half,
                centerX + half, level.getMaxBuildHeight(), centerZ + half);

        List<Entity> leftovers = level.getEntities((Entity) null, area, entity -> entity instanceof ItemEntity
                || entity instanceof PrimedTnt
                || entity instanceof FallingBlockEntity
                || entity instanceof ExperienceOrb
                || entity instanceof Projectile);

        leftovers.forEach(Entity::discard);
    }

    private static void reportProgress(MinecraftServer server) {
        if (totalChunks == 0)
            return;
        int done = totalChunks - QUEUE.size();
        int step = done * 10 / totalChunks; // Report every 10%
        if (step > lastReportedStep && step < 10) {
            lastReportedStep = step;
            String verb = state == State.CAPTURING ? "Backup" : "Restore";
            BattleRoyaleGame.broadcast(server, verb + " progress: " + (step * 10) + "%");
        }
    }

    // ------------------------------------------------------------------
    // Disk persistence
    // ------------------------------------------------------------------

    private static Path backupDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(BACKUP_FOLDER).normalize();
    }

    private static void saveAsync(MinecraftServer server) {
        state = State.SAVING;
        BattleRoyaleGame.broadcast(server, "Arena captured. Saving backup to disk...");

        Backup backup = new Backup(centerX, centerZ, size, new HashMap<>(SAVED));
        Path dir = backupDir(server);

        pendingIo = CompletableFuture.runAsync(() -> writeToDisk(dir, backup), Util.ioPool())
                .whenComplete((ignored, error) -> server.execute(() -> {
                    state = State.READY;
                    if (error != null) {
                        LOGGER.error("Failed to save arena backup", error);
                        BattleRoyaleGame.broadcast(server,
                                "Saving the backup failed; it is only kept in memory. Check the server log.");
                    } else {
                        BattleRoyaleGame.broadcast(server, "Arena backup saved.");
                    }
                }));
    }

    private static void saveConfigAsync(MinecraftServer server) {
        Path dir = backupDir(server);
        double cx = centerX;
        double cz = centerZ;
        int arenaSize = size;

        pendingIo = CompletableFuture.runAsync(() -> writeConfigToDisk(dir, cx, cz, arenaSize), Util.ioPool())
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        LOGGER.error("Failed to save arena config", error);
                    }
                });
    }

    /**
     * Called when the server starts. Loads an existing backup in the background.
     */
    public static void loadFromDisk(MinecraftServer server) {
        Path dir = backupDir(server);
        loadConfigFromDisk(dir);

        if (!Files.exists(dir.resolve(META_FILE)))
            return;

        state = State.LOADING;
        LOGGER.info("Loading arena backup from {}", dir);

        pendingIo = CompletableFuture.supplyAsync(() -> readFromDisk(dir), Util.ioPool())
                .whenComplete((backup, error) -> server.execute(() -> {
                    if (error != null || backup == null) {
                        LOGGER.error("Failed to load arena backup", error);
                        state = State.EMPTY;
                        return;
                    }
                    SAVED.clear();
                    SAVED.putAll(backup.chunks());
                    centerX = backup.centerX();
                    centerZ = backup.centerZ();
                    size = backup.size();
                    arenaConfigured = true;
                    state = State.READY;
                    LOGGER.info("Loaded arena backup: {} chunks, size {}", backup.chunks().size(), backup.size());
                }));
    }

    /**
     * Blocks until any in-progress save or load finishes. Call while the server is
     * stopping.
     */
    public static void awaitPendingIo() {
        try {
            pendingIo.join();
        } catch (Exception e) {
            LOGGER.error("Arena backup I/O failed during shutdown", e);
        }
    }

    private static void writeToDisk(Path dir, Backup backup) {
        try {
            Files.createDirectories(dir);

            // Remove the metadata first so a half-written backup is never loaded
            Files.deleteIfExists(dir.resolve(META_FILE));
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : files.toList()) {
                    String fileName = file.getFileName().toString();
                    if (fileName.endsWith(".dat") && !fileName.equals(CONFIG_FILE)) {
                        Files.delete(file);
                    }
                }
            }

            writeConfigToDisk(dir, backup.centerX(), backup.centerZ(), backup.size());

            // Group chunks into one file per 32x32-chunk region
            Map<Long, ListTag> regions = new HashMap<>();
            for (Map.Entry<Long, ChunkData> entry : backup.chunks().entrySet()) {
                ChunkPos pos = new ChunkPos(entry.getKey());
                long regionKey = ChunkPos.asLong(pos.getRegionX(), pos.getRegionZ());
                regions.computeIfAbsent(regionKey, key -> new ListTag())
                        .add(encodeChunk(pos, entry.getValue()));
            }

            for (Map.Entry<Long, ListTag> entry : regions.entrySet()) {
                ChunkPos region = new ChunkPos(entry.getKey());
                CompoundTag root = new CompoundTag();
                root.put("Chunks", entry.getValue());
                writeTag(dir.resolve("r." + region.x + "." + region.z + ".dat"), root);
            }

            CompoundTag meta = new CompoundTag();
            meta.putInt("DataVersion", currentDataVersion());
            meta.putDouble("CenterX", backup.centerX());
            meta.putDouble("CenterZ", backup.centerZ());
            meta.putInt("Size", backup.size());
            meta.putInt("ChunkCount", backup.chunks().size());
            writeTag(dir.resolve(META_FILE), meta);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeConfigToDisk(Path dir, double cx, double cz, int arenaSize) {
        try {
            Files.createDirectories(dir);
            CompoundTag config = new CompoundTag();
            config.putInt("DataVersion", currentDataVersion());
            config.putDouble("CenterX", cx);
            config.putDouble("CenterZ", cz);
            config.putInt("Size", arenaSize);
            config.putInt("WarTime", warTime);
            config.putInt("ShrinkAmount", shrinkAmount);
            config.putInt("ShrinkTime", shrinkTime);
            config.putInt("Lives", lives);
            config.putBoolean("GameSettingsConfigured", gameSettingsConfigured);
            writeTag(dir.resolve(CONFIG_FILE), config);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void loadConfigFromDisk(Path dir) {
        Path configPath = dir.resolve(CONFIG_FILE);
        if (!Files.exists(configPath)) {
            return;
        }

        try {
            CompoundTag config = readTag(configPath);
            centerX = config.getDouble("CenterX");
            centerZ = config.getDouble("CenterZ");
            size = config.getInt("Size");
            arenaConfigured = size > 0;

            if (config.contains("GameSettingsConfigured", Tag.TAG_BYTE)) {
                gameSettingsConfigured = config.getBoolean("GameSettingsConfigured");
            } else {
                gameSettingsConfigured = config.contains("WarTime", Tag.TAG_INT)
                        && config.contains("ShrinkAmount", Tag.TAG_INT)
                        && config.contains("ShrinkTime", Tag.TAG_INT)
                        && config.contains("Lives", Tag.TAG_INT);
            }

            if (gameSettingsConfigured) {
                warTime = config.getInt("WarTime");
                shrinkAmount = config.getInt("ShrinkAmount");
                shrinkTime = config.getInt("ShrinkTime");
                lives = config.getInt("Lives");
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load arena config", e);
        }
    }

    private static Backup readFromDisk(Path dir) {
        try {
            CompoundTag meta = readTag(dir.resolve(META_FILE));
            int dataVersion = meta.getInt("DataVersion");
            if (dataVersion != currentDataVersion()) {
                LOGGER.warn(
                        "Arena backup was made with data version {} (current {}). Some blocks may not restore correctly.",
                        dataVersion, currentDataVersion());
            }

            Map<Long, ChunkData> chunks = new HashMap<>();
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : files.toList()) {
                    String name = file.getFileName().toString();
                    if (!name.startsWith("r.") || !name.endsWith(".dat"))
                        continue;

                    ListTag list = readTag(file).getList("Chunks", Tag.TAG_COMPOUND);
                    for (int i = 0; i < list.size(); i++) {
                        CompoundTag chunkTag = list.getCompound(i);
                        ChunkPos pos = new ChunkPos(chunkTag.getInt("X"), chunkTag.getInt("Z"));
                        chunks.put(pos.toLong(), decodeChunk(chunkTag));
                    }
                }
            }

            int expected = meta.getInt("ChunkCount");
            if (chunks.size() != expected) {
                LOGGER.warn("Arena backup expected {} chunks but found {}", expected, chunks.size());
            }

            return new Backup(meta.getDouble("CenterX"), meta.getDouble("CenterZ"), meta.getInt("Size"), chunks);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static CompoundTag encodeChunk(ChunkPos pos, ChunkData data) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", pos.x);
        tag.putInt("Z", pos.z);

        ListTag sections = new ListTag();
        for (PalettedContainer<BlockState> section : data.sections()) {
            sections.add(SECTION_CODEC.encodeStart(NbtOps.INSTANCE, section).getOrThrow(false, LOGGER::error));
        }
        tag.put("Sections", sections);

        ListTag blockEntities = new ListTag();
        for (CompoundTag blockEntity : data.blockEntities().values()) {
            blockEntities.add(blockEntity.copy());
        }
        tag.put("BlockEntities", blockEntities);

        return tag;
    }

    private static ChunkData decodeChunk(CompoundTag tag) {
        ListTag sectionTags = tag.getList("Sections", Tag.TAG_COMPOUND);

        @SuppressWarnings("unchecked")
        PalettedContainer<BlockState>[] sections = new PalettedContainer[sectionTags.size()];
        for (int i = 0; i < sectionTags.size(); i++) {
            sections[i] = SECTION_CODEC.parse(NbtOps.INSTANCE, sectionTags.getCompound(i))
                    .resultOrPartial(LOGGER::error)
                    .orElse(null);
        }

        Map<BlockPos, CompoundTag> blockEntities = new HashMap<>();
        ListTag beTags = tag.getList("BlockEntities", Tag.TAG_COMPOUND);
        for (int i = 0; i < beTags.size(); i++) {
            CompoundTag beTag = beTags.getCompound(i);
            blockEntities.put(BlockEntity.getPosFromTag(beTag), beTag);
        }

        return new ChunkData(sections, blockEntities);
    }

    private static void writeTag(Path path, CompoundTag tag) throws IOException {
        try (OutputStream out = Files.newOutputStream(path)) {
            NbtIo.writeCompressed(tag, out);
        }
    }

    private static CompoundTag readTag(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return NbtIo.readCompressed(in);
        }
    }

    private static int currentDataVersion() {
        return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
    }
}
