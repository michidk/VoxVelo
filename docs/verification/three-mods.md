# Three-mod refactor verification — 2026-10-08

The refactor includes upstream settings work from `527dcf7` and listing assets from `d29d899`.

## Automated checks

Java 25 / Gradle 9.7.1 in Docker:

```sh
./gradlew build --no-daemon --console=plain \
  -PbleNatives=build/ci-natives \
  -PrequiredBleNatives=windows-x86_64,windows-aarch64,linux-x86_64,linux-aarch64,macos-x86_64,macos-aarch64
```

Passed, including Spotless and all `verify*` tasks. Native artifacts came from GitHub Actions run
`37804187124`. Actionlint 1.7.7 and `git diff --check` passed.

Packaging assertions verify three distinct mod IDs, matching required dependencies, entrypoint classes,
icons, no nested fitness library, no fitness/Bluetooth references in Bikes, and no Bikes/GeckoLib
references in Fitness Library. The generic boat API example compiles.

## Client smoke check

Launched the three packaged JARs with Fabric 0.19.5 / Minecraft 26.3, Xvfb and Mesa software Vulkan,
1280×720 at GUI scale 3. All three renamed mod IDs loaded, along with 16 bike models and two animations.
Visually checked the scrolling bike hub and Keyboard, Rider, Trainer, OpenBikeControl, Ride Recording,
and HUD & Rider Stats pages. Labels and controls rendered correctly; returning to the hub preserved its
scroll position. Screenshots and the launcher are outside the repository in
`/home/vibepod/workspaces/VoxVelo-three-mods-ui-check/`.

Not tested here: physical Bluetooth hardware, multiplayer, an actual ride, the in-world F8 hub, and the
singleplayer Tire Wear row. Earlier UI checks of Bluetooth and Road are documented by the upstream
settings handoff, not claimed as fresh runtime checks of this refactor.

## Publishing

Release tags build and publish three separate projects. At verification time the repository API returned
no Actions variables or repository secrets. Publishing still needs the three `MODRINTH_*_ID` variables
and `MODRINTH_TOKEN`; ordinary CI builds do not upload releases to Modrinth. No release tag was created.
