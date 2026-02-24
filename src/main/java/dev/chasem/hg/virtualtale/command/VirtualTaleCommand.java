package dev.chasem.hg.virtualtale.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.entities.Player;
import dev.chasem.hg.virtualtale.VirtualTaleConfig;
import dev.chasem.hg.virtualtale.emulator.EmulatorSession;
import dev.chasem.hg.virtualtale.emulator.EmulatorSessionManager;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;

/**
 * Command handler for /vt (VirtualTale).
 * Usage: /vt <subcommand> [args]
 * Subcommands: start <rom>, stop, list, roms, mapscale <n>
 */
public class VirtualTaleCommand extends AbstractPlayerCommand {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final EmulatorSessionManager sessionManager;
    private final VirtualTaleConfig config;

    @Nonnull
    private final RequiredArg<String> subcommandArg;
    @Nonnull
    private final DefaultArg<String> romArg;

    public VirtualTaleCommand(@Nonnull EmulatorSessionManager sessionManager, @Nonnull VirtualTaleConfig config) {
        super("vt", "VirtualTale Game Boy emulator");
        this.sessionManager = sessionManager;
        this.config = config;
        this.subcommandArg = withRequiredArg("subcommand", "start/stop/list/roms/mapscale", ArgTypes.STRING);
        this.romArg = withDefaultArg("rom", "ROM filename or value", ArgTypes.STRING, "", "");
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        String subcommand = subcommandArg.get(context).toLowerCase();
        String value = romArg.get(context);

        switch (subcommand) {
            case "start" -> handleStart(playerRef, value.isBlank() ? null : value);
            case "stop" -> handleStop(store, ref, playerRef, world);
            case "list" -> handleList(playerRef);
            case "roms" -> handleRoms(playerRef);
            case "mapscale" -> handleMapScale(playerRef, value);
            default -> sendUsage(playerRef);
        }
    }

    private void handleStart(@Nonnull PlayerRef playerRef, String romName) {
        if (romName == null || romName.isBlank()) {
            playerRef.sendMessage(Message.raw("Usage: /vt start --rom=<filename>"));
            return;
        }

        if (sessionManager.getSession(playerRef.getUuid()) != null) {
            playerRef.sendMessage(Message.raw("You already have an active session. Use /vt stop first."));
            return;
        }

        try {
            sessionManager.startSession(playerRef.getUuid(), playerRef, romName.trim());
            playerRef.sendMessage(Message.raw("VirtualTale started! Open your map (M) to see the Game Boy display."));
            playerRef.sendMessage(Message.raw("Controls: 1=UP 2=DOWN 3=LEFT 4=RIGHT 5=A 6=B 7=START 8=SELECT"));
        } catch (Exception e) {
            playerRef.sendMessage(Message.raw("Failed to start: " + e.getMessage()));
            LOGGER.atWarning().log("[VT] Failed to start session for %s: %s", playerRef.getUsername(), e.getMessage());
        }
    }

    private void handleStop(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                            @Nonnull PlayerRef playerRef, @Nonnull World world) {
        EmulatorSession session = sessionManager.getSession(playerRef.getUuid());
        if (session == null) {
            playerRef.sendMessage(Message.raw("You don't have an active session."));
            return;
        }

        // Stop session (blocks until render scheduler is fully terminated)
        sessionManager.stopSession(playerRef.getUuid());

        // Reset the server-side WorldMapTracker so terrain chunks get re-sent.
        // clear() wipes the loaded set + sentViewRadius and sends ClearWorldMap,
        // then sendSettings() re-sends map config so the client knows the map is active.
        // On the next WorldMapTracker tick, sentViewRadius=0 triggers terrain re-loading.
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.getWorldMapTracker().clear();
                player.getWorldMapTracker().sendSettings(world);
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[VT] Failed to reset world map for %s: %s", playerRef.getUsername(), e.getMessage());
        }

        playerRef.sendMessage(Message.raw("VirtualTale stopped. Map will refresh shortly."));
    }

    private void handleList(@Nonnull PlayerRef playerRef) {
        Collection<EmulatorSession> sessions = sessionManager.getAllSessions();
        if (sessions.isEmpty()) {
            playerRef.sendMessage(Message.raw("No active VirtualTale sessions."));
            return;
        }

        playerRef.sendMessage(Message.raw("Active sessions (" + sessions.size() + "):"));
        for (EmulatorSession session : sessions) {
            playerRef.sendMessage(Message.raw("  - " + session.getPlayerId() + " -> " + session.getRomName()));
        }
    }

    private void handleRoms(@Nonnull PlayerRef playerRef) {
        List<String> roms = sessionManager.listAvailableRoms();
        if (roms.isEmpty()) {
            playerRef.sendMessage(Message.raw("No ROMs found. Place .gb/.gbc files in the roms directory."));
            return;
        }

        playerRef.sendMessage(Message.raw("Available ROMs (" + roms.size() + "):"));
        for (String rom : roms) {
            playerRef.sendMessage(Message.raw("  - " + rom));
        }
    }

    private void handleMapScale(@Nonnull PlayerRef playerRef, String value) {
        if (value.isBlank()) {
            playerRef.sendMessage(Message.raw("Current map scale: " + config.getMapScale()));
            playerRef.sendMessage(Message.raw("Usage: /vt mapscale --rom=<1-8>"));
            return;
        }

        int scale;
        try {
            scale = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            playerRef.sendMessage(Message.raw("Invalid number: " + value));
            return;
        }

        if (scale < 1 || scale > 8) {
            playerRef.sendMessage(Message.raw("Map scale must be between 1 and 8."));
            return;
        }

        config.setMapScale(scale);
        playerRef.sendMessage(Message.raw("Map scale set to " + scale + ". Restart your session (/vt stop then /vt start) to apply."));

        // Auto-restart if session is active
        EmulatorSession session = sessionManager.getSession(playerRef.getUuid());
        if (session != null) {
            String romName = session.getRomName();
            sessionManager.stopSession(playerRef.getUuid());
            try {
                sessionManager.startSession(playerRef.getUuid(), playerRef, romName);
                playerRef.sendMessage(Message.raw("Session restarted with scale " + scale + "."));
            } catch (Exception e) {
                playerRef.sendMessage(Message.raw("Failed to restart session: " + e.getMessage()));
            }
        }
    }

    private void sendUsage(@Nonnull PlayerRef playerRef) {
        playerRef.sendMessage(Message.raw("Usage: /vt <subcommand> [--rom=<value>]"));
        playerRef.sendMessage(Message.raw("  start --rom=<file>  - Start emulator (e.g. --rom=Tetris.gb)"));
        playerRef.sendMessage(Message.raw("  stop                - Stop your current session"));
        playerRef.sendMessage(Message.raw("  list                - List active sessions"));
        playerRef.sendMessage(Message.raw("  roms                - List available ROM files"));
        playerRef.sendMessage(Message.raw("  mapscale --rom=<n>  - Set display size 1-8 (default: 4)"));
    }
}
