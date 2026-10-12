---
name: soulforge-build-and-verify
description: How to build, run and verify this project (D:\MDK-26.3-ModDevGradle-main, 锻魂 / SoulForge, NeoForge 26.3), including the proxy and toolchain facts that make Gradle work on this machine. Applies to this project only.
whenToUse: Read before running any Gradle task here, before claiming a build or run succeeded, and before pushing to the GitHub remote.
---

# SoulForge build, run and verification

## Commands

```
cd D:\MDK-26.3-ModDevGradle-main
.\gradlew.bat compileJava --console=plain --offline     # fastest signal
.\gradlew.bat runServer  --console=plain --offline      # headless mod-load check, ~12 s to crash or boot
.\gradlew.bat runClient  --console=plain --offline      # the client the user actually tests in
.\gradlew.bat build      --console=plain --offline      # produces build/libs/soulforge-1.0.0.jar
```

`:createMinecraftArtifacts` is the expensive task (about 342 s cold) and caches; later runs are seconds.
`--offline` works because all artifacts are already in the Gradle cache.

**Redirect Gradle output to a file, then parse it.** Console output is UTF-8 Chinese that
PowerShell 5.1 renders as mojibake, and the tool's own `read` refuses the log as binary. Write with
`cmd /c "... > build\x.log 2>&1"` and parse with an inline Node script, decoding as UTF-8 and falling
back to `latin1` when `\uFFFD` appears.

## Verify a run, do not trust the exit code

`runClient` exits **0** even when the mod fails to load, because the JVM shuts down cleanly after the
crash report. The exit code proves nothing. Grep the log for these instead:

| Signal | Meaning |
|---|---|
| `Done (1.280s)! For help, type "help"` | server reached the tick loop |
| `SoulForge common setup complete` | mod constructed and common setup ran |
| `Sound engine started` | client got past resource reload |
| `Missing item model` / `Missing FluidModel` / `Missing model for variant` | an asset layer is missing |
| `has failed to load correctly` / `ModLoadingException` | mod load failed |

A clean run of this mod has **zero** soulforge-related WARN/ERROR lines. After the asset fixes, a
`runClient` log was 292 lines with none; before, 375 lines with 108.

`runServer` is the cheap way to test mod loading, registry errors and recipe parsing without a GUI —
it reaches the same code paths and fails within seconds. Use it first, then `runClient`.

## A clean boot proves almost nothing about gameplay

"Compiles" and "boots without warnings" are both compatible with a feature being completely dead.
In this repo, a full smeltery implementation passed both while: multiblock detection never succeeded
(a furnace could not form at all), the controller exposed the wrong tank so molten metal had no
extraction path, and entities standing in any custom fluid were frozen solid.

For behaviour that only exists at runtime, add a **probe** that runs inside the real server and calls
the real code. `smeltery/multiblock/MultiblockProbe` + `MultiblockProbeRunner` are the working
example: on `ServerStartedEvent` they build a furnace at `(0, 200, 0)`, call the actual
`detectMultiblock`, break a wall and re-check, then drive the block entity's `tick`.

Gate it on an **environment variable**, and read it with `System.getenv`:

```powershell
$env:SOULFORGE_SELFTEST='true'
cmd /c "gradlew.bat runServer --offline > build\st.log 2>&1"
```

**Gradle's `-D` does not reach the forked game JVM.** `gradlew.bat runServer -Dsoulforge.selftest=true`
sets the property on Gradle itself; `System.getProperty` in mod code still returns null and the probe
silently never runs. An environment variable is inherited by the forked JVM.

Make the probe log a verdict per assertion (`OK …` / `FAIL …`) so the log is greppable, and confirm
it is inert without the variable (`selftest 行数 = 0`) before trusting a normal run.

A probe is only worth writing if it calls the production code. A self-test that re-implements the
logic it is testing passes no matter what the real code does — it is worse than no test, because it
looks like coverage.

## gradle.properties drives mod metadata

`mod_id`, `mod_name`, `mod_license`, `mod_version`, `mod_group_id` and `loader_version_range` are
substituted into `src/main/templates/META-INF/neoforge.mods.toml` by the `generateModMetadata` task
(`build.gradle`). Two traps:

- **`loader_version_range` must exist** and must be `[3,)` for 26.3 (the javafml version NeoForge
  26.3 accepts). A missing key fails the task with
  `Missing property (loader_version_range) for Groovy template expansion`. It also has to be listed
  in the `replaceProperties` map in `build.gradle`.
- **Never put literal non-ASCII in `gradle.properties`.** Gradle reads that file as ISO-8859-1, so a
  literal Chinese `mod_name` is double-encoded into mojibake on the way into the TOML. Use Java
  escapes: `mod_name=\u953b\u9b42` renders as 锻魂. This was verified byte-for-byte in the generated
  file and in the built jar.

`mod_id=soulforge` is not a preference — it must match `@Mod("soulforge")` in `SoulForge.java` and the
`assets/soulforge/` + `data/soulforge/` directories. If the template is ever left unexpanded, TOML
parsing fails immediately with:

```
com.electronwill.nightconfig.core.io.ParsingException: Invalid bare key: '${mod_id}'
- File D:/MDK-26.3-ModDevGradle-main/build/classes/java/main is not a valid mod file
```

