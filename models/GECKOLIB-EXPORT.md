# Voxelvelo bicycle assets

Copy each .geo.json into your mod's geometry resources, its matching PNG into textures, and bicycle.animation.json into animations. Select animation.voxelvelo.bicycle.pedal for the one-second looping crank animation. Pedal bones counter-rotate to stay level.

Geometry contains cuboids only: no meshes or poly_mesh. Each tire, rim and sidewall uses 48 segments. Wheels retain eight spokes and a shared 15-unit diameter (including tread). Coordinate system: forward -Z, Y up, 16 units per block, ground Y=0.

Hierarchy: frame → rear_wheel, front_assembly, crank. front_wheel is under front_assembly; pedal_left and pedal_right are under crank. Rotate wheels around their axle pivots on X. Steering must rotate front_assembly around the tilted steering_axis in metadata, rather than world Y; the rest bone itself is not tilted.

Per-bike metadata uses exported geometry coordinates, and contains saddle top, both grips, steering pivot/axis, and measured rest bounds in model units and blocks. Preserve model size and choose the entity hitbox separately.

Editable cuboid sources and static previews are included alongside the original mesh sources. Structural validation and cuboid export passed; actual rendering/animation inside GeckoLib 5.5.7 has not been tested in a running mod.
