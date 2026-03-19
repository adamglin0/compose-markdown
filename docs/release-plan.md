# Release Plan

## Coordinates

- Gradle group: `dev.markstream`
- planned artifact IDs:
  - `markstream-core`
  - `markstream-compose`
  - `markstream-sample-chat` is sample-only and should not be published by default
  - `markstream-benchmarks` stays internal-only

## Versioning

- current repository version: `0.1.0-SNAPSHOT`
- recommended public versioning policy for the next checkpoints:
  - `0.y.z` while public API and parser behavior are still stabilizing
  - bump `y` when snapshot/delta semantics or renderer-facing behavior change materially
  - bump `z` for compatible fixes, docs, and performance-only releases

## Publishing TODO

- add `maven-publish` only when artifact metadata and API docs are ready;
- publish `markdown-core` and `markdown-compose` first;
- document supported Kotlin / Compose / Gradle versions in release notes;
- generate a minimal changelog from stage checkpoints;
- add artifact signing only when there is a real publication target;
- keep benchmark and sample modules excluded from default publication.

## Pre-Release Checklist

- freeze public API surface for one milestone;
- verify benchmark trend is stable across at least two local runs;
- confirm README quick start against a clean checkout;
- decide whether reference-link behavior needs additional compatibility guarantees before `0.2.0`.
