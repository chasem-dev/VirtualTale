# VirtualTale

A Hytale server mod that runs a Game Boy emulator and streams the display onto Hytale's world map. Each player gets their own emulator instance.

## Setup

1. Build the mod:
   ```bash
   ./gradlew build
   ```

2. Copy `build/libs/VirtualTale-0.1.0-all.jar` to your `Server/mods/` directory.

3. Start the server. The plugin will create the following structure inside its data folder:
   ```
   plugins/virtual-tale/
   ├── config.json
   ├── roms/          ← Place ROM files here (.gb, .gbc, .gba)
   ├── bios/          ← Place BIOS files here
   │   └── gba_bios.bin   (required for GBA ROMs)
   └── saves/         ← Per-player save files
       └── <game>/<player-uuid>/<game>.sav
   ```

4. Place your ROM files in `roms/` and (for GBA) place `gba_bios.bin` in `bios/`.

## Gradle Tasks

| Command | Description |
|---------|-------------|
| `./gradlew build` | Compile, test, and package the fat JAR (`build/libs/VirtualTale-*-all.jar`) |
| `./gradlew runServer` | Build and launch a local Hytale server with the plugin installed |
| `./gradlew test` | Run unit tests |
| `./gradlew shadowJar` | Build the shadow JAR (dependencies merged) |

## Usage

### Commands

| Command | Description |
|---------|-------------|
| `/vt start <rom>` | Start an emulator session with the given ROM |
| `/vt stop` | Stop your current session |
| `/vt list` | Show all active sessions |
| `/vt roms` | List available ROM files |

The ROM name can be the exact filename, the name without extension, or a case-insensitive prefix. For example, if you have `Tetris.gb`, any of these work:

```
/vt start Tetris.gb
/vt start Tetris
/vt start tetris
```

### Controls

All controls work while the map screen (M key) is open.

**D-pad (movement):** WASD keys. The server detects your movement direction, presses the corresponding Game Boy direction, and teleports you back to your anchor position.

**Action buttons (hotbar):** Switch to hotbar slots 1-4 to press buttons:

| Hotbar Slot | Game Boy Button |
|-------------|-----------------|
| 1 | A |
| 2 | B |
| 3 | START |
| 4 | SELECT |

Buttons auto-release after 200ms (configurable).

### Viewing the Display

Open the world map with **M** after starting a session. The Game Boy's 160x144 display is rendered as a 5x5 grid of map chunks at the configured map origin.

## Configuration

The config file is at `Server/plugins/virtual-tale/config.json`:

```json
{
  "mapOriginChunkX": -3,
  "mapOriginChunkZ": -3,
  "renderFps": 20,
  "anchorX": 0.0,
  "anchorY": 70.0,
  "anchorZ": 0.0,
  "anchorWorldName": "default",
  "buttonHoldMs": 200
}
```

ROMs go in `roms/`, GBA BIOS goes in `bios/`, and save files are written to `saves/<game>/<player-uuid>/<game>.sav` (all directories are created automatically on startup).

| Field | Description |
|-------|-------------|
| `mapOriginChunkX/Z` | Map chunk coordinates where the top-left of the display is placed |
| `renderFps` | How often frames are pushed to the map (20 is a good default) |
| `anchorX/Y/Z` | World position players are held at during a session (for WASD input detection) |
| `anchorWorldName` | World name for the anchor position |
| `buttonHoldMs` | How long hotbar button presses are held before auto-release |

## How It Works

```
Game Boy emulator (~60fps)
  -> FrameBuffer (thread-safe double buffer)
    -> Render thread (sampled at 20fps)
      -> ColorMapper (RGB -> ARGB)
      -> MapDisplayRenderer (split into 5x5 grid of 32x32 chunks)
      -> DeltaCompressor (skip unchanged chunks)
      -> UpdateWorldMap packet (only changed chunks, auto-compressed by Hytale)
        -> Player's map display
```

The emulator runs on its own thread per player. A separate render thread samples the latest frame at the configured FPS, converts it to Hytale's ARGB map format, splits it into 32x32 map chunks, and only sends chunks that changed since the last frame.
