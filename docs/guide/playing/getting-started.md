# Getting Started

This page gets you from zero to playing in under five minutes.

## What You Need

- **Java 21 or later.** Download from [Adoptium](https://adoptium.net/) or your preferred
  distribution. Run `java -version` to check.
- **A GPU that supports OpenGL 4.1.** Any discrete GPU from the last decade will work.
  Integrated graphics (Intel HD 4000+, Apple Silicon) are fine.
- **ROM files** for the games you want to play. The engine does not include any game data.
  You must supply your own legally obtained copies.

### Expected ROM Files

The engine is verified against these specific ROM revisions. Other revisions may produce
incorrect results.

| Game | Expected Filename | Revision |
|------|-------------------|----------|
| Sonic 1 | `Sonic The Hedgehog (W) (REV01) [!].gen` | World, Revision 01 |
| Sonic 2 | `Sonic The Hedgehog 2 (W) (REV01) [!].gen` | World, Revision 01 |
| Sonic 3&K | `Sonic and Knuckles & Sonic 3 (W) [!].gen` | World (lock-on combined ROM) |

ROM filenames can be changed in `config.json` if yours differ. See
[Configuration](configuration.md) for details.

## Install and Run

### Option A: Download a Release

1. Download the latest release ZIP from the Releases page.
2. Extract it to a folder.
3. Place your ROM files in the same folder as the JAR file.
4. Double-click the JAR, or run from a terminal:
   ```
   java -jar openggf.jar
   ```
   On Windows, you can also use the included `run.cmd`.

### Option B: Build from Source

1. Clone the repository:
   ```
   git clone https://github.com/jamesj999/sonic-engine.git
   cd sonic-engine
   ```
2. Build with Maven:
   ```
   mvn package
   ```
3. Place your ROM files in the project root directory (next to `pom.xml`).
4. Run:
   ```
   java -jar target/OpenGGF-0.6.prerelease-jar-with-dependencies.jar
   ```

## First Launch

When the engine starts, you will see:

1. **Master title screen** -- An engine-wide title screen with animated clouds and a game
   selection menu. Use the arrow keys to highlight a game and press Space to select it.
2. **Game title screen** -- The selected game's original title screen (e.g., the Sonic 2
   "PRESS START BUTTON" screen).
3. **Gameplay** -- The first zone of the selected game.

If a ROM file is missing for the game you selected, the engine will show an error.

## Quick Configuration

The engine reads settings from `config.json` in the working directory. If the file
does not exist, defaults are used. A few settings you might want to change immediately:

| Setting | What it does | Default |
|---------|-------------|---------|
| `DEFAULT_ROM` | Which game boots first (`"s1"`, `"s2"`, or `"s3k"`) | `"s1"` |
| `MASTER_TITLE_SCREEN_ON_STARTUP` | Show game picker on launch | `true` |
| `SCREEN_WIDTH` / `SCREEN_HEIGHT` | Window size in pixels | `640` x `448` |
| `AUDIO_ENABLED` | Enable or disable sound | `true` |
| `SIDEKICK_CHARACTER_CODE` | Add Tails as a CPU sidekick (`"tails"` or `""`) | `""` |

For the full list, see [Configuration](configuration.md) or the
[Configuration Reference](../../../CONFIGURATION.md).

## What Next?

- [Controls](controls.md) -- Learn the keyboard layout
- [Game Status](game-status.md) -- See what works in each game
- [Troubleshooting](troubleshooting.md) -- If something went wrong
