package com.ruinbound.worldgen;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;

import java.util.Random;

/**
 * Spawns a prefab room on the surface with a low chance per newly-generated chunk.
 */
public final class DungeonRoomWorldgen {

    private final String prefabFile;          // e.g. "yor_dungeon_room.prefab.json"
    private final int chancePerChunk;         // e.g. 2500 => ~1 per 2500 chunks
    private final int edgePadding;            // keep placement away from chunk edges

    private static final int CHUNK_SIZE = 16;

    public DungeonRoomWorldgen(String prefabFile, int chancePerChunk, int edgePadding) {
        this.prefabFile = prefabFile;
        this.chancePerChunk = Math.max(1, chancePerChunk);
        this.edgePadding = Math.max(0, edgePadding);
    }

    /** Call this from your plugin when you receive the ChunkPreLoadProcessEvent. */
    public void onChunkPreLoad(ChunkPreLoadProcessEvent event) {
        if (!event.isNewlyGenerated()) return;

        WorldChunk chunk = event.getChunk();
        World world = chunk.getWorld();

        // Deterministic RNG per chunk (stable distribution for a given seed)
        long seed = world.getWorldConfig().getSeed();
        long mixed = seed
                ^ (chunk.getX() * 341873128712L)
                ^ (chunk.getZ() * 132897987541L);

        Random r = new Random(mixed);

        // Rarity gate
        if (r.nextInt(chancePerChunk) != 0) return;

        // Choose a spot inside the chunk (avoid edges)
        int usable = CHUNK_SIZE - edgePadding * 2;
        if (usable <= 0) return;

        int localX = edgePadding + r.nextInt(usable);
        int localZ = edgePadding + r.nextInt(usable);

        // Surface height at this X/Z inside the chunk
        short surfaceY = chunk.getHeight(localX, localZ);

        // Convert chunk-local to world coords
        int worldX = chunk.getX() * CHUNK_SIZE + localX;
        int worldZ = chunk.getZ() * CHUNK_SIZE + localZ;

        // Place 1 block above surface so it sits on top
        int worldY = surfaceY + 1;

        BlockSelection selection = PrefabStore.get().getServerPrefab(prefabFile);
        if (selection == null) return; // don't spam logs during worldgen

        Vector3i placePos = new Vector3i(worldX, worldY, worldZ);

        // World edits must happen on the world thread
        world.execute(() -> selection.placeNoReturn(world, placePos, world.getEntityStore().getStore()));
    }
}
