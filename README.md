# layered-slope-grading

Manual layer grading tool for NeoForge 1.21.1, built on the Layered Slopes
block family.

NeoForge 1.21.1, Java 21. Mod ID: `layeredslopegrading`. MIT licensed.

Extracted from
[Dynamic Terrain](https://github.com/brooswit-minecraft/dynamic-terrain)
(MINECRAFT-511), following the same pattern as the earlier
[Layered Slopes](https://github.com/brooswit-minecraft/layered-slopes)
(MINECRAFT-499) and
[Smooth Slopes](https://github.com/brooswit-minecraft/smooth-slopes)
(MINECRAFT-500) extractions.

## What this mod provides

- **`GradingTool`** — the player-facing feature: right-click a block with a
  pickaxe to pull one sixteenth of it toward you. Pickaxes have no vanilla
  right-click action on blocks, so nothing vanilla is displaced.
- **`Smoothing`** — the `smooth()`/`canSmooth()` orchestrator that actually
  moves one layer of material from a block into a neighbour. Takes a
  `disturb` flag: `false` for manual grading and the debug command (grass
  keeps its grass top while graded), `true` for erosion (grass settles to
  dirt). This mod always calls it with `disturb=false`.
- **`SmoothRules`** — the pure, Minecraft-type-free decision for one
  `smooth()` step: whether a move is accepted and what each side becomes.
- **`Settling`** — the pure decision for whether a `smooth()` step settles
  grass as dirt, driven by the `disturb` flag above.
- **`LayeredMaterials`** — the vanilla-block ↔ layered-block mapping
  registry (`register`/`registerSlab`/`layeredFor`/`layeredForSlab`/
  `sameGroup`/`disturbsTo`/`disturbed`) that `Smoothing` depends on
  directly.
- **`Heading`** — a Minecraft-type-free horizontal heading used to pick
  which neighbour `GradingTool` grades into.
- The `/dtsmooth <pos> <direction>` debug command (op-only), name
  unchanged from Dynamic Terrain for compatibility.

## Install / dependency pairing

Depends on [Layered Slopes](https://github.com/brooswit-minecraft/layered-slopes)
(required; see `build.gradle`'s `downloadLayeredSlopes` task and
`neoforge.mods.toml`'s `layeredslopes` dependency block). Runs fully
standalone otherwise — **never** requires Dynamic Terrain. Dynamic
Terrain instead depends on this mod (the same direction it already depends
on Layered Slopes), because its automatic erosion system
(`Erosion.java`) calls the exact same `Smoothing.smooth()` mechanics this
mod ships, just with `disturb=true` instead of this mod's `disturb=false`.

## Test coverage — what's covered, what isn't

Headless unit tests (no game bootstrap): `HeadingTest`, `SmoothRulesTest`
and `SettlingTest` cover the pure decision logic ported from Dynamic
Terrain unchanged. `Smoothing`/`LayeredMaterials`/`GradingTool` depend on
Minecraft and NeoForge classes this project's plain `test` sourceSet has
no classpath access to, so they aren't covered by unit tests here — the
same limitation Dynamic Terrain had before this extraction. No in-game
GameTest harness exists in this repo yet; `./gradlew build`'s
`compileJava`/`test` is the acceptance bar this extraction set.
