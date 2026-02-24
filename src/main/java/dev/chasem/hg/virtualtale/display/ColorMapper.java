package dev.chasem.hg.virtualtale.display;

import javax.annotation.Nonnull;

/**
 * Maps Game Boy RGB pixel values to RGBA format for Hytale's MapImage.
 * coffee-gb outputs 24-bit RGB (0xRRGGBB); Hytale MapImage expects 32-bit RGBA
 * with R in bits 24-31, G in bits 16-23, B in bits 8-15, A in bits 0-7.
 *
 * This matches Hytale's internal ImageBuilder.Color.pack() format:
 *   (r << 24) | (g << 16) | (b << 8) | a
 */
public final class ColorMapper {

    private ColorMapper() {
    }

    /**
     * Converts a 24-bit RGB pixel to 32-bit RGBA by adding full opacity alpha.
     *
     * @param rgb 24-bit RGB value (0x00RRGGBB)
     * @return 32-bit RGBA value (0xRRGGBBFF)
     */
    public static int toRgba(int rgb) {
        return (rgb << 8) | 0xFF;
    }

    /**
     * Converts an array of RGB pixels to RGBA in-place.
     *
     * @param pixels array of RGB pixels, modified to RGBA
     */
    public static void toRgbaInPlace(@Nonnull int[] pixels) {
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (pixels[i] << 8) | 0xFF;
        }
    }

    /**
     * Converts an array of RGB pixels to RGBA, writing to a destination array.
     *
     * @param src  source RGB pixels
     * @param dest destination RGBA pixels (must be at least src.length)
     * @param len  number of pixels to convert
     */
    public static void toRgba(@Nonnull int[] src, @Nonnull int[] dest, int len) {
        for (int i = 0; i < len; i++) {
            dest[i] = (src[i] << 8) | 0xFF;
        }
    }

    /**
     * Returns an opaque black pixel in RGBA format. Used for padding.
     */
    public static int black() {
        return 0x000000FF;
    }
}
