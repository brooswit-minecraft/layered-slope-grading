bump: minor

### Added
- Grading recognizes vanilla `dirt_path` and its new `layered_dirt_path`
  variant (MINECRAFT-569), correctly starting from the real 15/16 vanilla
  baseline rather than treating it as a full 16/16 block: grading a path
  down yields a 14-layer path, grading material onto one yields 16, and a
  rejected/no-op move leaves vanilla dirt path untouched. Depends on
  Layered Slopes' not-yet-released dirt-path addition (PR #3); this PR
  stays a draft, pinned to the real released layered-slopes version, until
  that dependency actually ships -- see the paired-PR CI step in `ci.yml`
  for how this PR is validated against that exact unreleased commit in the
  meantime.
