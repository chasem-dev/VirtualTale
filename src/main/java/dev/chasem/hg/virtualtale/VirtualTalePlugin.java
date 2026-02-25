package dev.chasem.hg.virtualtale;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import dev.chasem.hg.virtualtale.command.VirtualTaleCommand;
import dev.chasem.hg.virtualtale.emulator.EmulatorSessionManager;
import dev.chasem.hg.virtualtale.input.HotbarPacketFilter;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * VirtualTale - A Game Boy / GBA emulator rendered on Hytale's world map.
 * Each player gets their own emulator instance. The display is streamed
 * as map chunks, and input is captured via hotbar key presses (1-8).
 */
public class VirtualTalePlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static VirtualTalePlugin instance;

    private VirtualTaleConfig config;
    private EmulatorSessionManager sessionManager;
    private HotbarPacketFilter hotbarFilter;
    private PacketFilter registeredFilter;

    public VirtualTalePlugin(JavaPluginInit init) {
        super(init);
        instance = this;

        // Load config (also creates roms/ directory inside data folder)
        config = VirtualTaleConfig.load(getDataDirectory());

        LOGGER.atInfo().log("[VT] VirtualTale initializing...");
    }

    public static VirtualTalePlugin getInstance() {
        return instance;
    }

    @Override
    protected void setup() {
        // Create session manager
        sessionManager = new EmulatorSessionManager(config);

        // Register packet filter for hotbar key presses (all 8 GB buttons)
        hotbarFilter = new HotbarPacketFilter(sessionManager, config.getButtonHoldMs());
        registeredFilter = PacketAdapters.registerInbound(hotbarFilter);
        LOGGER.atInfo().log("[VT] HotbarPacketFilter registered");

        // Register command
        getCommandRegistry().registerCommand(new VirtualTaleCommand(sessionManager, config));
        LOGGER.atInfo().log("[VT] Commands registered");

        // Handle player disconnect - clean up debounce state and stop their session
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            UUID playerId = event.getPlayerRef().getUuid();
            hotbarFilter.clearPlayer(playerId);
            sessionManager.stopSession(playerId);
        });

        LOGGER.atInfo().log("[VT] VirtualTale setup complete!");
        printBanner();
    }

    @Override
    protected void shutdown() {
        if (registeredFilter != null) {
            PacketAdapters.deregisterInbound(registeredFilter);
        }
        if (hotbarFilter != null) {
            hotbarFilter.shutdown();
        }
        if (sessionManager != null) {
            sessionManager.shutdownAll();
        }
        LOGGER.atInfo().log("[VT] VirtualTale shut down");
    }

    @Nonnull
    public EmulatorSessionManager getSessionManager() {
        return sessionManager;
    }

    private void printBanner() {
        String[] banner = {
            "",
            "    ┌─────────────────────────────────┐",
            "    │  ┌─────────────────────────────┐ │",
            "    │  │                             │ │",
            "    │  │      V I R T U A L          │ │",
            "    │  │        T A L E              │ │",
            "    │  │                             │ │",
            "    │  │      GB / GBA on Map        │ │",
            "    │  └─────────────────────────────┘ │",
            "    │          ___   ___               │",
            "    │    +    | B | | A |              │",
            "    │   +++   |___| |___|              │",
            "    │    +                             │",
            "    │      [SELECT] [START]            │",
            "    │                                  │",
            "    │    /vt start <rom> to play!      │",
            "    └──────────────────────────────────┘",
            ""
        };
        for (String line : banner) {
            LOGGER.atInfo().log("[VT] %s", line);
        }
    }
}
