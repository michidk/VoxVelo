# Race bicycle

Simplified from the supplied white/red bicycle reference with thicker round
tires, eight spokes per wheel, square frame tubes without chamfers, blocky drop
handlebars and a rectangular saddle. Chain, cassette, derailleur and fine
mechanical details are omitted.

Source: `racebike.bbmodel`. Textures are embedded.
Regenerate from the project root with `node scripts/build-racebike.mjs`.
Preview: `renders/racebike-equal-wheels-clearance-v2.png`.

The front wheel pivots at [10.5, 7.5, 0], the rear wheel at [-12, 7.5, 0].
Rotate their independent groups around Z to spin them. The crankset pivots
at [-2, 6.3, 0]; frame and fork remain stationary.
Frame geometry uses approximately 73-degree seat/head angles, a shorter head
tube, a lower bottom bracket and forward fork offset.
No animation clip has been authored yet.

This is a generic Blockbench mesh model, not a Minecraft-ready export.
Round mesh tires require a compatible game/model pipeline; vanilla Java
block/item JSON and standard Bedrock cube geometry do not support these meshes
directly. Choose the target integration before exporting or converting.

The preview uses the included CPU renderer because the installed GPU renderer
could not acquire a compatible adapter. It shows geometry with material-color
lighting, rather than texture sampling. The CPU renderer currently assumes
unrotated parent groups and is for the static preview, not animated poses.

Validation covers hierarchy references, missing nodes and degenerate geometry;
it does not prove compatibility with any Minecraft runtime.
