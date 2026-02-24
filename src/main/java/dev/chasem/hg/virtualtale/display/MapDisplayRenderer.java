package dev.chasem.hg.virtualtale.display;

import com.hypixel.hytale.protocol.packets.worldmap.MapChunk;
import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMap;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Splits the Game Boy's 160x144 display into a grid of map chunks
 * for rendering on Hytale's world map.
 *
 * Each map chunk covers 32 world blocks. Images are 64x64 pixels
 * (2 pixels per block), which the client stretches to fill the chunk area.
 *
 * The display is centered on the player's world position so the player
 * marker appears at the center of the Game Boy screen on the map.
 *
 * Rendering zones in world coordinates:
 *   - Display area: the Game Boy frame content
 *   - Border: a thin gray frame around the display
 *   - Padding: solid black extending beyond the border
 *
 * All colors use RGBA format (R<<24 | G<<16 | B<<8 | A) matching
 * Hytale's internal ImageBuilder.Color.pack() format.
 */
public class MapDisplayRenderer {

    public static final int GB_WIDTH = 160;
    public static final int GB_HEIGHT = 144;

    /** World blocks per map chunk (fixed by Hytale). */
    public static final int CHUNK_BLOCKS = 32;

    /** Image pixels per world block. Client stretches image to fit chunk. */
    public static final int PX_PER_BLOCK = 2;

    /** Image pixels per chunk side. */
    public static final int CHUNK_IMAGE_SIZE = CHUNK_BLOCKS * PX_PER_BLOCK; // 64

    private static final int CHUNK_IMAGE_PIXELS = CHUNK_IMAGE_SIZE * CHUNK_IMAGE_SIZE;

    /** Width of the gray border around the display, in world blocks. */
    public static final int BORDER_BLOCKS = 5;

    /** Extra chunks of solid black around the display+border. */
    public static final int PADDING_CHUNKS = 8;

    /** Dark gray border color (RGBA). */
    private static final int BORDER_COLOR = 0x2A2A2AFF;

    /** Opaque black (RGBA). */
    private static final int BLACK = 0x000000FF;

    private final DeltaCompressor compressor;

    /** World block coordinates of the display top-left corner. */
    private final int displayStartX;
    private final int displayStartZ;
    private final int mapScale;
    private final int displayBlocksW;
    private final int displayBlocksH;

    /** Grid bounds in absolute chunk coordinates. */
    private final int gridMinChunkX;
    private final int gridMinChunkZ;
    private final int gridWidth;
    private final int gridHeight;

    /** Number of display+border chunks (the "inner" grid). */
    private final int innerMinChunkX;
    private final int innerMinChunkZ;
    private final int innerMaxChunkX;
    private final int innerMaxChunkZ;

    // Reusable chunk pixel buffer
    private final int[] chunkPixels = new int[CHUNK_IMAGE_PIXELS];

    // Tracks which pure-black padding chunks have been sent (they never change)
    private boolean[] paddingSent;

    /**
     * @param playerWorldX player's world X position (display will be centered here)
     * @param playerWorldZ player's world Z position (display will be centered here)
     * @param mapScale     how many world blocks each GB pixel covers (1 = 1:1, 2 = 2x larger, etc.)
     */
    public MapDisplayRenderer(double playerWorldX, double playerWorldZ, int mapScale) {
        this.mapScale = Math.max(1, mapScale);

        // Calculate display area in world blocks
        this.displayBlocksW = GB_WIDTH * this.mapScale;
        this.displayBlocksH = GB_HEIGHT * this.mapScale;

        // Center display on player position
        this.displayStartX = (int) Math.round(playerWorldX) - displayBlocksW / 2;
        this.displayStartZ = (int) Math.round(playerWorldZ) - displayBlocksH / 2;

        // Inner grid covers display + border
        int innerMinX = displayStartX - BORDER_BLOCKS;
        int innerMinZ = displayStartZ - BORDER_BLOCKS;
        int innerMaxX = displayStartX + displayBlocksW + BORDER_BLOCKS;
        int innerMaxZ = displayStartZ + displayBlocksH + BORDER_BLOCKS;
        this.innerMinChunkX = Math.floorDiv(innerMinX, CHUNK_BLOCKS);
        this.innerMinChunkZ = Math.floorDiv(innerMinZ, CHUNK_BLOCKS);
        this.innerMaxChunkX = ceilDiv(innerMaxX, CHUNK_BLOCKS);
        this.innerMaxChunkZ = ceilDiv(innerMaxZ, CHUNK_BLOCKS);

        // Outer grid adds padding of solid black chunks
        this.gridMinChunkX = innerMinChunkX - PADDING_CHUNKS;
        this.gridMinChunkZ = innerMinChunkZ - PADDING_CHUNKS;
        int gridMaxChunkX = innerMaxChunkX + PADDING_CHUNKS;
        int gridMaxChunkZ = innerMaxChunkZ + PADDING_CHUNKS;
        this.gridWidth = gridMaxChunkX - gridMinChunkX;
        this.gridHeight = gridMaxChunkZ - gridMinChunkZ;

        this.compressor = new DeltaCompressor(
                innerMaxChunkX - innerMinChunkX,
                innerMaxChunkZ - innerMinChunkZ,
                CHUNK_IMAGE_PIXELS
        );

        this.paddingSent = new boolean[gridWidth * gridHeight];
    }

