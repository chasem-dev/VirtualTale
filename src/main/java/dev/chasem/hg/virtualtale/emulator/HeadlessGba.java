package dev.chasem.hg.virtualtale.emulator;

import com.hypixel.hytale.logger.HytaleLogger;
import ygba.Agent;
import ygba.memory.IORegMemory;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;

/**
 * Wraps BooYahGBA's {@link Agent} class for headless GBA emulation.
 * Runs the emulation loop on a dedicated thread at ~59.7 FPS and
 * pushes frames into a shared {@link FrameBuffer}.
 *
 * BooYahGBA outputs pixels in ARGB format (0xFFRRGGBB). The rendering
 * pipeline's ColorMapper handles conversion to Hytale's RGBA format.
 * Since ColorMapper does {@code (pixel << 8) | 0xFF}, the 0xFF alpha
 * byte shifts out and gets re-added at the bottom — works correctly.
 */
public class HeadlessGba implements EmulatorBackend {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final int DISPLAY_WIDTH = 240;
    private static final int DISPLAY_HEIGHT = 160;
    private static final int PIXEL_COUNT = DISPLAY_WIDTH * DISPLAY_HEIGHT;

    /** GBA runs at ~59.7275 FPS. */
    private static final long FRAME_NANOS = 16_742_706L;

    private final File romFile;
    private final File biosFile;
    private final FrameBuffer frameBuffer;

    private Agent agent;
    private Thread emulatorThread;
    private volatile boolean running;

    // Reusable buffer to avoid per-frame allocation
    private final int[] pixelBuffer = new int[PIXEL_COUNT];

    public HeadlessGba(@Nonnull File romFile, @Nonnull File biosFile, @Nonnull FrameBuffer frameBuffer) {
        this.romFile = romFile;
        this.biosFile = biosFile;
        this.frameBuffer = frameBuffer;
    }

    @Override
    public int getDisplayWidth() {
        return DISPLAY_WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return DISPLAY_HEIGHT;
    }

    @Override
    public void start() throws IOException {
        if (running) {
            throw new IllegalStateException("GBA emulator is already running");
        }

        if (!biosFile.exists()) {
            throw new IOException("GBA BIOS not found: " + biosFile.getAbsolutePath()
                    + ". Place gba_bios.bin in the plugin data directory under gba/.");
        }

        LOGGER.atInfo().log("[VT] Loading GBA ROM: %s (BIOS: %s)", romFile.getName(), biosFile.getName());

        agent = new Agent(biosFile.getAbsolutePath(), romFile.getAbsolutePath());

        running = true;
        emulatorThread = new Thread(this::runLoop, "VirtualTale-GBA-" + romFile.getName());
        emulatorThread.setDaemon(true);
        emulatorThread.start();

        LOGGER.atInfo().log("[VT] GBA emulator started for ROM: %s", romFile.getName());
    }

    @Override
    public void stop() {
        running = false;
        if (agent != null) {
            agent.stop();
            agent = null;
        }
        if (emulatorThread != null) {
            emulatorThread.interrupt();
            try {
                emulatorThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        LOGGER.atInfo().log("[VT] GBA emulator stopped for ROM: %s", romFile.getName());
    }

    @Override
    public void pressButton(@Nonnull EmulatorButton button) {
        int mask = mapButton(button);
        if (mask != 0 && agent != null) {
            agent.pressButton(mask);
        }
    }

    @Override
    public void releaseButton(@Nonnull EmulatorButton button) {
        int mask = mapButton(button);
        if (mask != 0 && agent != null) {
            agent.releaseButton(mask);
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Nonnull
    @Override
    public String getRomName() {
        return romFile.getName();
    }

    private static int mapButton(@Nonnull EmulatorButton button) {
        return switch (button) {
            case A -> IORegMemory.BTN_A;
            case B -> IORegMemory.BTN_B;
            case SELECT -> IORegMemory.BTN_SELECT;
            case START -> IORegMemory.BTN_START;
            case RIGHT -> IORegMemory.BTN_RIGHT;
            case LEFT -> IORegMemory.BTN_LEFT;
            case UP -> IORegMemory.BTN_UP;
            case DOWN -> IORegMemory.BTN_DOWN;
            case R -> IORegMemory.BTN_R;
            case L -> IORegMemory.BTN_L;
        };
    }

    private void runLoop() {
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                long frameStart = System.nanoTime();

                agent.runOneFrame();

                // Copy pixels from the agent into our buffer and submit
                int[] agentPixels = agent.getPixels();
                System.arraycopy(agentPixels, 0, pixelBuffer, 0,
                        Math.min(agentPixels.length, PIXEL_COUNT));
                frameBuffer.submitFrame(pixelBuffer);

                // Frame pacing
                long elapsed = System.nanoTime() - frameStart;
                long sleepNanos = FRAME_NANOS - elapsed;
                if (sleepNanos > 1_000_000) {
                    Thread.sleep(sleepNanos / 1_000_000, (int) (sleepNanos % 1_000_000));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (running) {
                LOGGER.atWarning().log("[VT] GBA emulator error: %s", e.getMessage());
            }
        } finally {
            running = false;
        }
    }
}
