package dev.chasem.hg.virtualtale.emulator;

import com.hypixel.hytale.logger.HytaleLogger;
import eu.rekawek.coffeegb.Gameboy;
import eu.rekawek.coffeegb.controller.Button;
import eu.rekawek.coffeegb.controller.ButtonPressEvent;
import eu.rekawek.coffeegb.controller.ButtonReleaseEvent;
import eu.rekawek.coffeegb.events.EventBus;
import eu.rekawek.coffeegb.gpu.Display;
import eu.rekawek.coffeegb.memory.cart.Cartridge;
import eu.rekawek.coffeegb.serial.SerialEndpoint;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;

/**
 * Wraps coffee-gb's Gameboy class for headless (no-GUI) server-side use.
 * Runs the emulator loop on a daemon thread and captures frame output
 * into a FrameBuffer for the rendering pipeline.
 */
public class HeadlessGameboy {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final FrameBuffer frameBuffer;
    private final File romFile;

    private Gameboy gameboy;
    private EventBus eventBus;
    private Thread emulatorThread;
    private volatile boolean running;

    // Temporary buffer for converting frames
    private final int[] rgbBuffer = new int[FrameBuffer.PIXEL_COUNT];

    public HeadlessGameboy(@Nonnull File romFile, @Nonnull FrameBuffer frameBuffer) {
        this.romFile = romFile;
        this.frameBuffer = frameBuffer;
    }

    /**
     * Starts the emulator. Loads the ROM, initializes the Gameboy, registers
     * frame listeners, and starts the emulator thread.
     */
    public void start() throws IOException {
        if (running) {
            throw new IllegalStateException("Emulator is already running");
        }

        LOGGER.atInfo().log("[VT] Loading ROM: %s", romFile.getName());

        Cartridge cartridge = new Cartridge(romFile);
        gameboy = new Gameboy(cartridge);
        eventBus = new EventBus();

        // Register frame listeners on the event bus
        eventBus.register(this::onDmgFrame, Display.DmgFrameReadyEvent.class);
        eventBus.register(this::onGbcFrame, Display.GbcFrameReadyEvent.class);

        // Initialize the gameboy with our event bus (no-op serial, no console)
        gameboy.init(eventBus, SerialEndpoint.NULL_ENDPOINT, null);

        running = true;
        emulatorThread = new Thread(this::runLoop, "VirtualTale-Emulator-" + romFile.getName());
        emulatorThread.setDaemon(true);
        emulatorThread.start();

        LOGGER.atInfo().log("[VT] Emulator started for ROM: %s", romFile.getName());
    }

    /**
     * Stops the emulator and waits for the thread to finish.
     */
    public void stop() {
        running = false;
        if (gameboy != null) {
            gameboy.stop();
            gameboy = null;
        }
        if (emulatorThread != null) {
            emulatorThread.interrupt();
            try {
                emulatorThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        eventBus = null;
        LOGGER.atInfo().log("[VT] Emulator stopped for ROM: %s", romFile.getName());
    }

    /**
     * Presses a Game Boy button.
     */
    public void pressButton(@Nonnull Button button) {
        if (eventBus != null) {
            eventBus.post(new ButtonPressEvent(button));
        }
    }

    /**
     * Releases a Game Boy button.
     */
    public void releaseButton(@Nonnull Button button) {
        if (eventBus != null) {
            eventBus.post(new ButtonReleaseEvent(button));
        }
    }

    public boolean isRunning() {
        return running;
    }

    @Nonnull
    public String getRomName() {
        return romFile.getName();
    }

    /**
     * Game Boy CPU runs at 4,194,304 Hz. Each tick() advances 4 clocks (1 M-cycle).
     * A full frame takes 69,905 ticks (~59.7 FPS).
     * We pace the loop to real-time by sleeping after each emulated frame.
     */
    private static final int TICKS_PER_FRAME = 69_905;
    private static final long FRAME_NANOS = 16_742_706L; // 1_000_000_000 / 59.7275

    private void runLoop() {
        try {
            long frameStartNanos = System.nanoTime();
            int tickCount = 0;

            while (running && !Thread.currentThread().isInterrupted()) {
                gameboy.tick();
                tickCount++;

                if (tickCount >= TICKS_PER_FRAME) {
                    tickCount = 0;

                    // Wait until real-time catches up to emulated time
                    long elapsed = System.nanoTime() - frameStartNanos;
                    long sleepNanos = FRAME_NANOS - elapsed;
                    if (sleepNanos > 1_000_000) { // >1ms worth of waiting
                        Thread.sleep(sleepNanos / 1_000_000, (int) (sleepNanos % 1_000_000));
                    }

                    frameStartNanos = System.nanoTime();
                }
            }
        } catch (InterruptedException e) {
            // Normal shutdown
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (running) {
                LOGGER.atWarning().log("[VT] Emulator error: %s", e.getMessage());
            }
        } finally {
            running = false;
        }
    }

    private void onDmgFrame(Display.DmgFrameReadyEvent event) {
        event.toRgb(rgbBuffer, false);
        frameBuffer.submitFrame(rgbBuffer);
    }

    private void onGbcFrame(Display.GbcFrameReadyEvent event) {
        event.toRgb(rgbBuffer);
        frameBuffer.submitFrame(rgbBuffer);
    }
}
