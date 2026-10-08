# VoxVelo Bikes

Ride Minecraft on road, gravel and mountain bikes. Craft your bike from parts, dye it to your favorite color, and explore with power-based riding physics and animated multiplayer riders.

![Road, gravel and mountain bikes](https://raw.githubusercontent.com/michidk/VoxVelo/main/.github/screenshots/01-bike-lineup.png)

## Features

- Three bike types with different speed, grip, braking and suspension.
- Craftable frames, wheels, tires, handlebars, saddles, pedals and helmets. Recipes appear in the vanilla recipe book.
- Dyeable bikes and frames, Speed Boost enchantments, and a wrench for repairs and dismantling.
- Terrain-aware physics: slopes, surface resistance, air drag, one-block steps, falls and collisions.
- Multiplayer riding with synchronized steering, spinning wheels and cranks, and a posed rider.
- Rebindable keyboard controls and cycling statistics.
- A terrain-following cycling track generator with bridges, tunnels, slab slopes and lit borders.

![An animated rider wearing a bike helmet](https://raw.githubusercontent.com/michidk/VoxVelo/main/.github/screenshots/04-rider.png)

## Installation and riding

Install Fabric Loader, [Fabric API](https://modrinth.com/mod/fabric-api), [GeckoLib](https://modrinth.com/mod/geckolib), and a compatible VoxVelo Bikes release. Use the Minecraft and dependency versions listed for your download. Bikes works in singleplayer, on LAN and on dedicated servers.

Craft a bike or find one in the creative Tools & Utilities tab. Right-click a block to place it, then right-click the bike to mount.

| Action | Default key |
| --- | --- |
| Pedal | W / Up |
| Steer | A / D or Left / Right |
| Brake | S / Down |
| Hard brake | Left Alt |
| Dismount | Shift |
| Settings | B |

Sneak-use a bike with an empty hand to pick it up. Change bindings in Minecraft's Controls settings.

## Build a cycling track

Operators can run `/voxvelo track` to generate a closed loop through the landscape. See the [track generator documentation](https://github.com/michidk/VoxVelo#cycling-track-generator) for length, material, width and grade options.

**Back up your world first:** track generation replaces terrain and has no undo.

![A generated cycling track bridging a river valley](https://raw.githubusercontent.com/michidk/VoxVelo/main/.github/screenshots/05-race-track.png)
![A generated cycling track crossing a hill](https://raw.githubusercontent.com/michidk/VoxVelo/main/.github/screenshots/06-race-track.png)

## Pedal a real smart trainer

Keyboard riding needs only Bikes and its normal dependencies. For smart trainer input, terrain resistance, fitness recording and road following, also install matching releases of [VoxVelo Fitness Library](https://modrinth.com/project/voxvelo-fitness-lib) and [VoxVelo Fitness](https://modrinth.com/project/voxvelo-fitness).

The three projects share a repository. Older releases may still use the `voxvelo` mod ID; follow the installation instructions for the release you download.

[Documentation and recipes](https://github.com/michidk/VoxVelo#readme) · [Source code](https://github.com/michidk/VoxVelo) · [Report an issue](https://github.com/michidk/VoxVelo/issues)

VoxVelo's own code and assets are licensed under the [MIT License](https://github.com/michidk/VoxVelo/blob/main/LICENSE). Minecraft is a trademark of Mojang AB. VoxVelo is not affiliated with Mojang or Microsoft.
