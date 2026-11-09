# BloXes

A Fabric client mod for Minecraft **26.3**. Select a circle or square area, see its edge in the world, and count every block inside it.

## Phase 1 (this version)

- Circle or square area, any size from 16 to 4096 blocks across (256 by default)
- Outline in the world: a see-through wall with solid rails at the top, at your feet and at the bottom
- Total block count and a per block breakdown (netherrack, blackstone, gravel, ancient debris, ...)
- "Mineable only" is on by default and leaves out bedrock, barriers, fluids and other unbreakable blocks
- Small HUD box with the total (and optionally the top 3 / 5 / 10 block types)
- Block list menu with name filter and scrolling

### Planned

- Phase 2: blocks/h, time left, day counter, adjustable idle pause (5-120 s, default 15 s), HUD toggles
- Phase 3: item search over chests you have opened before (keybind)

## Use

| Command | What it does |
| --- | --- |
| `/bloxes` | Opens the settings menu |
| `/bloxes here` | Centers the area on you and turns it on |
| `/bloxes circle` / `/bloxes square` | Changes the shape |
| `/bloxes list` | Opens the block list |
| `/bloxes off` | Turns the area off |

## How the counting works

The client only has the chunks it has loaded. Each chunk inside the area is counted when it loads and the result is kept, so the total fills in as you move around the area. The HUD shows how much of the area has been scanned. Chunks near you are counted again every few ticks so the numbers follow your mining.

Settings are saved in `config/bloxes.json`.

## Build

Needs Java 25.

```
./gradlew build
```

The jar ends up in `build/libs/` (use the one without `-sources`).
