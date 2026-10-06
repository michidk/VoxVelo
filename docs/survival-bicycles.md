# Survival bicycles

There are three tires, all around a Slimeball:

| Tire | Recipe | Character |
| --- | --- | --- |
| Road Tire | four Leather in a plus | fast on hard ground, slips and drags on loose ground |
| Gravel Tire | eight Leather | the all-rounder |
| Mountain Tire | four Leather in a plus, Flint in the corners | grips loose ground, drags on hard ground |

Craft a Bare Wheel from five Iron Ingots and four Iron Nuggets, then combine it
with any tire to make a Bike Wheel with that tire. The wheel's icon and tooltip
show its tire. Wheels and tires stack normally; tire and tire condition are
carried by each wheel's item data, so different wheels form separate stacks.

Each bicycle's crafting-table recipe is:

```text
  H
W F W
  S P
```

H = handlebars, W = Bike Wheel, F = frame, S = saddle, P = pedal set.
Any handlebars and any wheels fit any frame (see [Mixing parts](#mixing-parts)).
A Mountain Bike comes with Flat Handlebars, Gravel and Race with Drop
Handlebars. All parts have ordinary vanilla-material recipes. Assembly transfers the actual frame
color and component stacks, including worn or burst tires. Race is the fastest
paved-road bike, Mountain handles loose surfaces best, and Gravel is balanced.
The old `road_bike` item and `road` entity type value remain compatible with saves;
new Race Bikes use the `race_bike` item ID.

Combine any frame or completed bicycle with one Minecraft dye in a crafting
grid. Recoloring replaces the previous frame color without changing its parts.
Use the bike item to place it. Sneak and use an empty hand to pick it up with
all installed parts, their state, and its frame color.

Placed bicycles have no inventory or maintenance screen. Their assembly is
fixed until they are dismantled. Left-click an unoccupied bike with a Bike
Wrench to repair all whole-bike damage; right-click it with the wrench to
dismantle it. Dismantling drops the colored frame and every installed wheel,
handlebar, saddle and pedal set into the world, preserving tire state. Both
actions cost one wrench durability. The wrench has 256 uses.

To change parts or repair a tire, dismantle the bike, craft a wheel with the
desired tire, and assemble the bicycle again. No separate damaged-wheel item
is registered.

Both wheels, handlebars, and the saddle are needed to mount.
Without pedals the bike can coast but cannot propel. A burst tire slows riding
to at most 3 blocks/second (1.5 with both burst); it never destroys the bicycle.
Bicycles also have whole-bike durability: race bikes have 60, gravel bikes 80,
and mountain bikes 100. Hard crashes and falls over three blocks damage them.
At zero condition the bike is destroyed and drops its frame and every installed
part. Sneak-use with an empty hand still picks up the complete bike, preserving
its damage and parts.

An assembled bike can be enchanted with Speed Boost I–III at an enchanting
table or by applying an enchanted book in an anvil. Each level adds 10% to its
top speed, before the rider's personal speed limit is applied. The enchantment
survives placement, world saves and pickup. It is lost when a bike is
dismantled or destroyed because those actions drop the separate physical parts.

## Mixing parts

The frame decides the bike's mass, top speed, drivetrain, wheelbase, ledge
handling and suspension. The tires and handlebars bring their own handling, so
gravel tires on a race frame or drop bars on a mountain frame work as expected:

- **Tires** set the rolling resistance on hard and on loose ground and the grip
  on loose ground. Each tire uses the tuning of the bike it comes with, and the
  front and rear tire count half each, so mixed tires sit in between.
- **Handlebars:** flats are upright (+0.06 m² drag area) and give more steering
  lock (+5° at walking pace, +1.5° at speed) than drops.

A stock bike rides exactly as its type always has. Swapped parts are drawn with
the other bike's wheel or handlebar model, and the rider's hands move to the
fitted handlebars. Wheels from before tires had a tread count as gravel wheels;
wheels already installed on a bike keep the tire of that bike's type.

## Bike helmet

Craft a Bike Helmet from Iron Nugget, any Wool, Iron Nugget on top and String in
the two lower corners. Wear it in the head slot: it is light armor (1 armor
point, like a leather cap, repaired with wool) and halves the damage the rider
takes from crashes on the bike (walls and heavy hits). Falls are unchanged. Dye
it like leather armor; only the shell takes the color. A cauldron washes the
dye out.

The model is `geckolib/models/armor/bike_helmet.geo.json` with its texture in
`textures/armor/bike_helmet.png`. It is the handoff model with the root bone
renamed to `armorHead` and everything raised 24 units, so the pivot sits at
the biped neck where GeckoLib's armor renderer expects it. The editable source
is `models/final/bike_helmet.bbmodel` (head-local, pivot at Y 0).

## Server configuration

`config/voxelvelo-server.json` is created at startup. Restart the server after
changing it. In singleplayer these settings apply to the integrated server.

```json
{
  "enableVehicleDamage": true,
  "vehicleDamageInCreativeMode": false,
  "enableTireDamage": true,
  "tireDamageRate": 0.0001,
  "roughTerrainTireDamageMultiplier": 1.75,
  "collisionTireDamage": 0.08
}
```

`enableVehicleDamage` controls crash and fall damage to the whole bicycle.
Creative riders are exempt by default; set `vehicleDamageInCreativeMode` to
true to make their bikes take damage too. The tire rate is condition lost per
block ridden, with full condition = 1.
Smooth riding lasts approximately 10,000 blocks per front tire; the rear wears
5% faster so failures usually occur separately. Loose terrain increases wear,
drops over three blocks and high-speed impacts add damage. Setting
`enableTireDamage` to false prevents new wear, but does not refill worn tires.
Negative/non-finite settings are sanitized. Tire state persists independently
on each wheel. The server owns installed-part state, whole-bike damage and tire
wear; visible component presence, frame color and tire condition use normal
tracked entity data.

## Final asset drop-in contract

The intentionally crude render placeholders are separate from the legacy art.
Replace these files without changing registration, recipes, wrench interactions, or entities:

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
The `front_wheel`, `rear_wheel` and `handlebar` bones are also drawn on the other
frames when those parts are swapped: each is moved so its pivot lands on the
same bone's pivot of the other model, so keep wheel pivots at the axle and the
handlebar pivot at the top of the head tube (`BikeRig.headY`/`headZ`).
The shared animation is
`assets/voxelvelo/geckolib/animations/entity/bicycle.animation.json`.
Legacy `pedal_left`/`pedal_right` animation names also remain supported.

Replace component icon placeholders at
`assets/voxelvelo/textures/item/<item_id>.png`. The registered component IDs are
`road_tire`, `tire` (gravel), `mountain_tire`, `bare_wheel`, `bike_wheel` (plus `bike_wheel_road` and
`bike_wheel_mountain`, picked by the wheel's tire), `mountain_bike_frame`, `gravel_bike_frame`,
`race_bike_frame`, `flat_handlebars`, `drop_handlebars`, `bike_saddle`, `pedal_set`,
and `bike_wrench`. Item definitions and generated-model references already exist.
Frame icons use vanilla dye tint; supply grayscale frame-only icons. Completed
bike icons remain untinted because a flat PNG cannot isolate frame geometry.

## Manual multiplayer checks

Craft all three types, including one from a dyed frame and a worn wheel. Place,
pick up, save/reload, and dismantle; confirm color, vehicle damage and wheel
condition survive. Crash and drop each type, confirm race wears out first and
mountain last, then repair by left-clicking with a wrench. Right-click with the
wrench and confirm every component drops exactly once, including with two
players acting together and with the wrench's last use. Check vehicle damage
enabled/disabled and the creative-mode exemption on a dedicated server.
