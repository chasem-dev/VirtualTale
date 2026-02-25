# VirtualTale

A Hytale server mod that runs a Game Boy emulator and streams the display onto Hytale's world map. Each player gets their own emulator instance.


   <img width="1512" height="874" alt="Screenshot 2026-02-25 at 11 30 50 AM" src="https://github.com/user-attachments/assets/19e57276-4c81-448a-ad5c-03d79fca6a55" />
   
<img width="1512" height="874" alt="Screenshot 2026-02-25 at 11 30 34 AM" src="https://github.com/user-attachments/assets/7ac784d3-6311-4977-89f7-d3a6435dc62d" />


## Setup

1. Build the mod:
   ```bash
   ./gradlew build
   ```

3. Copy `build/libs/VirtualTale-0.1.0-all.jar` to your `Server/mods/` directory.

4. Start the server. The plugin will create the following structure inside its data folder:
   ```
   mods/VirtualTale_VirtualTale/
   ├── config.json
   ├── roms/          ← Place ROM files here (.gb, .gbc, .gba)
   ├── bios/          ← Place BIOS files here
   │   └── gba_bios.bin   (required for GBA ROMs)
   └── saves/         ← Per-player save files
       └── <game>/<player-uuid>/<game>.sav
   ```

5. Place your ROM files in `roms/` and (for GBA) place `gba_bios.bin` in `bios/`.

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
| `/vt start --rom=<file>` | Start an emulator session with the given ROM |
| `/vt stop` | Stop your current session |
| `/vt list` | Show all active sessions |
| `/vt roms` | List available ROM files |
| `/vt mapscale --rom=<n>` | Set display size 1–8 (default: 4) |
| `/vt speed --rom=<1-8>` | Set emulator speed multiplier (default: 1x) |

The ROM name can be the exact filename, the name without extension, or a case-insensitive prefix. For example, if you have `Tetris.gb`, any of these work:

```
/vt start --rom=Tetris.gb
/vt start --rom=Tetris
/vt start --rom=tetris
```

### Controls

All controls work while the map screen (M key) is open.

**D-pad:** WASD movement is detected server-side and translated to D-pad presses (player is teleported back to anchor).

**Hotbar keys (1–8):** Press hotbar keys to trigger emulator buttons:

| Key | Button |
|-----|--------|
| 1 | UP |
| 2 | DOWN |
| 3 | LEFT |
| 4 | RIGHT |
| 5 | A |
| 6 | B |
| 7 | START |
| 8 | SELECT |

Buttons auto-release after a configurable hold duration (default 200ms). Repeated presses of the same key are debounced while the button is held.

**Known issues:**
- Spamming the same key too quickly can cause it to stop registering. The server resets your hotbar to a neutral slot after each press, but if you press the key again before that reset reaches the client, no new event is generated. Press a different key to unstick it.
- The display may not render correctly on first start depending on your map zoom level. If this happens, stop the session (`/vt stop`), adjust your map zoom (try zooming out or in), then restart.

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

## Emulator Libraries

This project uses two open-source Game Boy emulators running headlessly on the server:

- [**coffee-gb**](https://github.com/trekawek/coffee-gb) — Game Boy / Game Boy Color emulator (`.gb`, `.gbc`)
- [**BooYahGBA**](https://github.com/chasem-dev/BooYahGBA) — Game Boy Advance emulator (`.gba`)
