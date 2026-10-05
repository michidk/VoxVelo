# Survival bicycles

Craft a Tire from eight Leather around a Slimeball. Craft a Bare Wheel from
five Iron Ingots and four Iron Nuggets, then combine it with a Tire to make a
Bike Wheel. Wheels and tires stack normally; tire condition is carried by each
wheel's item data, so wheels with different condition form separate stacks.

Each bicycle's crafting-table recipe is:

```text
  H
W F W
  S P
```

H = handlebars, W = Bike Wheel, F = frame, S = saddle, P = pedal set.
Mountain uses Flat Handlebars; Gravel and Race use Drop Handlebars. All parts
have ordinary vanilla-material recipes. Assembly transfers the actual frame
color and component stacks, including worn or burst tires. Race is the fastest
paved-road bike, Mountain handles loose surfaces best, and Gravel is balanced.
The old `road_bike` item and `road` entity type value remain compatible with saves;
new Race Bikes use the `race_bike` item ID.

Combine any frame or completed bicycle with one Minecraft dye in a crafting
grid. Recoloring replaces the previous frame color without changing its parts.
Use the bike item to place it. Sneak and use an empty hand to pick it up with
all installed parts, their state, and its frame color.

Use a Bike Wrench on an unoccupied bicycle to open maintenance. The first five
slots, left to right, are front wheel, rear wheel, handlebars, saddle, and pedals.
Each slot is labeled, and the screen shows tire/overall condition, repair
instructions, and disassembly instructions. Remove/install parts normally, including shift-click.
Only the correct parts fit; each installed slot holds one component.

To replace a tire, hold a Tire on the cursor and click either installed wheel.
This consumes one Tire and restores that wheel's tire without replacing its
metal component. Alternatively remove the worn wheel, craft it with a Tire,
and reinstall it. No separate damaged-wheel item is registered.

Close maintenance, then sneak and use the wrench to disassemble. This returns
the colored frame and exactly the parts that remain installed; missing parts
are never recreated. Excess returned items drop at the player. Maintenance
changes and disassembly cost one wrench durability; opening and inspecting do
not. The wrench has 256 uses.

Both wheels, appropriate handlebars, and the saddle are needed to mount.
Without pedals the bike can coast but cannot propel. A burst tire slows riding
to at most 3 blocks/second (1.5 with both burst); it never destroys the bicycle.
Bicycles have no whole-bike durability and do not break when punched.

## Server configuration

`config/voxelvelo-server.json` is created at startup. Restart the server after
changing it. In singleplayer these settings apply to the integrated server.

```json
{
  "enableTireDamage": true,
  "tireDamageRate": 0.0001,
  "roughTerrainTireDamageMultiplier": 1.75,
  "collisionTireDamage": 0.08
}
```

The rate is tire condition lost per block ridden, with full condition = 1.
Smooth riding lasts approximately 10,000 blocks per front tire; the rear wears
5% faster so failures usually occur separately. Loose terrain increases wear,
drops over three blocks and high-speed impacts add damage. Setting
`enableTireDamage` to false prevents new wear, but does not refill worn tires.
Negative/non-finite settings are sanitized. Tire state persists independently
on each wheel. The server owns inventories and wear; visible component presence,
frame color, and tire condition use normal tracked entity data.

Only one nearby player can maintain a bike at a time. Riding, pickup, and
disassembly are blocked during maintenance. The menu closes when its mechanic
loses the wrench, moves away, or the bicycle disappears. Closing/disconnecting
releases the bike; installed components remain in the entity, not the menu.

## Final asset drop-in contract

The intentionally crude render placeholders are separate from the legacy art.
Replace these files without changing registration, recipes, maintenance, or entities:

```text
assets/voxelvelo/geckolib/models/entity/bikes/mountain_bike.geo.json
assets/voxelvelo/geckolib/models/entity/bikes/gravel_bike.geo.json
assets/voxelvelo/geckolib/models/entity/bikes/race_bike.geo.json
assets/voxelvelo/textures/entity/bikes/mountain_bike.png
assets/voxelvelo/textures/entity/bikes/gravel_bike.png
assets/voxelvelo/textures/entity/bikes/race_bike.png
```

Use GeckoLib/Bedrock geometry and the existing `BikeRig.MODEL_SCALE` coordinate
convention. Keep the rider attachment positions compatible with `BikeType.rig`;
that centralized visual rig can be adjusted if supplied models have different
seat, hand, or crank positions. UV dimensions come from the supplied model.

| Bone | Behavior |
| --- | --- |
| `frame_colored` | Only this bone's cubes receive frame tint; use neutral white/gray texture pixels |
| `front_assembly` | Optional steering parent containing fork, handlebar, and front wheel |
| `fork` | Steering parent when `front_assembly` is absent |
| `handlebar` | Hidden when handlebars are removed; independently steers if outside `fork` |
| `front_wheel`, `rear_wheel` | Rolling rotation around X; hidden when removed |
| `saddle` | Hidden when removed |
| `crank` | Pedal animation; hidden with its children when pedal set is removed |
| `left_pedal`, `right_pedal` | Counter-rotation animation; parent under crank |

Put the frame geometry directly in `frame_colored`, and give other colored
components their own bones. Frame children keep their normal texture color.
Put wheel pivots at their axles and the steering parent's pivot at the head tube.
The shared animation is
`assets/voxelvelo/geckolib/animations/entity/bicycle.animation.json`.
Legacy `pedal_left`/`pedal_right` animation names also remain supported.

Replace component icon placeholders at
`assets/voxelvelo/textures/item/<item_id>.png`. The registered component IDs are
`tire`, `bare_wheel`, `bike_wheel`, `mountain_bike_frame`, `gravel_bike_frame`,
`race_bike_frame`, `flat_handlebars`, `drop_handlebars`, `bike_saddle`, `pedal_set`,
and `bike_wrench`. Item definitions and generated-model references already exist.
Frame icons use vanilla dye tint; supply grayscale frame-only icons. Completed
bike icons remain untinted because a flat PNG cannot isolate frame geometry.

## Manual multiplayer checks

Craft all three types, including one from a dyed frame and a worn wheel. Place,
pick up, save/reload, and disassemble; confirm color and wheel condition survive.
Watch another player dye/place a bike and remove parts; check visible state.
Try mounting without each required part, and coasting without pedals.
Use two players to attempt simultaneous maintenance/pickup/disassembly and
confirm that every component returns exactly once. Repair each wheel using both
the cursor and crafting paths, including with a full inventory and a wrench's
last use. Check enabled/disabled damage on a dedicated server.
