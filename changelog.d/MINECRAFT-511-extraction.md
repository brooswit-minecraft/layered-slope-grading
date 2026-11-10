bump: minor

### Added
- Layered Slope Grading: the manual layer grading tool (right-click a block
  with a pickaxe) and its supporting pure-logic classes, extracted from
  Dynamic Terrain (MINECRAFT-511): `GradingTool`, `Smoothing` (the shared
  `smooth()`/`canSmooth()` orchestrator, `disturb=false` for manual grading),
  `SmoothRules`, `Settling`, `LayeredMaterials` and `Heading`. Also carries
  the `/dtsmooth <pos> <direction>` debug command, name unchanged for
  compatibility. Requires Layered Slopes (MINECRAFT-499); never requires
  Dynamic Terrain, which depends on this mod in turn so its erosion system
  can call the same `Smoothing.smooth()` mechanics with `disturb=true`.
