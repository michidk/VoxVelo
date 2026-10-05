# Voxelvelo final visual assets

This is an asset handoff, not a standalone Fabric mod or resource pack.

## Drop-in structure

Copy `assets/voxelvelo/` into the mod's `src/main/resources/assets/voxelvelo/`. Change the namespace consistently if the mod does not use `voxelvelo`.

- `textures/item/`: eleven final 16×16 transparent pixel-art PNGs.
- `models/item/`: matching classic `minecraft:item/generated` model JSONs. For Minecraft versions using the newer item-definition system, the implementation must also supply `assets/voxelvelo/items/*.json` definitions.
- `geo/`: `mountain_bike.geo.json`, `gravel_bike.geo.json`, `race_bike.geo.json`.
- `textures/entity/`: matching 64×64 pixel-shaded texture atlases.
- `animations/bicycle.animation.json`: shared one-second looping pedal animation.
- `blockbench/`: editable cuboid-only `.bbmodel` sources.
- `metadata/`: dimensions, seat/grip positions, rig and tint information in exported coordinates.
- `previews/`: three static model renders and a nearest-neighbor inventory contact sheet. Labels exist only on the contact sheet, never in the individual icons.

## Final rig

```text
bike
├── frame_colored
│   ├── top_tube
│   ├── down_tube
│   ├── seat_tube
│   └── rear_stays
├── fork
│   ├── handlebar
│   └── front_wheel
├── rear_wheel
├── crank
│   ├── left_pedal
│   └── right_pedal
└── saddle
```

Head tube geometry belongs directly to `frame_colored`. Seatpost belongs to `saddle`. Only `frame_colored` and its descendants should receive frame tint; traverse its children when configuring the renderer. Fork, cockpit, wheels, saddle and drivetrain remain untinted. Tintable texture regions are neutral grayscale; colored material cells elsewhere in the atlas do not indicate tint eligibility.

Forward is -Z, Y is up, ground is Y=0, 16 model units = one block. Overall length is approximately 1.97–2.01 blocks, height 1.06–1.10 blocks. The slightly shorter mountain saddle suits its upright bars; adjust rider pose, not wheel scale. All wheels share 12.6-unit outer diameter, including rotating tread. Each ring has 48 cuboid segments and each wheel has eight spokes. No meshes, `poly_mesh`, chains, bags, bottles, or branding.

`fork` has a nonzero X rest rotation aligning its local Y to the real tilted steering axis. Add local Y steering to its rest transform; do not zero its rest X. Handlebar and front wheel follow this bone. Wheels spin about local X at their exact axle pivots. Crank spins about local X at the bottom bracket. `animation.voxelvelo.bicycle.pedal` rotates crank 360° in one second and counter-rotates both pedals to keep them level. The integration may drive wheel spin and crank phase from distance/speed instead of the supplied constant-rate loop.

Metadata coordinates are model units in the exported geometry coordinate system. `saddle_top` is the seat's top-center attachment, `grips` are the two hand attachment points. Entity collision bounds and player pose are integration decisions; do not reuse the full visual length as player width without considering gameplay.

Icons were generated with the imagegen CLI (`gpt-image-2`), then reduced and cleaned up at pixel level with nearest-neighbor sampling, binary alpha and limited grayscale palettes. Prompt is in the source project's `art/icon-prompt.txt`. Final sprites are actual 16×16 artwork, not 3D render thumbnails.

Structural validation and export through Blockbench MCP passed. Automated asset tests cover rig, rest-pose preservation, tint isolation and sprite properties. A running Fabric/GeckoLib instance was not available, so runtime loading, steering and rider pose still require integration testing. Geometry uses the cuboid-only Bedrock schema appropriate for GeckoLib; the implementation owns registration and version-specific resource loading.
