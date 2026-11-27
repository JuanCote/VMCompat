# VMCompat

OpenGL compatibility fixes and performance improvements for Project Viewpoint on Apple Silicon Macs running Project Zomboid.

For normal play, use the signed [Steam Workshop release](https://steamcommunity.com/sharedfiles/filedetails/?id=3837219118) with Project Viewpoint and ZombieBuddy. Enable VMCompat, approve its preload in ZombieBuddy and restart the game. The Workshop release contains the signed JAR. This repository contains the source.

## Source baseline

The initial source snapshot corresponds to Workshop release **1.0.20**, tested on Apple M2 Pro with Project Zomboid **42.22.2**, Project Viewpoint **0.1.7** and ZombieBuddy **2.3.4**. The preload validates supported dependency binaries before installing compatibility patches. Storm support is a proposed contribution. The current entry point uses ZombieBuddy.

## Build and test

Use macOS on Apple Silicon, Python 3, JDK 25 and a local installation of the dependencies above. The game and mod JARs are inputs from your own installation.

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
python3 tools/build_workshop.py
python3 tools/check_workshop.py
```

The build makes an unsigned development ZIP, `build/VMCompat-unsigned.zip`. The checks exercise the production patch classes in isolated fixtures and write results to `build/checks.json`. A source build needs its own valid signature to qualify for ZombieBuddy's signed preload. The Workshop signature applies to the official JAR.

| Optional variable | Meaning |
| --- | --- |
| `VMCOMPAT_GAME_JAVA_DIR` | Project Zomboid's `Contents/Java` directory with `projectzomboid.jar` and, for tests, `ZombieBuddy.jar` |
| `VMCOMPAT_VIEWPOINT_JAR` | Path to the installed `Viewpoint.jar` |
| `VMCOMPAT_OLDPOINT_JAR` | Path for the optional OldPoint check |
| `VMCOMPAT_TEST_JAVA` | Java executable for tests, defaults to the game's bundled Apple Silicon runtime |
| `VMCOMPAT_WORKSHOP_OUT` | Output directory, defaults to `build/` |

The default dependency paths point to the standard macOS Steam installation. See [CONTRIBUTING.md](CONTRIBUTING.md) for the contribution and multiplayer test workflow.

## Storm loader (experimental)

Some multiplayer servers load Project Viewpoint through [Storm](https://github.com/guspuffygit/project-zomboid-storm) instead of ZombieBuddy, and refuse clients that have ZombieBuddy in the game. `tools/build_storm.py` builds VMCompat as a Storm java mod for that case:

```sh
export VMCOMPAT_STORM_JAR="$HOME/Zomboid/storm/core/stage/<id>/storm.jar"
python3 tools/build_storm.py
```

It writes `build/VMCompatStorm-unsigned.zip`. Unpack it into `~/Zomboid/mods`, which Storm always loads, and start the game with the server's Storm launch options. ZombieBuddy's jar must not be in the game folder and the Workshop VMCompat stays disabled. `Zomboid/Lua/VMCompatStartup.txt` then reports `ACTIVE ... / Storm / ...`.

The Storm entry point is `WorkshopBootstrap.startStorm()`, called from `src/storm/java/vmcompat/storm/StormMod.java` while Storm registers its mods. It takes the `Instrumentation` from Storm's bootstrap class, skips the `mods/default.txt` and ZombieBuddy checks, limits transformation to the classes Storm's game class loader defines, and does nothing in a Storm server process. Everything after that is the shared activation path. Not ported yet: the Lua status dialog, the performance HUD and the settings keys.

## License

VMCompat code and original assets are available under the [MIT License](LICENSE). Project Zomboid, Project Viewpoint, ZombieBuddy, and their respective assets and libraries are separate dependencies with their own licenses.
