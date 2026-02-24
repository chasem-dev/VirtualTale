package dev.chasem.hg.virtualtale.emulator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe double buffer for transferring frame data from the emulator thread
 * to the render thread. The emulator thread writes frames, and the render thread
 * reads the latest available frame.
 */
public class FrameBuffer {

    public static final int GB_WIDTH = 160;
    public static final int GB_HEIGHT = 144;
    public static final int PIXEL_COUNT = GB_WIDTH * GB_HEIGHT;

    private final ReentrantLock lock = new ReentrantLock();
    private final int[] buffer = new int[PIXEL_COUNT];
    private final AtomicLong frameCount = new AtomicLong(0);

    /**
     * Submits a new frame from the emulator thread.
     * Copies the pixel data into the internal buffer under lock.
     *
     * @param pixels RGB pixel data (160x144 = 23040 ints)
     */
    public void submitFrame(@Nonnull int[] pixels) {
        lock.lock();
        try {
            System.arraycopy(pixels, 0, buffer, 0, Math.min(pixels.length, PIXEL_COUNT));
            frameCount.incrementAndGet();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets the latest frame, copying into the provided destination array.
     * Returns true if a new frame was available since the last call with the given counter.
     *
     * @param dest            destination array (must be at least PIXEL_COUNT)
     * @param lastFrameCount  the frame count from the caller's last read
     * @return the current frame count, or -1 if no new frame since lastFrameCount
     */
    public long getLatestFrame(@Nonnull int[] dest, long lastFrameCount) {
        long current = frameCount.get();
        if (current == lastFrameCount) {
            return -1;
        }

        lock.lock();
        try {
            System.arraycopy(buffer, 0, dest, 0, PIXEL_COUNT);
            return frameCount.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the current frame count. Useful for checking if new frames are available
     * without copying data.
     */
    public long getFrameCount() {
        return frameCount.get();
    }
}
