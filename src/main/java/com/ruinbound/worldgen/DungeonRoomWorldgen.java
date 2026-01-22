package com.ruinbound.worldgen;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.universe.world.worldgen.IWorldGen;
import com.hypixel.hytale.server.core.universe.world.worldgen.WorldGenLoadException;
import com.hypixel.hytale.server.worldgen.chunk.ChunkGenerator;
import com.hypixel.hytale.server.worldgen.chunk.ZoneBiomeResult;
import com.hypixel.hytale.server.worldgen.zone.Zone;
import com.hypixel.hytale.server.worldgen.zone.ZoneGeneratorResult;

import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spawns a prefab room on the surface with a low chance per newly-generated chunk.
 * Uses Zones (stable) instead of biome tint/edges. Enforces "1 dungeon per zone" (normal world only).
 */
public final class DungeonRoomWorldgen {

    // ===== Debug =====
    private static final boolean DEBUG_WORLDGEN = true;
    private static void dbg(String msg) {
        if (DEBUG_WORLDGEN) System.out.println("[Ruinbound/WG] " + msg);
    }

    // ===== Config =====
    private final String prefabFile;      // e.g. "yor_dungeon_room.prefab.json"
    private final int chancePerChunk;     // 1 => always try, 10 => ~1/10, 100 => ~1/100
    private final int edgePadding;        // keep placement away from chunk edges
    private static final int CHUNK_SIZE = 16;

    // ===== State =====
    // Zone ID -> already spawned
    private static final Set<Integer> ZONES_WITH_DUNGEON = ConcurrentHashMap.newKeySet();
    // Chunk coords -> already spawned (extra safety against "newly generated" quirks)
    private static final Set<Long> CHUNKS_WITH_DUNGEON = ConcurrentHashMap.newKeySet();

    public DungeonRoomWorldgen(String prefabFile, int chancePerChunk, int edgePadding) {
        this.prefabFile = prefabFile;
        this.chancePerChunk = Math.max(1, chancePerChunk);
        this.edgePadding = Math.max(0, edgePadding);
    }

    private static long chunkKey(int cx, int cz) {
        return (((long) cx) << 32) ^ (cz & 0xffffffffL);
    }

