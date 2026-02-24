package dev.chasem.hg.virtualtale.display;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class DeltaCompressorTest {

    // Match the actual chunk image pixel count (64*64 = 4096)
    private static final int CHUNK_PIXELS = MapDisplayRenderer.CHUNK_IMAGE_SIZE
            * MapDisplayRenderer.CHUNK_IMAGE_SIZE;

    private DeltaCompressor compressor;

    @BeforeEach
    void setUp() {
        compressor = new DeltaCompressor(5, 5, CHUNK_PIXELS);
    }

    @Test
    void firstFrame_alwaysReportsChanged() {
        int[] pixels = new int[CHUNK_PIXELS];
        Arrays.fill(pixels, 0x000000FF);

        assertThat(compressor.hasChanged(0, pixels)).isTrue();
    }

    @Test
    void identicalFrame_reportsUnchanged() {
        int[] pixels = new int[CHUNK_PIXELS];
        Arrays.fill(pixels, 0x00FF00FF);

        compressor.hasChanged(0, pixels);

        assertThat(compressor.hasChanged(0, pixels)).isFalse();
    }

    @Test
    void differentFrame_reportsChanged() {
        int[] pixels1 = new int[CHUNK_PIXELS];
        Arrays.fill(pixels1, 0x000000FF);

        int[] pixels2 = new int[CHUNK_PIXELS];
        Arrays.fill(pixels2, 0xFFFFFFFF);

        compressor.hasChanged(0, pixels1);
        assertThat(compressor.hasChanged(0, pixels2)).isTrue();
    }

    @Test
    void singlePixelChange_reportsChanged() {
        int[] pixels = new int[CHUNK_PIXELS];
        Arrays.fill(pixels, 0x000000FF);

        compressor.hasChanged(0, pixels);

        pixels[512] = 0xFF0000FF;
        assertThat(compressor.hasChanged(0, pixels)).isTrue();
    }

    @Test
    void independentChunks_trackedSeparately() {
        int[] pixels1 = new int[CHUNK_PIXELS];
        Arrays.fill(pixels1, 0x000000FF);

        int[] pixels2 = new int[CHUNK_PIXELS];
        Arrays.fill(pixels2, 0xFFFFFFFF);

        compressor.hasChanged(0, pixels1);
        compressor.hasChanged(1, pixels2);

        assertThat(compressor.hasChanged(0, pixels1)).isFalse();

        int[] pixels2Changed = new int[CHUNK_PIXELS];
        Arrays.fill(pixels2Changed, 0x0000FFFF);
        assertThat(compressor.hasChanged(1, pixels2Changed)).isTrue();
    }

    @Test
    void reset_forcesFullRedraw() {
        int[] pixels = new int[CHUNK_PIXELS];
        Arrays.fill(pixels, 0x000000FF);

        compressor.hasChanged(0, pixels);
        assertThat(compressor.hasChanged(0, pixels)).isFalse();

        compressor.reset();

        assertThat(compressor.hasChanged(0, pixels)).isTrue();
    }

    @Test
    void invalidChunkIndex_returnsTrue() {
        int[] pixels = new int[CHUNK_PIXELS];
        assertThat(compressor.hasChanged(-1, pixels)).isTrue();
        assertThat(compressor.hasChanged(25, pixels)).isTrue();
    }

    @Test
    void gridDimensions_correct() {
        assertThat(compressor.getGridWidth()).isEqualTo(5);
        assertThat(compressor.getGridHeight()).isEqualTo(5);
        assertThat(compressor.getTotalChunks()).isEqualTo(25);
    }
}
