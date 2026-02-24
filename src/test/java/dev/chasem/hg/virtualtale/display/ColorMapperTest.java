package dev.chasem.hg.virtualtale.display;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ColorMapperTest {

    @Test
    void toRgba_addsFullAlphaToRgb() {
        // White RGB -> White RGBA
        assertThat(ColorMapper.toRgba(0xFFFFFF)).isEqualTo(0xFFFFFFFF);
    }

    @Test
    void toRgba_handlesBlack() {
        // Black RGB -> opaque black RGBA (alpha in low byte)
        assertThat(ColorMapper.toRgba(0x000000)).isEqualTo(0x000000FF);
    }

    @Test
    void toRgba_handlesPartialColors() {
        // GB green palette color 0x99c886 -> 0x99c886FF
        assertThat(ColorMapper.toRgba(0x99c886)).isEqualTo(0x99c886FF);
    }

    @Test
    void toRgba_pureRed() {
        assertThat(ColorMapper.toRgba(0xFF0000)).isEqualTo(0xFF0000FF);
    }

    @Test
    void toRgbaInPlace_convertsEntireArray() {
        int[] pixels = {0x000000, 0xFFFFFF, 0x99c886, 0x051f2a};
        ColorMapper.toRgbaInPlace(pixels);

        assertThat(pixels).containsExactly(
                0x000000FF, 0xFFFFFFFF, 0x99c886FF, 0x051f2aFF
        );
    }

    @Test
    void toRgba_arrayVersion_copiesBetweenArrays() {
        int[] src = {0xe6f8da, 0x99c886, 0x437969};
        int[] dest = new int[3];

        ColorMapper.toRgba(src, dest, 3);

        assertThat(dest).containsExactly(0xe6f8daFF, 0x99c886FF, 0x437969FF);
        // Source should be unchanged
        assertThat(src).containsExactly(0xe6f8da, 0x99c886, 0x437969);
    }

    @Test
    void toRgba_arrayVersion_respectsLength() {
        int[] src = {0xFF0000, 0x00FF00, 0x0000FF};
        int[] dest = new int[3];

        ColorMapper.toRgba(src, dest, 2);

        assertThat(dest[0]).isEqualTo(0xFF0000FF);
        assertThat(dest[1]).isEqualTo(0x00FF00FF);
        assertThat(dest[2]).isEqualTo(0); // Not touched
    }

    @Test
    void black_returnsOpaqueBlack() {
        assertThat(ColorMapper.black()).isEqualTo(0x000000FF);
    }
}