    /**
     * Ceiling integer division that works correctly for negative numbers.
     */
    static int ceilDiv(int a, int b) {
        return -Math.floorDiv(-a, b);
    }

    /**
     * Renders a Game Boy frame (160x144 RGBA pixels) to the map, returning
     * an UpdateWorldMap packet with only the changed chunks.
     *
     * @param rgbaPixels 160x144 RGBA pixel array (23040 ints)
     * @return packet with changed chunks, or null if nothing changed
     */
    @Nullable
    public UpdateWorldMap renderFrame(@Nonnull int[] rgbaPixels) {
        List<MapChunk> changedChunks = new ArrayList<>();

        int innerW = innerMaxChunkX - innerMinChunkX;

        for (int row = 0; row < gridHeight; row++) {
            for (int col = 0; col < gridWidth; col++) {
                int chunkX = gridMinChunkX + col;
                int chunkZ = gridMinChunkZ + row;
                int gridIndex = row * gridWidth + col;

                boolean isInner = chunkX >= innerMinChunkX && chunkX < innerMaxChunkX
                        && chunkZ >= innerMinChunkZ && chunkZ < innerMaxChunkZ;

                if (isInner) {
                    // Display/border chunk - render full detail
                    extractChunk(rgbaPixels, chunkX, chunkZ, mapScale, chunkPixels,
                            displayStartX, displayStartZ, displayBlocksW, displayBlocksH);

                    int innerCol = chunkX - innerMinChunkX;
                    int innerRow = chunkZ - innerMinChunkZ;
                    int innerIndex = innerRow * innerW + innerCol;

                    if (compressor.hasChanged(innerIndex, chunkPixels)) {
                        MapImage image = new MapImage(
                                CHUNK_IMAGE_SIZE, CHUNK_IMAGE_SIZE,
                                Arrays.copyOf(chunkPixels, CHUNK_IMAGE_PIXELS)
                        );
                        changedChunks.add(new MapChunk(chunkX, chunkZ, image));
                    }
                } else {
                    // Padding chunk - solid black, send once
                    if (!paddingSent[gridIndex]) {
                        paddingSent[gridIndex] = true;
                        // Use a small 1x1 image - client stretches to fill chunk
                        MapImage blackImage = new MapImage(1, 1, new int[]{BLACK});
                        changedChunks.add(new MapChunk(chunkX, chunkZ, blackImage));
                    }
                }
            }
        }

        if (changedChunks.isEmpty()) {
            return null;
        }

        return new UpdateWorldMap(
                changedChunks.toArray(new MapChunk[0]),
                null,
                null
        );
    }

