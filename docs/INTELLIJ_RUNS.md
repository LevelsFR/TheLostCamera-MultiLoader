# IntelliJ runs

Import the root `settings.gradle` as the main IntelliJ project. This is the v0.5 multiloader project, not a NeoForge-only 26.2 project. The root project contains the 26.1.2 Fabric and NeoForge modules. The `versions/26.2` and `versions/26.3` folders are independent Gradle builds that reuse `common/` and the loader adapter sources.

The shareable configurations in `.run/` contain the complete matrix:

- six client runs: Fabric and NeoForge for 26.1.2, 26.2 and 26.3;
- six server runs with `--nogui`;
- `The Lost Camera - Assemble all versions`;
- `The Lost Camera - Assemble all`.

Run matrix:

| Minecraft | Fabric project task | NeoForge project task |
|---|---|---|
| 26.1.2 | `:fabric:runClient` / `:fabric:runServer` | `:neoforge:runClient` / `:neoforge:runServer` |
| 26.2 | `versions/26.2`, `:fabric:runClient` / `:fabric:runServer` | `versions/26.2`, `:neoforge:runClient` / `:neoforge:runServer` |
| 26.3 | `versions/26.3`, `:fabric:runClient` / `:fabric:runServer` | `versions/26.3`, `:neoforge:runClient` / `:neoforge:runServer` |

The aggregate configurations launch the root `assembleAllVersions` or `assembleAll` task. The final jars are copied to `build/all-versions/` by version and loader.

The Fabric 26.3 development client task already includes `-Dorg.lwjgl.system.allocator=system`. This avoids the native LWJGL allocator crash observed on the Windows/OpenGL development machine; the option is scoped to the development run and is not packaged in the mod JAR.
