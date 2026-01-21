package com.ruinbound.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public final class SpawnRoomCommand extends AbstractPlayerCommand {

    private static final String PREFAB_FILE = "yor_dungeon_room.prefab.json";

    public SpawnRoomCommand() {
        super("spawnroom", "Spawn the dungeon room prefab");
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());

        Vector3i pos = transform.getPosition().toVector3i();
        pos.add(0, 0, 12);

        world.execute(() -> {
            BlockSelection selection = PrefabStore.get().getServerPrefab(PREFAB_FILE);

            if (selection == null) {
                player.sendMessage(Message.raw("Prefab not found in server folder: " + PREFAB_FILE));
                return;
            }

            selection.placeNoReturn(world, pos, store);
            player.sendMessage(Message.raw("Spawned " + PREFAB_FILE + " at " + pos));
        });
    }
}
