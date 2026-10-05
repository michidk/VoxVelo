# Asset validation

All three final sources passed Blockbench MCP's outliner, unlisted-node and degenerate-cuboid gates with zero errors and warnings. Bedrock export skipped no elements.

Automated checks (`scripts/test-final-assets.mjs`) passed for all three bikes:

- Cuboid-only schema and exact resource identifiers.
- Required bone hierarchy, steering rest alignment and axle pivot centering.
- Frame tint isolation and atlas UV bounds.
- Final scale and identical outer wheel diameter.
- 48 tire segments and eight spokes per wheel.
- Preservation of the intended rest geometry after steering-space conversion.
- Sampled frame/fork surfaces clear of the rotating tire envelope.
- One-second crank loop and inverse pedal rotations.

All eleven inventory sprites passed size (16×16), binary alpha, transparent edge padding, limited grayscale palette and distinct frame-silhouette checks (`scripts/test-final-icons.py`).

Static previews were rendered and visually inspected. These are asset-level checks, not a claim of in-game compatibility testing: the implementation must test resource loading and rider animation in its actual Fabric/Minecraft/GeckoLib versions.
