# K Client (Fabric)

Right Shift opens the K Client click GUI (HUD / PvP / Crosshair / Settings). No hacks.

- `common/`  shared code (GUI, HUD, crosshair, config). No Minecraft classes.
- `v1214/`   Minecraft 1.21.4 build (Yarn mappings, Java 21, Gradle 8.12)
- `v263/`    Minecraft 26.3 build (Mojang names, unobfuscated, Java 25, Gradle 9.6)

Each folder builds on its own: `gradle build` in v1214 or v263.
