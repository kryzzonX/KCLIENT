# K Client (Fabric)

Right Shift opens the K Client click GUI (HUD / PvP / Crosshair / Settings). No hacks.

- `common/`  shared code (GUI, HUD, crosshair, config). No Minecraft classes.
- `yarn/`    Minecraft 1.21.2 - 1.21.8 and 1.21.11 (Yarn names). One jar per Minecraft version.
             Variants: `src-gfx/3d|2d` (matrix type), `src-entry/old|new` (1.21.9+ input and keybinding API).
- `v26/`     Minecraft 26.1 - 26.3 (Mojang names, unobfuscated).

Build locally, for example:
  cd yarn && gradle build -Pminecraft_version=1.21.4 -Pyarn_mappings=1.21.4+build.8 -Pfabric_version=0.110.5+1.21.4
The GitHub workflow builds every version in a matrix and bundles the jars into the launcher.

Not covered: 1.14 - 1.21.1 and 1.21.9 - 1.21.10.
