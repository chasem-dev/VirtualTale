package dev.chasem.hg.virtualtale.display;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class MapDisplayRendererTest {

    private static final int IMG = MapDisplayRenderer.CHUNK_IMAGE_SIZE; // 64
    private static final int IMG_PIXELS = IMG * IMG;
    private static final int PX_PER_BLOCK = MapDisplayRenderer.PX_PER_BLOCK; // 2
    private static final int BORDER = MapDisplayRenderer.BORDER_BLOCKS; // 5
    private static final int CHUNK = MapDisplayRenderer.CHUNK_BLOCKS; // 32

    /** RGBA opaque black. */
    private static final int BLACK = 0x000000FF;
    /** RGBA border gray. */
    private static final int BORDER_COLOR = 0x2A2A2AFF;

    /** Pixel index in the flat image array at (px, py). */
    private static int idx(int px, int py) {
        return py * IMG + px;
    }

    @Test
    void extractChunk_displayAtOrigin_allDisplayContent() {
        // Frame: all red (RGBA)
        int red = 0xFF0000FF;
        int[] frame = new int[160 * 144];
        Arrays.fill(frame, red);

        int[] chunk = new int[IMG_PIXELS];
        // Chunk (0,0) covers world blocks [0,32)x[0,32). Display at (0,0) -> entire chunk is display.
        MapDisplayRenderer.extractChunk(frame, 0, 0, 1, chunk, 0, 0, 160, 144);

        // All pixels should be red (display content)
        assertThat(chunk[idx(0, 0)]).isEqualTo(red);
        assertThat(chunk[idx(IMG - 1, IMG - 1)]).isEqualTo(red);
    }

    @Test
    void extractChunk_borderVisible_whenDisplayOffset() {
        int red = 0xFF0000FF;
        int[] frame = new int[160 * 144];
        Arrays.fill(frame, red);

        int[] chunk = new int[IMG_PIXELS];
        // Display starts at (5,5). Chunk (0,0) covers [0,32)x[0,32).
        // World block (0,0) is outside display [5,165) but inside border [0,170).
        MapDisplayRenderer.extractChunk(frame, 0, 0, 1, chunk, 5, 5, 160, 144);

        // Pixel (0,0) -> world block (0,0) -> in border, outside display -> gray
        assertThat(chunk[idx(0, 0)]).isEqualTo(BORDER_COLOR);

        // Pixel at world block (5,5) = first display pixel -> red
        // px = (5 - 0) * PX_PER_BLOCK = 10, py = 10
        assertThat(chunk[idx(10, 10)]).isEqualTo(red);
    }

    @Test
    void extractChunk_blackOutsideBorder() {
        int red = 0xFF0000FF;
        int[] frame = new int[160 * 144];
        Arrays.fill(frame, red);

        int[] chunk = new int[IMG_PIXELS];
        // Chunk (-2, -2) covers world blocks [-64, -32) x [-64, -32).
        // Display at (0,0), border extends [-5, 165) x [-5, 149).
        // All of chunk (-2,-2) is well outside border -> all black.
        MapDisplayRenderer.extractChunk(frame, -2, -2, 1, chunk, 0, 0, 160, 144);

        assertThat(chunk[idx(0, 0)]).isEqualTo(BLACK);
        assertThat(chunk[idx(IMG - 1, IMG - 1)]).isEqualTo(BLACK);
    }

    @Test
    void extractChunk_borderAndBlackMixed() {
        int red = 0xFF0000FF;
        int[] frame = new int[160 * 144];
        Arrays.fill(frame, red);

        int[] chunk = new int[IMG_PIXELS];
        // Chunk (-1, 0) covers world blocks [-32, 0) x [0, 32).
        // Display at (0,0), border X extends [-5, 165).
        // World blocks [-32, -6) -> black (outside border)
        // World blocks [-5, 0) -> gray (in border, outside display)
        MapDisplayRenderer.extractChunk(frame, -1, 0, 1, chunk, 0, 0, 160, 144);

        // px=0 -> worldX = -32 + 0/2 = -32 -> outside border -> black
        assertThat(chunk[idx(0, 0)]).isEqualTo(BLACK);

        // px that maps to worldX = -5: px = (-5 - (-32)) * PX_PER_BLOCK = 27*2 = 54
        // worldX = -32 + 54/2 = -32 + 27 = -5 -> in border, outside display -> gray
        assertThat(chunk[idx(54, 0)]).isEqualTo(BORDER_COLOR);
    }

    @Test
    void extractChunk_scale2_eachPixelDoubled() {
        // Frame with unique pixels
        int[] frame = new int[160 * 144];
        for (int y = 0; y < 144; y++) {
            for (int x = 0; x < 160; x++) {
                frame[y * 160 + x] = (y << 16) | (x << 8) | 0xFF; // RGBA with unique R,G
            }
        }

        int dispW = 160 * 2;
        int dispH = 144 * 2;
        int[] chunk = new int[IMG_PIXELS];
        // Chunk (0,0) covers world blocks [0,32)x[0,32). Display at (0,0).
        MapDisplayRenderer.extractChunk(frame, 0, 0, 2, chunk, 0, 0, dispW, dispH);

        // px=0, py=0 -> worldX=0, worldZ=0 -> gbX=0/2=0, gbY=0/2=0
        int expected00 = (0 << 16) | (0 << 8) | 0xFF;
        assertThat(chunk[idx(0, 0)]).isEqualTo(expected00);

        // px=4, py=0 -> worldX=0+4/2=2, worldZ=0 -> gbX=2/2=1, gbY=0
        int expected10 = (0 << 16) | (1 << 8) | 0xFF;
        assertThat(chunk[idx(4, 0)]).isEqualTo(expected10);
    }

    @Test
    void constructorCentersOnPlayer_scale1() {
        // Player at (80, 72) -> display center at (80,72)
        // displayStartX = 80 - 160/2 = 0
        // displayStartZ = 72 - 144/2 = 0
        MapDisplayRenderer renderer = new MapDisplayRenderer(80, 72, 1);

        assertThat(renderer.getDisplayStartX()).isEqualTo(0);
        assertThat(renderer.getDisplayStartZ()).isEqualTo(0);
    }

    @Test
    void constructorCentersOnPlayer_atOrigin() {
        // Player at (0, 0) -> display center at (0,0)
        // displayStartX = 0 - 160/2 = -80
        // displayStartZ = 0 - 144/2 = -72
        MapDisplayRenderer renderer = new MapDisplayRenderer(0, 0, 1);

        assertThat(renderer.getDisplayStartX()).isEqualTo(-80);
        assertThat(renderer.getDisplayStartZ()).isEqualTo(-72);
    }

    @Test
    void renderFrame_firstFrame_returnsChunks() {
        MapDisplayRenderer renderer = new MapDisplayRenderer(80, 72, 1);
        int[] frame = new int[160 * 144];
        Arrays.fill(frame, 0x000000FF);

        var packet = renderer.renderFrame(frame);
        assertThat(packet).isNotNull();

        // Should have inner chunks (display+border) plus padding chunks
        int innerChunks = renderer.getInnerGridWidth() * renderer.getInnerGridHeight();
        int totalChunks = renderer.getGridWidth() * renderer.getGridHeight();
        int paddingChunks = totalChunks - innerChunks;
        assertThat(packet.chunks.length).isEqualTo(innerChunks + paddingChunks);
    }

    @Test
    void renderFrame_scale2_largerGrid() {
        MapDisplayRenderer r1 = new MapDisplayRenderer(0, 0, 1);
        MapDisplayRenderer r2 = new MapDisplayRenderer(0, 0, 2);

        // Scale 2 should have a larger inner grid than scale 1
        assertThat(r2.getInnerGridWidth()).isGreaterThan(r1.getInnerGridWidth());
        assertThat(r2.getInnerGridHeight()).isGreaterThan(r1.getInnerGridHeight());
    }

    @Test
    void renderFrame_unchangedFrame_returnsNull() {
        MapDisplayRenderer renderer = new MapDisplayRenderer(80, 72, 1);
        int[] frame = new int[160 * 144];
        Arrays.fill(frame, 0x000000FF);

        renderer.renderFrame(frame);

        // Second render with same frame -> null (no changes)
        var packet = renderer.renderFrame(frame);
        assertThat(packet).isNull();
    }

    @Test
    void reset_causesFullRedraw() {
        MapDisplayRenderer renderer = new MapDisplayRenderer(80, 72, 1);
        int[] frame = new int[160 * 144];
        Arrays.fill(frame, 0x000000FF);

        renderer.renderFrame(frame);
        assertThat(renderer.renderFrame(frame)).isNull();

        renderer.reset();

        var packet = renderer.renderFrame(frame);
        assertThat(packet).isNotNull();
        // Should re-send all chunks (inner + padding)
        int totalChunks = renderer.getGridWidth() * renderer.getGridHeight();
        assertThat(packet.chunks.length).isEqualTo(totalChunks);
    }

    @Test
    void constants_areCorrect() {
        assertThat(MapDisplayRenderer.CHUNK_BLOCKS).isEqualTo(32);
        assertThat(MapDisplayRenderer.PX_PER_BLOCK).isEqualTo(2);
        assertThat(MapDisplayRenderer.BORDER_BLOCKS).isEqualTo(5);
        assertThat(MapDisplayRenderer.CHUNK_IMAGE_SIZE).isEqualTo(64);
        assertThat(MapDisplayRenderer.PADDING_CHUNKS).isEqualTo(8);
    }

    @Test
    void ceilDiv_handlesPositiveAndNegative() {
        assertThat(MapDisplayRenderer.ceilDiv(10, 3)).isEqualTo(4);
        assertThat(MapDisplayRenderer.ceilDiv(9, 3)).isEqualTo(3);
        assertThat(MapDisplayRenderer.ceilDiv(-10, 3)).isEqualTo(-3);
        assertThat(MapDisplayRenderer.ceilDiv(-9, 3)).isEqualTo(-3);
        assertThat(MapDisplayRenderer.ceilDiv(0, 3)).isEqualTo(0);
    }
}
