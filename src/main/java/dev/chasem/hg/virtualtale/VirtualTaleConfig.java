package dev.chasem.hg.virtualtale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuration for the VirtualTale plugin.
 */
public class VirtualTaleConfig {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private int mapOriginChunkX = -3;
    private int mapOriginChunkZ = -3;
    private int renderFps = 20;
    private double anchorX = 0.0;
    private double anchorY = 70.0;
    private double anchorZ = 0.0;
    private String anchorWorldName = "default";
    private int mapScale = 4;
    private long buttonHoldMs = 200;

    private transient Path configPath;
    private transient Path romDirectory;
    private transient Path biosDirectory;
    private transient Path savesDirectory;

    public static VirtualTaleConfig load(@Nonnull Path dataDirectory) {
        Path path = dataDirectory.resolve("config.json");
        VirtualTaleConfig config;
        if (Files.exists(path)) {
            try {
                String json = Files.readString(path);
                config = GSON.fromJson(json, VirtualTaleConfig.class);
                LOGGER.atInfo().log("[VT] Config loaded from %s", path);
            } catch (Exception e) {
                LOGGER.atWarning().log("[VT] Failed to load config: %s, using defaults", e.getMessage());
                config = new VirtualTaleConfig();
            }
        } else {
            config = new VirtualTaleConfig();
            LOGGER.atInfo().log("[VT] Using default config");
        }
        config.configPath = path;
        config.romDirectory = dataDirectory.resolve("roms");
        config.biosDirectory = dataDirectory.resolve("bios");
        config.savesDirectory = dataDirectory.resolve("saves");

        // Ensure directories exist
        try {
            Files.createDirectories(config.romDirectory);
            Files.createDirectories(config.biosDirectory);
            Files.createDirectories(config.savesDirectory);
        } catch (IOException e) {
            LOGGER.atWarning().log("[VT] Failed to create directories: %s", e.getMessage());
        }

        config.save();
        return config;
    }

    public void save() {
        if (configPath == null) return;
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(this));
        } catch (IOException e) {
            LOGGER.atWarning().log("[VT] Failed to save config: %s", e.getMessage());
        }
    }

    public String getRomDirectory() { return romDirectory.toString(); }
    public String getSavesDirectory() { return savesDirectory.toString(); }
    public int getMapOriginChunkX() { return mapOriginChunkX; }
    public int getMapOriginChunkZ() { return mapOriginChunkZ; }
    public int getRenderFps() { return renderFps; }
    public double getAnchorX() { return anchorX; }
    public double getAnchorY() { return anchorY; }
    public double getAnchorZ() { return anchorZ; }
    public String getAnchorWorldName() { return anchorWorldName; }
    public int getMapScale() { return mapScale; }
    public void setMapScale(int mapScale) { this.mapScale = Math.max(1, mapScale); save(); }
    public long getButtonHoldMs() { return buttonHoldMs; }

    /**
     * Returns the expected GBA BIOS file location.
     * The BIOS should be placed at {@code <dataDir>/bios/gba_bios.bin}.
     */
    public java.io.File getGbaBiosFile() {
        return biosDirectory.resolve("gba_bios.bin").toFile();
    }
}
