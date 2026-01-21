package com.ruinbound;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.ruinbound.commands.TestCommand;
import com.ruinbound.commands.SpawnRoomCommand;
import com.ruinbound.worldgen.DungeonRoomWorldgen;

import javax.annotation.Nonnull;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

public final class Ruinbound extends JavaPlugin
{

    public Ruinbound(@Nonnull JavaPluginInit init)
    {
        super(init);
    }

    private static final String PREFAB_FILE = "yor_dungeon_room.prefab.json";
    private static final String RESOURCE_PATH = "Server/Prefabs/" + PREFAB_FILE;
    private DungeonRoomWorldgen dungeonRoomWorldgen;

    @Override
    protected void setup()
    {
        getLogger().at(Level.INFO).log("Ruinbound enabled!");

        exportPrefabIfMissing(PREFAB_FILE, RESOURCE_PATH);

        dungeonRoomWorldgen = new DungeonRoomWorldgen("yor_dungeon_room.prefab.json", 50, 3);

        getEventRegistry().registerGlobal(ChunkPreLoadProcessEvent.class, dungeonRoomWorldgen::onChunkPreLoad);

        getCommandRegistry().registerCommand(new TestCommand());
        getCommandRegistry().registerCommand(new SpawnRoomCommand());
    }

    @Override
    protected void shutdown() {
        getLogger().at(Level.INFO).log("Ruinbound disabled!");
    }

    private void exportPrefabIfMissing(String fileName, String resourcePath) {
        try {
            Path serverPrefabDir = PrefabStore.get().getServerPrefabsPath();
            Files.createDirectories(serverPrefabDir);

            Path out = serverPrefabDir.resolve(fileName);
            if (Files.exists(out)) {
                return;
            }

            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (in == null) {
                    getLogger().at(Level.WARNING).log("Prefab resource not found in JAR: " + resourcePath);
                    return;
                }
                Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                getLogger().at(Level.INFO).log("Exported prefab to server folder: " + out);
            }
        } catch (Exception e) {
            getLogger().at(Level.WARNING).withCause(e).log("Failed to export prefab");
        }
    }
}