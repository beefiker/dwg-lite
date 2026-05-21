# dwg-lite

Lightweight Kotlin DWG reader for preview-oriented apps.

This is an early, dependency-free parser under the `dev.jaeyoung.dwg` package. It is intended to become a Maven artifact with coordinates:

```text
dev.jaeyoung:dwg-lite
```

## Current Scope

- DWG version: AC1015 / AutoCAD R2000
- Entities: `POINT`, `LINE`, `CIRCLE`, `ARC`, `LWPOLYLINE`, `TEXT`, `MTEXT`
- Output model: small Kotlin data classes for preview rendering
- Unsupported versions/entities return structured unsupported results instead of throwing

The reader is intentionally narrow and optimized for safe preview extraction rather than full CAD editing.

## Reference

Implementation work referenced the DWG reader layout in acadrust:

https://github.com/hakanaktt/acadrust

The unit-test fixtures are generated from acadrust's `gen_all_entities_all_versions` example.

## Build

```bash
./gradlew test
```

## Local Maven Publish Check

```bash
./gradlew publishToMavenLocal
```

The current development artifact is published as:

```text
dev.jaeyoung:dwg-lite:0.1.0-SNAPSHOT
```

## License

MPL-2.0.
