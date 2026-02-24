package dev.chasem.hg.virtualtale.display;

import javax.annotation.Nonnull;
import java.util.Arrays;

/**
 * Compares the current frame's chunk pixels against the previous frame
 * and identifies which chunks have changed. Only changed chunks
 * need to be sent over the network.
 */
public class DeltaCompressor {

    private final int gridWidth;
    private final int gridHeight;
    private final int totalChunks;
    private final int chunkPixelCount;

    // Previous frame's chunk data for comparison
    private final int[][] previousChunks;

    public DeltaCompressor(int gridWidth, int gridHeight, int chunkPixelCount) {
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.totalChunks = gridWidth * gridHeight;
        this.chunkPixelCount = chunkPixelCount;
        this.previousChunks = new int[totalChunks][];
    }

    /**
     * Compares a chunk's pixel data against the previous frame.
     * Returns true if the chunk has changed and needs to be sent.
     *
     * @param chunkIndex index in the grid (row * gridWidth + col)
     * @param pixels     the current chunk's ARGB pixel data
     * @return true if the chunk differs from the previous frame
     */
    public boolean hasChanged(int chunkIndex, @Nonnull int[] pixels) {
        if (chunkIndex < 0 || chunkIndex >= totalChunks) {
            return true;
        }

        int[] previous = previousChunks[chunkIndex];
        if (previous == null) {
            // First frame for this chunk - always send
            previousChunks[chunkIndex] = Arrays.copyOf(pixels, chunkPixelCount);
            return true;
        }

        if (!Arrays.equals(previous, 0, chunkPixelCount, pixels, 0, chunkPixelCount)) {
            System.arraycopy(pixels, 0, previous, 0, chunkPixelCount);
            return true;
        }

        return false;
    }

    /**
     * Resets all previous chunk data, forcing a full redraw on the next frame.
     */
    public void reset() {
        Arrays.fill(previousChunks, null);
    }

    public int getGridWidth() {
        return gridWidth;
    }

    public int getGridHeight() {
        return gridHeight;
    }

    public int getTotalChunks() {
        return totalChunks;
    }
}
