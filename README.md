# The Lost Camera

The Lost Camera is an exploration and photography mod for Minecraft 26.1.2, 26.2 and 26.3, available on Fabric and NeoForge.

Discover forgotten ruins, photograph their remains, watch the past rebuild itself, and keep the memories in a personal Field Album.

## v0.5

This repository contains the complete v0.5 multiloader project. It is not limited to NeoForge or Minecraft 26.2: every supported target uses the common gameplay and resource layer with a small Fabric or NeoForge adapter.

Supported targets:

- Fabric 26.1.2, 26.2 and 26.3
- NeoForge 26.1.2, 26.2 and 26.3

## Project layout

- `common/`: gameplay, data, resources and loader-neutral client code
- `fabric/` and `neoforge/`: loader integrations for 26.1.2
- `versions/26.2/` and `versions/26.3/`: version-specific build projects and metadata
- `.run/`: shareable IntelliJ Gradle run configurations for every client/server target and aggregate build

## Build

Use the Gradle wrapper with Java 25:

```text
gradlew assembleAllVersions
```

The combined installable jars are written to `build/all-versions/<minecraft>/<loader>/`. `assembleAll` is an alias for the same aggregate build.