## Proxy — maven.neoforged.net is unreachable directly

JVM proxy settings live in `C:\Users\q1505\.gradle\gradle.properties`:

```
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=10810
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=10810
systemProp.http.nonProxyHosts=localhost|127.0.0.1|0.0.0.0|[::1]|*.mojang.com|*.minecraft.net
```

- **Port 10810 is the HTTP proxy** (Whale.exe). Port 10808 is SOCKS5 (vcore.exe) and the JVM's
  `http(s).proxyHost` only speaks HTTP — pointing Gradle at 10808 fails.
- `maven.neoforged.net` is **blocked on direct connection** (measured 000). It must stay behind the
  proxy; do not add it to `nonProxyHosts`.
- Mojang hosts work direct and are flaky or return HTTP 400 through the proxy, hence the
  `*.mojang.com|*.minecraft.net` bypass.
- If the accelerator is off, Gradle reports `Connection refused` and git reports
  `Failed to connect to github.com:443 over proxy 127.0.0.1`. Check that ports 10808 **and** 10810 are
  listening before blaming anything else — a running `Whale` process does not imply a listening port.
- The proxy user is not the only non-obvious wart here: `git config --global` has
  `http.proxy` / `https.proxy` set to `socks5://127.0.0.1:10808`, so **git** uses SOCKS5 while
  **Gradle** uses HTTP on 10810. Both must be up to build and push in one session.

## Git and the GitHub remote

- Remote: `origin` = `https://github.com/cehnhaiyang/cursed-fog-tech.git`, branch `main`.
- Commit messages: write the message to a file (`build\commit-msg.txt`) and use
  `git commit -F build\commit-msg.txt`. PowerShell 5.1 does not support heredocs (`<<'EOF'`), so
  inlining a multi-line Chinese message fails with parse errors.
- Delete the message file after committing; `build/` is gitignored so it never gets staged.
- Stage with `git add -A`, then assert no `build/`, `run/`, `.gradle/`, `.idea/` path is staged before
  committing. All four are in `.gitignore`, but the assertion is what catches a bad ignore rule.
- Commit author is `The Zone <dev@thezone.local>` from the global config; commits do not link to the
  user's GitHub account.

## Do not delete files as "cleanup"

This repo carries a large amount of **untracked** work: `.dsh/skills/*`, `tools/gen_assets.mjs`,
every file added under `smeltery/module/`, `smeltery/tank/`, and the probes. `git status` shows them
as `??`, which means **there is no HEAD copy and `git checkout` cannot bring them back.**

`TinkersConstruct-3.12.1.231/` is gitignored and is a ~30k-file reference tree; never sweep it.

Deleting a "temporary" file at the end of a task destroyed two untracked probe files in this session
and they had to be reconstructed from memory. Rules that follow:

- Before `Remove-Item`, resolve the absolute path and confirm it is the file you mean. A "clean up
  the temp files" instruction is not authorization to delete source files.
- `git add` any new file as soon as it works. It costs a second and converts an irreversible loss
  into a one-line revert. Never `git add -A` — stage the specific paths.
- If you do destroy something untracked, say so plainly and rebuild it, then re-verify that the
  rebuild reproduces the previous output. Do not present the reconstruction as the original.
- `build/` is gitignored, so logs and extracted jars there are genuinely disposable. That is the only
  directory where a blanket delete is safe.

## Asset and data file safety

`src/main/resources/assets/soulforge/` and `data/soulforge/` are UTF-8 and must stay that way. Read,
edit and write them only with the built-in tools or a Node script using
`fs.readFileSync(p, 'utf8')` / `fs.writeFileSync(p, s, 'utf8')`. Never `Get-Content | Set-Content`,
which re-encodes as GBK and destroys the Chinese lang files. `gradlew.bat`, `gradle.properties` and
`.gitignore` use LF; git reports `LF will be replaced by CRLF` warnings on commit, which are benign.

## The jar is not the way to test

`build/libs/soulforge-1.0.0.jar` is a valid artifact, but for iterating use `runClient`: ModDevGradle
wires `build/classes/java/main` and `build/resources/main` into the game directly via the
`neoForge.mods { "${mod_id}" { sourceSet(sourceSets.main) } }` block in `build.gradle`. Loading the jar
into a separate game install is a different, slower loop.

## `TinkersConstruct-3.12.1.231/` is the design reference

The tree is TiC3 3.12.1.231 (MIT), gitignored, and the stated design source for this mod. Use it to
settle **mechanism** questions — how a smeltery finds fuel, what a multiblock scan does, how the GUI
is laid out — and read the corresponding `smeltery/**` classes there.

It targets 1.21.1/Forge: `net.minecraftforge.*`, `lombok`, `slimeknights.mantle.*` and
`ResourceLocation` are all present in it and none resolve in this project. Copy the algorithm and the
constants, never the imports.

Two facts that are easy to get wrong when porting from it:

- Its `FluidType` recipes (`motionScale`, `canSwim(false)`) encode 1.21.1 fluid semantics. See
  `soulforge-neoforge-26.3-api` for what 26.3 requires instead.
- Its per-block-entity `ServantBlockEntity` callback design was deliberately **not** ported here;
  see `soulforge-smeltery-multiblock` for the periodic-rescan replacement and why.
