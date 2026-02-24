package dev.chasem.hg.virtualtale.emulator;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.chasem.hg.virtualtale.VirtualTaleConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all active emulator sessions. Each player can have at most one session.
 * Automatically selects the correct backend (Game Boy or GBA) based on ROM type.
 */
public class EmulatorSessionManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ConcurrentHashMap<UUID, EmulatorSession> sessions = new ConcurrentHashMap<>();
    private final VirtualTaleConfig config;

    public EmulatorSessionManager(@Nonnull VirtualTaleConfig config) {
        this.config = config;
    }

    /**
     * Starts a new emulator session for a player.
     * The backend is automatically selected based on the ROM file extension.
     *
     * @param playerId  the player's UUID
     * @param playerRef the player's reference for sending packets
     * @param romName   the ROM filename (without path)
     * @return the created session
     * @throws IOException           if the ROM cannot be loaded
     * @throws IllegalStateException if the player already has a session
     */
    @Nonnull
    public EmulatorSession startSession(
            @Nonnull UUID playerId,
            @Nonnull PlayerRef playerRef,
            @Nonnull String romName
    ) throws IOException {
        if (sessions.containsKey(playerId)) {
            throw new IllegalStateException("Player already has an active session");
        }

        File romFile = resolveRom(romName);
        if (romFile == null || !romFile.exists()) {
            throw new IOException("ROM not found: " + romName);
        }

        RomType romType = RomType.detect(romFile);
        if (romType == null) {
            throw new IOException("Unrecognized ROM type: " + romName
                    + ". Supported: .gb, .gbc, .gba, .agb");
        }

        // Create the appropriately-sized frame buffer
        FrameBuffer frameBuffer = new FrameBuffer(romType.getWidth(), romType.getHeight());

        // Create the appropriate backend
        EmulatorBackend backend;
        switch (romType) {
            case GAMEBOY -> backend = new HeadlessGameboy(romFile, frameBuffer);
            case GBA -> {
                File biosFile = config.getGbaBiosFile();
                backend = new HeadlessGba(romFile, biosFile, frameBuffer);
            }
            default -> throw new IOException("Unsupported ROM type: " + romType);
        }

        EmulatorSession session = new EmulatorSession(
                playerId,
                playerRef,
                backend,
                frameBuffer,
                config.getAnchorX(),
                config.getAnchorZ(),
                config.getMapScale()
        );

        session.start(config.getRenderFps());
        sessions.put(playerId, session);
        LOGGER.atInfo().log("[VT] Started %s session for %s with ROM: %s", romType, playerId, romName);
        return session;
    }

    /**
     * Stops and removes the session for a player.
     */
    public void stopSession(@Nonnull UUID playerId) {
        EmulatorSession session = sessions.remove(playerId);
        if (session != null) {
            session.stop();
            LOGGER.atInfo().log("[VT] Stopped session for %s", playerId);
        }
    }

    /**
     * Gets the active session for a player, or null if none.
     */
    @Nullable
    public EmulatorSession getSession(@Nonnull UUID playerId) {
        return sessions.get(playerId);
    }

    /**
     * Returns all active sessions.
     */
    @Nonnull
    public Collection<EmulatorSession> getAllSessions() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    /**
     * Stops all sessions. Called during plugin shutdown.
     */
    public void shutdownAll() {
        for (Map.Entry<UUID, EmulatorSession> entry : sessions.entrySet()) {
            entry.getValue().stop();
        }
        sessions.clear();
        LOGGER.atInfo().log("[VT] All sessions shut down");
    }

    /**
     * Lists available ROM files in the configured directory.
     * Includes .gb, .gbc, .gba, and .agb files.
     */
    @Nonnull
    public List<String> listAvailableRoms() {
        List<String> roms = new ArrayList<>();
        Path romDir = Path.of(config.getRomDirectory());
        if (!Files.isDirectory(romDir)) {
            return roms;
        }
        try (var stream = Files.list(romDir)) {
            stream.filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".gb") || name.endsWith(".gbc")
                                || name.endsWith(".gba") || name.endsWith(".agb");
                    })
                    .forEach(p -> roms.add(p.getFileName().toString()));
        } catch (IOException e) {
            LOGGER.atWarning().log("[VT] Failed to list ROMs: %s", e.getMessage());
        }
        return roms;
    }

    /**
     * Resolves a ROM name to a file. Tries exact match first, then
     * with extensions, then case-insensitive prefix search.
     */
    @Nullable
    private File resolveRom(@Nonnull String romName) {
        Path romDir = Path.of(config.getRomDirectory());

        // Exact match
        Path exact = romDir.resolve(romName);
        if (Files.exists(exact)) {
            return exact.toFile();
        }

        // Try with common extensions
        for (String ext : new String[]{".gb", ".gbc", ".gba", ".agb"}) {
            Path withExt = romDir.resolve(romName + ext);
            if (Files.exists(withExt)) {
                return withExt.toFile();
            }
        }

        // Case-insensitive prefix search
        if (Files.isDirectory(romDir)) {
            try (var stream = Files.list(romDir)) {
                return stream
                        .filter(p -> p.getFileName().toString().toLowerCase()
                                .startsWith(romName.toLowerCase()))
                        .findFirst()
                        .map(Path::toFile)
                        .orElse(null);
            } catch (IOException e) {
                return null;
            }
        }

        return null;
    }
}
