package dev.chasem.hg.virtualtale.emulator;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.worldmap.ClearWorldMap;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMap;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.chasem.hg.virtualtale.display.ColorMapper;
import dev.chasem.hg.virtualtale.display.MapDisplayRenderer;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Per-player session that ties together an emulator backend, a frame buffer,
 * a map renderer, and a scheduled render loop that pushes frames to the player.
 *
 * Works with any {@link EmulatorBackend} (Game Boy, GBA, etc.).
 */
public class EmulatorSession {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final UUID playerId;
    private final PlayerRef playerRef;
    private final EmulatorBackend backend;
    private final FrameBuffer frameBuffer;
    private final MapDisplayRenderer renderer;
    private final ScheduledExecutorService renderScheduler;

    private ScheduledFuture<?> renderTask;
    private long lastFrameCount = -1;

    // Reusable frame buffers (avoid allocation per frame)
    private final int[] readBuffer;
    private final int[] rgbaBuffer;

    public EmulatorSession(
            @Nonnull UUID playerId,
            @Nonnull PlayerRef playerRef,
            @Nonnull EmulatorBackend backend,
            @Nonnull FrameBuffer frameBuffer,
            double playerWorldX,
            double playerWorldZ,
            int mapScale
    ) {
        this.playerId = playerId;
        this.playerRef = playerRef;
        this.backend = backend;
        this.frameBuffer = frameBuffer;
        this.renderer = new MapDisplayRenderer(playerWorldX, playerWorldZ, mapScale,
                backend.getDisplayWidth(), backend.getDisplayHeight());
        this.renderScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "VT-Render-" + playerId.toString().substring(0, 8));
            t.setDaemon(true);
            return t;
        });

        int pixelCount = frameBuffer.getPixelCount();
        this.readBuffer = new int[pixelCount];
        this.rgbaBuffer = new int[pixelCount];
    }

    /**
     * Starts the emulator and begins the render loop at the specified FPS.
     */
    public void start(int renderFps) throws IOException {
        // Clear the player's map so our display is the only thing visible
        try {
            playerRef.getPacketHandler().write(new ClearWorldMap());
            LOGGER.atInfo().log("[VT] Cleared world map for %s", playerId);
        } catch (Exception e) {
            LOGGER.atWarning().log("[VT] Failed to clear map for %s: %s", playerId, e.getMessage());
        }

        backend.start();

        long periodMs = 1000L / renderFps;
        renderTask = renderScheduler.scheduleAtFixedRate(
                this::renderTick,
                periodMs,
                periodMs,
                TimeUnit.MILLISECONDS
        );

        LOGGER.atInfo().log("[VT] Session started for %s - ROM: %s, %d FPS",
                playerId, backend.getRomName(), renderFps);
    }

    /**
     * Stops the emulator and render loop. Blocks until the render scheduler
     * has fully terminated so no stale frames are sent after this returns.
     */
    public void stop() {
        if (renderTask != null) {
            renderTask.cancel(false);
        }
        renderScheduler.shutdown();
        try {
            // Wait for any in-flight render tick to finish before clearing the map
            if (!renderScheduler.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                renderScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            renderScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        backend.stop();

        LOGGER.atInfo().log("[VT] Session stopped for %s", playerId);
    }

    /**
     * Called by the render scheduler. Reads the latest frame from the buffer,
     * converts colors, renders to map chunks, and sends to the player.
     */
    private void renderTick() {
        try {
            // Check for new frame
            long newCount = frameBuffer.getLatestFrame(readBuffer, lastFrameCount);
            if (newCount == -1) {
                return; // No new frame
            }
            lastFrameCount = newCount;

            // Convert RGB -> RGBA
            ColorMapper.toRgba(readBuffer, rgbaBuffer, frameBuffer.getPixelCount());

            // Render to map chunks (delta compressed)
            UpdateWorldMap packet = renderer.renderFrame(rgbaBuffer);
            if (packet != null) {
                MapDisplayRenderer.sendToPlayer(playerRef, packet);
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[VT] Render error for %s: %s", playerId, e.getMessage());
        }
    }

    @Nonnull
    public EmulatorBackend getBackend() {
        return backend;
    }

    @Nonnull
    public UUID getPlayerId() {
        return playerId;
    }

    @Nonnull
    public String getRomName() {
        return backend.getRomName();
    }

    public boolean isRunning() {
        return backend.isRunning();
    }
}
