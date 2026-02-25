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
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings;
import dev.chasem.hg.virtualtale.VirtualTaleConfig;
import dev.chasem.hg.virtualtale.emulator.EmulatorSession;
import dev.chasem.hg.virtualtale.emulator.EmulatorSessionManager;
import dev.chasem.hg.virtualtale.emulator.RomType;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Command handler for /vt (VirtualTale).
 * Usage: /vt <subcommand> [args]
 * Subcommands: start <rom>, stop, list, roms, mapscale <n>, speed <1-8>
 */
public class VirtualTaleCommand extends AbstractPlayerCommand {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Predicate<PlayerRef> HIDE_ALL_PLAYER_MARKERS_FILTER = ignored -> true;

    private final EmulatorSessionManager sessionManager;
    private final VirtualTaleConfig config;
    private final Map<UUID, Boolean> previousShowEntityMarkersByPlayer = new ConcurrentHashMap<>();

    @Nonnull
    private final RequiredArg<String> subcommandArg;
    @Nonnull
    private final DefaultArg<String> romArg;

    public VirtualTaleCommand(@Nonnull EmulatorSessionManager sessionManager, @Nonnull VirtualTaleConfig config) {
        super("vt", "VirtualTale emulator (Game Boy / GBA)");
        this.sessionManager = sessionManager;
        this.config = config;
        this.subcommandArg = withRequiredArg("subcommand", "start/stop/list/roms/mapscale/speed", ArgTypes.STRING);
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
            case "start" -> handleStart(store, ref, playerRef, value.isBlank() ? null : value);
            case "stop" -> handleStop(store, ref, playerRef, world);
            case "list" -> handleList(playerRef);
            case "roms" -> handleRoms(playerRef);
            case "mapscale" -> handleMapScale(playerRef, value);
            case "speed" -> handleSpeed(playerRef, value);
            default -> sendUsage(playerRef);
        }
    }

    private void handleStart(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                             @Nonnull PlayerRef playerRef, String romName) {
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
            updatePlayerMarkerVisibility(store, ref, playerRef, true);
            String systemName = isGbaRom(romName) ? "GBA" : "Game Boy";
            playerRef.sendMessage(Message.raw("VirtualTale started! (" + systemName + ") Open your map (M) to see the display."));
            playerRef.sendMessage(Message.raw("Controls: 1=UP 2=DOWN 3=LEFT 4=RIGHT 5=A 6=B 7=START 8=SELECT"));
            if (isGbaRom(romName)) {
                playerRef.sendMessage(Message.raw("GBA shoulder buttons are not yet mapped to hotbar keys."));
            }
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
            updatePlayerMarkerVisibility(store, ref, playerRef, false);
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
            playerRef.sendMessage(Message.raw("No ROMs found. Place .gb/.gbc/.gba files in the roms directory."));
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

    private void handleSpeed(@Nonnull PlayerRef playerRef, String value) {
        EmulatorSession session = sessionManager.getSession(playerRef.getUuid());
        if (session == null) {
            playerRef.sendMessage(Message.raw("You don't have an active session."));
            return;
        }

        if (value.isBlank()) {
            double current = session.getBackend().getSpeedMultiplier();
            playerRef.sendMessage(Message.raw("Current speed: " + formatSpeed(current)));
            playerRef.sendMessage(Message.raw("Usage: /vt speed --rom=<1-8>"));
            return;
        }

        int speed;
        try {
            speed = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            playerRef.sendMessage(Message.raw("Invalid number: " + value));
            return;
        }

        if (speed < 1 || speed > 8) {
            playerRef.sendMessage(Message.raw("Speed must be between 1 and 8."));
            return;
        }

        session.getBackend().setSpeedMultiplier(speed);
        playerRef.sendMessage(Message.raw("Emulator speed set to " + speed + "x."));
    }

    private static String formatSpeed(double multiplier) {
        if (multiplier == (long) multiplier) {
            return (long) multiplier + "x";
        }
        return String.format("%.1fx", multiplier);
    }

    private void sendUsage(@Nonnull PlayerRef playerRef) {
        playerRef.sendMessage(Message.raw("Usage: /vt <subcommand> [--rom=<value>]"));
        playerRef.sendMessage(Message.raw("  start --rom=<file>  - Start emulator (e.g. --rom=Tetris.gb or --rom=Pokemon.gba)"));
        playerRef.sendMessage(Message.raw("  stop                - Stop your current session"));
        playerRef.sendMessage(Message.raw("  list                - List active sessions"));
        playerRef.sendMessage(Message.raw("  roms                - List available ROM files"));
        playerRef.sendMessage(Message.raw("  mapscale --rom=<n>  - Set display size 1-8 (default: 4)"));
        playerRef.sendMessage(Message.raw("  speed --rom=<1-8>   - Set emulator speed multiplier (default: 1)"));
    }

    private static boolean isGbaRom(@Nonnull String romName) {
        String lower = romName.toLowerCase();
        return lower.endsWith(".gba") || lower.endsWith(".agb");
    }

    private void updatePlayerMarkerVisibility(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                                              @Nonnull PlayerRef playerRef, boolean hidePlayerMarkers) {
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.getWorldMapTracker().setPlayerMapFilter(
                        hidePlayerMarkers ? HIDE_ALL_PLAYER_MARKERS_FILTER : null
                );
            }

            PlayerSettings settings = store.getComponent(ref, PlayerSettings.getComponentType());
            PlayerSettings effectiveSettings = settings != null ? settings : PlayerSettings.defaults();

            if (hidePlayerMarkers) {
                previousShowEntityMarkersByPlayer.put(playerRef.getUuid(), effectiveSettings.showEntityMarkers());
                if (effectiveSettings.showEntityMarkers()) {
                    store.putComponent(
                            ref,
                            PlayerSettings.getComponentType(),
                            withShowEntityMarkers(effectiveSettings, false)
                    );
                }
            } else {
                Boolean previousShowEntityMarkers = previousShowEntityMarkersByPlayer.remove(playerRef.getUuid());
                boolean showEntityMarkers = previousShowEntityMarkers != null ? previousShowEntityMarkers : true;
                if (effectiveSettings.showEntityMarkers() != showEntityMarkers) {
                    store.putComponent(
                            ref,
                            PlayerSettings.getComponentType(),
                            withShowEntityMarkers(effectiveSettings, showEntityMarkers)
                    );
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log(
                    "[VT] Failed to update map player marker visibility for %s: %s",
                    playerRef.getUsername(),
                    e.getMessage()
            );
        }
    }

    private static PlayerSettings withShowEntityMarkers(@Nonnull PlayerSettings source, boolean showEntityMarkers) {
        return new PlayerSettings(
                showEntityMarkers,
                source.armorItemsPreferredPickupLocation(),
                source.weaponAndToolItemsPreferredPickupLocation(),
                source.usableItemsItemsPreferredPickupLocation(),
                source.solidBlockItemsPreferredPickupLocation(),
                source.miscItemsPreferredPickupLocation(),
                source.creativeSettings(),
                source.hideHelmet(),
                source.hideCuirass(),
                source.hideGauntlets(),
                source.hidePants()
        );
    }
}
