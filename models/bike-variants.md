# Gravel and mountain bikes

Build with:

```sh
node scripts/build-racebike.mjs gravelbike
node scripts/build-racebike.mjs mountainbike
```

Each model has embedded textures, eight spokes per wheel, independent
`front_wheel` / `rear_wheel` groups with axle-centered Z rotation pivots,
and a separate crankset. No animation clips are authored yet.

Gravel: tan square frame, orange accents, tan sidewalls, moderately wide
treaded tires, drop handlebars and a rigid fork.
Mountain: green sloping frame, wider knobby tires, flat bars and a suspension
fork with gold stanchions and black lowers.

Sources are `gravelbike.bbmodel` and `mountainbike.bbmodel`.
Previews are `renders/gravelbike-equal-wheels-clearance-v2.png` and
`renders/mountainbike-equal-wheels-clearance-v2.png`.

All three bikes share a 15-unit outer wheel diameter, including tread, and a
7.5-unit axle height. Tire widths vary. Rear stays remain wide past the tire
before converging into the frame. Run `node scripts/check-wheel-clearance.mjs`
to sample frame surfaces against tire/rim volumes and the rotating tread
envelope; this is a sampled clearance check, not an exact solid intersection.

These are generic Blockbench mesh assets. Minecraft export requires a
mesh-compatible target integration. Previews use the static CPU renderer;
structural validation does not prove game-runtime compatibility.
