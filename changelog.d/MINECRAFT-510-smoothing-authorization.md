bump: minor

### Added
- `SmoothingAuthorizationEvent`, a cancellable NeoForge event fired on `NeoForge.EVENT_BUS`
  immediately before `Smoothing.smooth()` mutates either end of a move, so a claim/protection mod
  can deny grading/erosion on its land before any block changes. Cancelling leaves both the
  source and destination cells completely unchanged -- the generic safe-mutation seam
  MINECRAFT-510's dedicated Layered Slopes/DIV erosion compat mod needs, with no erosion-specific
  logic added to this mod. Covered by two new standalone GameTests
  (`SmoothingAuthorizationGameTests`): cancellation leaves both ends unchanged, and the
  production default (no listener registered) still succeeds as before.
