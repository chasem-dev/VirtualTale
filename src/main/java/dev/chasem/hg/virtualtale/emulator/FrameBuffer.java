package dev.chasem.hg.virtualtale.emulator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe double buffer for transferring frame data from the emulator thread
 * to the render thread. The emulator thread writes frames, and the render thread
 * reads the latest available frame.
 *
 * Supports configurable resolution for different emulator backends
 * (GB = 160x144, GBA = 240x160).
 */
public class FrameBuffer {

    public static final int GB_WIDTH = 160;
    public static final int GB_HEIGHT = 144;

    private final int width;
    private final int height;
    private final int pixelCount;

    private final ReentrantLock lock = new ReentrantLock();
    private final int[] buffer;
    private final AtomicLong frameCount = new AtomicLong(0);

    public FrameBuffer(int width, int height) {
        this.width = width;
        this.height = height;
        this.pixelCount = width * height;
        this.buffer = new int[pixelCount];
    }

    /** Creates a Game Boy-sized frame buffer (160x144). */
    public FrameBuffer() {
        this(GB_WIDTH, GB_HEIGHT);
    }

    /**
     * Submits a new frame from the emulator thread.
     * Copies the pixel data into the internal buffer under lock.
     *
     * @param pixels pixel data array (must be at least pixelCount ints)
     */
    public void submitFrame(@Nonnull int[] pixels) {
        lock.lock();
        try {
            System.arraycopy(pixels, 0, buffer, 0, Math.min(pixels.length, pixelCount));
            frameCount.incrementAndGet();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets the latest frame, copying into the provided destination array.
     * Returns true if a new frame was available since the last call with the given counter.
     *
     * @param dest            destination array (must be at least pixelCount)
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
            System.arraycopy(buffer, 0, dest, 0, pixelCount);
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

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getPixelCount() {
        return pixelCount;
    }
}
