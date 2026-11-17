bump: minor

### Added
- `GradingTool`: right-click a block with a shovel to push one sixteenth of
  it away from you, via the same `Smoothing.smooth()` mechanics the pickaxe
  pull already used (MINECRAFT-557). Replaces vanilla shovel path conversion
  on any block grading recognizes, including an unsupported/blocked push,
  which is now a safe no-op instead of an unexpected flatten-to-path.
  Pickaxe pull is unchanged; hoes are untouched.
