package dev.chasem.hg.virtualtale.emulator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;

/**
 * Detects the emulator backend type from a ROM file's extension.
 */
public enum RomType {
    GAMEBOY(160, 144),
    GBA(240, 160);

    private final int width;
    private final int height;

    RomType(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /**
     * Detects the ROM type from a file's extension.
     *
     * @return the detected type, or null if the extension is unrecognized
     */
    @Nullable
    public static RomType detect(@Nonnull File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".gb") || name.endsWith(".gbc")) {
            return GAMEBOY;
        }
        if (name.endsWith(".gba") || name.endsWith(".agb")) {
            return GBA;
        }
        return null;
    }
}
