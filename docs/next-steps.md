# Next Steps

## Priority 1 - Tighten Compatibility Edges

- grow the block-level pass sets in `SpecConformanceTest` (indented code, lists, multi-line reference definitions, tables)
- decide whether reference-link behavior needs stricter title parsing or should stay intentionally one-line only
- document any preset-level behavior that is intentionally divergent before a public `0.2.x` cut

## Priority 2 - Reduce Incremental Cost

- profile large block lists where snapshot rebuild and delta classification dominate append cost
- evaluate narrower immutable rebuild strategies before attempting broader feature expansion
- add at least one repeatable benchmark history file or CI artifact flow so performance drift becomes easier to review

## Priority 3 - Strengthen Cross-platform Confidence

- keep renderer-facing assumptions isolated so non-Compose consumers can be added later without reshaping core APIs

## Priority 4 - Prepare Release Surface

- freeze the public engine and snapshot contract for one milestone
- decide whether module-level READMEs should stay lightweight or become publication-ready package docs
- agree on compatibility promises and changelog policy for published releases

## Priority 5 - Renderer Follow-up

- improve table and image rendering only if product requirements justify the extra surface area
- consider interactive task-list UI as a renderer-only enhancement, not as a core parser expansion
- keep renderer changes scoped so the append-only engine contract remains stable
