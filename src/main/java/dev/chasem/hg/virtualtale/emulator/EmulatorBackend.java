package dev.chasem.hg.virtualtale.emulator;

import javax.annotation.Nonnull;
import java.io.IOException;

/**
 * Abstraction over different emulator cores (Game Boy, GBA, etc.).
 * Implementations run on their own threads and push frames into a shared {@link FrameBuffer}.
 */
public interface EmulatorBackend {

    /** Native display width in pixels. */
    int getDisplayWidth();

    /** Native display height in pixels. */
    int getDisplayHeight();

    /** Starts the emulator. Loads ROM, spawns emulation thread. */
    void start() throws IOException;

    /** Stops the emulator and waits for the thread to finish. */
    void stop();

    /** Presses a button. Buttons not supported by this backend are ignored. */
    void pressButton(@Nonnull EmulatorButton button);

    /** Releases a button. Buttons not supported by this backend are ignored. */
    void releaseButton(@Nonnull EmulatorButton button);

    boolean isRunning();

    @Nonnull
    String getRomName();
}