    /** Call this from your plugin when you receive the ChunkPreLoadProcessEvent. */
    public void onChunkPreLoad(ChunkPreLoadProcessEvent event) {
        if (!event.isNewlyGenerated()) return;

        WorldChunk chunk = event.getChunk();
        World world = chunk.getWorld();

        // Alleen de normale wereld
        // (World.DEFAULT is a String constant; world.getName() returns the world's name)
        //if (!World.DEFAULT.equals(world.getName())) return;

        // Extra guard: nooit 2x in dezelfde chunk
        long cKey = chunkKey(chunk.getX(), chunk.getZ());
        if (CHUNKS_WITH_DUNGEON.contains(cKey)) return;

        // Deterministic RNG per chunk
        long seed = world.getWorldConfig().getSeed();
        long mixed = seed ^ (chunk.getX() * 341873128712L) ^ (chunk.getZ() * 132897987541L);
        Random r = new Random(mixed);

        // Rarity gate
        if (r.nextInt(chancePerChunk) != 0) return;

        // Prefab selection (load once per event)
        BlockSelection selection = PrefabStore.get().getServerPrefab(prefabFile);
        if (selection == null) return;

        // Get the world's generator and ensure it's the standard ChunkGenerator (has zone lookup)
        IWorldGen gen;
        try {
            gen = world.getWorldConfig().getWorldGenProvider().getGenerator();
        } catch (WorldGenLoadException e) {
            if (DEBUG_WORLDGEN) dbg("WorldGenLoadException: " + e.getMessage());
            return;
        } catch (Exception e) {
            if (DEBUG_WORLDGEN) dbg("Failed to get world generator: " + e.getMessage());
            return;
        }

        if (!(gen instanceof ChunkGenerator chunkGen)) {
            // Could be a different generator type; zones might not exist there
            if (DEBUG_WORLDGEN) dbg("World generator is not ChunkGenerator: " + gen.getClass().getName());
            return;
        }

        // Choose a spot inside the chunk (avoid edges)
        int usable = CHUNK_SIZE - edgePadding * 2;
        if (usable <= 1) return; // need room for slope sampling

        // --- Tuning knobs ---
        final int attemptsPerChunk = 12;
        final int maxSlope = 2;          // max height delta within 2x2
        final int minY = 40;             // don't place deep underground
        final int scanDownLimit = 80;    // scan down at most N blocks from the heightmap

        // ChunkGenerator uses an int seed in several APIs
        int seedInt = (int) (seed ^ (seed >>> 32));

        for (int attempt = 0; attempt < attemptsPerChunk; attempt++) {

            int localX = edgePadding + r.nextInt(usable);
            int localZ = edgePadding + r.nextInt(usable);

            // Bounds for +1 slope sample
            if (localX + 1 >= CHUNK_SIZE || localZ + 1 >= CHUNK_SIZE) continue;

            // --- Flatness check (2x2) ---
            short h00 = chunk.getHeight(localX, localZ);
            short h10 = chunk.getHeight(localX + 1, localZ);
            short h01 = chunk.getHeight(localX, localZ + 1);
            short h11 = chunk.getHeight(localX + 1, localZ + 1);

            int minH = Math.min(Math.min(h00, h10), Math.min(h01, h11));
            int maxH = Math.max(Math.max(h00, h10), Math.max(h01, h11));

            if (maxH - minH > maxSlope) continue;

            // World coords for zone lookup
            int worldX = chunk.getX() * CHUNK_SIZE + localX;
            int worldZ = chunk.getZ() * CHUNK_SIZE + localZ;

            // --- Zone lookup (stable) ---
            ZoneBiomeResult zbr;
            try {
                zbr = chunkGen.getZoneBiomeResultAt(seedInt, worldX, worldZ);
            } catch (Exception e) {
                if (DEBUG_WORLDGEN) dbg("Zone lookup failed: " + e.getMessage());
                continue;
            }

            if (zbr == null) continue;
            ZoneGeneratorResult zgr = zbr.getZoneResult();
            if (zgr == null) continue;

            Zone zone = zgr.getZone();
            if (zone == null) continue;

            int zoneId = zone.id();

            // 1 per zone
            if (ZONES_WITH_DUNGEON.contains(zoneId)) continue;

            // --- Scan down to find ground (avoid floating) ---
            int y = minH;
            int scanned = 0;
            while (y > minY && scanned < scanDownLimit) {
                int blockId = chunk.getBlock(localX, y, localZ);
                if (blockId != com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.EMPTY_ID) {
                    break;
                }
                y--;
                scanned++;
            }
            if (y <= minY) continue;

            int worldY = y + 1;

            // Reserve zone + chunk atomically
            if (!ZONES_WITH_DUNGEON.add(zoneId)) continue;

            if (!CHUNKS_WITH_DUNGEON.add(cKey)) {
                // Rare: chunk already claimed concurrently; release zone reservation
                ZONES_WITH_DUNGEON.remove(zoneId);
                continue;
            }

            Vector3i placePos = new Vector3i(worldX, worldY, worldZ);

            if (DEBUG_WORLDGEN) {
                dbg("SPAWN zoneId=" + zoneId + " (" + zone.name() + ") at "
                        + worldX + "," + worldY + "," + worldZ
                        + " chunk=" + chunk.getX() + "," + chunk.getZ()
                        + " zonesClaimed=" + ZONES_WITH_DUNGEON.size());
            }

            // World edits must happen on the world thread
            world.execute(() -> selection.placeNoReturn(world, placePos, world.getEntityStore().getStore()));
            return;
        }
    }
}