    /**
     * Extracts the image for a single map chunk from the GB frame.
     * Uses absolute world coordinates for the chunk position and display area.
     *
     * Rendering zones (in world block coordinates):
     *   - Display area: [dispStartX, dispStartX+dispW) x [dispStartZ, dispStartZ+dispH) -> GB frame pixels
     *   - Border: [dispStartX-BORDER, dispStartX+dispW+BORDER) x ... minus display -> gray
     *   - Outside: everything else -> black
     *
     * @param frame      the full 160x144 RGBA frame
     * @param chunkX     absolute chunk X coordinate
     * @param chunkZ     absolute chunk Z coordinate
     * @param scale      world blocks per GB pixel
     * @param dest       destination array (CHUNK_IMAGE_PIXELS ints)
     * @param dispStartX display top-left world block X
     * @param dispStartZ display top-left world block Z
     * @param dispW      display width in world blocks (GB_WIDTH * scale)
     * @param dispH      display height in world blocks (GB_HEIGHT * scale)
     */
    static void extractChunk(@Nonnull int[] frame, int chunkX, int chunkZ,
                             int scale, @Nonnull int[] dest,
                             int dispStartX, int dispStartZ, int dispW, int dispH) {
        int chunkWorldX = chunkX * CHUNK_BLOCKS;
        int chunkWorldZ = chunkZ * CHUNK_BLOCKS;

        int borderMinX = dispStartX - BORDER_BLOCKS;
        int borderMinZ = dispStartZ - BORDER_BLOCKS;
        int borderMaxX = dispStartX + dispW + BORDER_BLOCKS;
        int borderMaxZ = dispStartZ + dispH + BORDER_BLOCKS;

        for (int py = 0; py < CHUNK_IMAGE_SIZE; py++) {
            int worldZ = chunkWorldZ + py / PX_PER_BLOCK;
            int destRow = py * CHUNK_IMAGE_SIZE;

            // Check Z zone
            boolean zInDisplay = worldZ >= dispStartZ && worldZ < dispStartZ + dispH;
            boolean zInBorder = worldZ >= borderMinZ && worldZ < borderMaxZ;

            if (!zInBorder) {
                // Completely outside border -> black row
                Arrays.fill(dest, destRow, destRow + CHUNK_IMAGE_SIZE, BLACK);
                continue;
            }

            int gbY = zInDisplay ? (worldZ - dispStartZ) / scale : -1;
            int frameRow = (gbY >= 0 && gbY < GB_HEIGHT) ? gbY * GB_WIDTH : -1;

            for (int px = 0; px < CHUNK_IMAGE_SIZE; px++) {
                int worldX = chunkWorldX + px / PX_PER_BLOCK;

                boolean xInDisplay = worldX >= dispStartX && worldX < dispStartX + dispW;
                boolean xInBorder = worldX >= borderMinX && worldX < borderMaxX;

                if (zInDisplay && xInDisplay && frameRow >= 0) {
                    // Inside display area -> GB pixel
                    int gbX = (worldX - dispStartX) / scale;
                    if (gbX >= 0 && gbX < GB_WIDTH) {
                        dest[destRow + px] = frame[frameRow + gbX];
                    } else {
                        dest[destRow + px] = BLACK;
                    }
                } else if (zInBorder && xInBorder) {
                    // Inside border but outside display -> gray
                    dest[destRow + px] = BORDER_COLOR;
                } else {
                    // Outside everything -> black
                    dest[destRow + px] = BLACK;
                }
            }
        }
    }

    /**
     * Sends the packet to a specific player.
     */
    public static void sendToPlayer(@Nonnull PlayerRef player, @Nonnull UpdateWorldMap packet) {
        player.getPacketHandler().write(packet);
    }

    /**
     * Forces a full redraw on the next frame (resets delta compression state
     * and re-sends all padding chunks).
     */
    public void reset() {
        compressor.reset();
        paddingSent = new boolean[gridWidth * gridHeight];
    }

    public int getDisplayStartX() { return displayStartX; }
    public int getDisplayStartZ() { return displayStartZ; }
    public int getGridMinChunkX() { return gridMinChunkX; }
    public int getGridMinChunkZ() { return gridMinChunkZ; }
    public int getGridWidth() { return gridWidth; }
    public int getGridHeight() { return gridHeight; }
    public int getMapScale() { return mapScale; }
    public int getInnerGridWidth() { return innerMaxChunkX - innerMinChunkX; }
    public int getInnerGridHeight() { return innerMaxChunkZ - innerMinChunkZ; }
}
