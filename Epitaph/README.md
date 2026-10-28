# Epitaph 1.0-SNAPSHOT (Minecraft 1.21.11, Fabric, Java 21)

Client-side, visual-only mod. Open the menu in game with **LEFT SHIFT + ENTER**.

## STATUS - read this first
This source was written and checked **without being able to compile against Minecraft** (the build
sandbox could not reach Fabric/Mojang servers). **No JAR has been built or launched yet.**
What *was* run and passed: a parse check of every Java file, 20 automated tests of the config loader and item
classifier, and generation + visual inspection of every texture. Everything that touches Minecraft/Fabric
classes (menu, HUD, mixin, pack registration) is unverified until your first build. Expect to possibly fix a
few Yarn method names; send me the first error block and I will fix it.

## Build the JAR (Windows)
1. Install JDK 21 (https://adoptium.net, Temurin 21). Check: `java -version`.
2. Unzip this folder somewhere simple, e.g. `C:\Projects\Epitaph`.
3. Open PowerShell in that folder and run: `.\gradlew.bat build`
4. The installable mod is `build\libs\epitaph-1.0-SNAPSHOT.jar` (NOT the `-sources` one).
If Gradle says a version does not exist, open https://fabricmc.net/develop, pick 1.21.11 and copy the
values into `gradle.properties` (`yarn_mappings`, `loader_version`, `fabric_version`, `loom_version`).

No local setup? Upload the folder to a free GitHub repo: the included workflow builds the JAR
(Actions tab -> latest run -> `epitaph-jar`).

## Install in the Modrinth App
1. Open your Minecraft 1.21.11 Fabric profile -> Settings/Open folder -> `mods`.
2. Put `epitaph-1.0-SNAPSHOT.jar` there, plus **Fabric API** (add it in the profile's Content tab; it is the only dependency).
3. Launch, join a world, press **LEFT SHIFT + ENTER**.

## Features
- Menu (EPITAPH): tabs General / Screenmates / Items / Position / HUD / Appearance, scrolling, real toggles + sliders,
  Save Settings, Reset to Defaults, Reset Item Position. Settings in `config/epitaph.json`; bad or missing file never crashes.
- Screenmates above the hotbar: BoyKisser + Mikey, each with its own on/off, side (left/centre/right), X/Y offset; shared size.
  Hidden with F1. Transparent cutouts included (BoyKisser: watermark removed, body filled).
- BoyKisser animation: frame playback from `textures/gui/screenmates/boykisser/frame_NNN.png` + `animation.json`.
  Only a still was supplied, so it currently uses a procedural dance (bounce/sway). For the real animation run
  `python tools/gif_to_frames.py your.gif --erase x0,y0,x1,y1` (erase = watermark box) and rebuild.
- Item textures (original dark violet art): swords, axes, mace, spears, totem, shield - six built-in client resource
  packs, each switched independently from the Items tab (applied on Save/Done; triggers a resource reload).
- First-person held item scale (global + shield/totem/mace/axe/sword/spear/other) and X/Y/Z offsets, plus extra
  shield offsets, via a mixin on `HeldItemRenderer`. Visual only.
- HUD: hotbar style (vanilla / dark / violet glass), selected-slot highlight, optional pulsing effects, accent colour.

## Spear mods
Anything with an id ending `_spear` (and the trident) counts as a spear. For another mod's item add its id to
`"spearItemIds"` in `config/epitaph.json`, e.g. `["minecraft:trident", "mymod:glaive"]`. To retexture it, add
`item/<name>.png` under `resourcepacks/epitaph_spears/assets/<modid>/textures/` (use that mod's own namespace).

## Known gaps / unverified
- Never compiled or launched. See STATUS.
- Held-item mixin targets `HeldItemRenderer#renderFirstPersonItem` with the 1.21.11 parameter list from memory. If the game
  fails to start, set `"enableHeldItemMixin": false` in `config/epitaph.json` (or launch with `-Depitaph.disableMixins=true`).
  The Items tab shows "Held-item hook: ACTIVE" once it runs.
- Shield texture paths and spear texture names are not verified against the 1.21.11 jar (extra copies are harmless).
- Pack registration uses Fabric API by reflection (class was renamed across versions); the log says whether it worked.
- Third-person item scaling not implemented.
- No rebindable key: the shortcut is read directly from the keyboard (LEFT SHIFT + ENTER).
