# Generic fitness verification — 2026-10-08

Validated with Java 25, Minecraft 26.3 / Fabric 0.19.5 in Docker, Xvfb and Mesa software Vulkan.
The launches used packaged jars, excluded Gradle source outputs and external JmDNS, and loaded the bundled
nested JmDNS jar. Generic-only and boat launches used fitness's own Loom Minecraft jars without GeckoLib's
injected interfaces, and excluded GeckoLib entirely.

| Check | Result |
| --- | --- |
| `:build :bikes:build :fitness:build`, default configuration cache | Passed |
| Existing bike/recipe/model/track/road/device/session/FIT checks | Passed |
| Generic API registration, identity release and invalid measurements | Passed |
| Independent fitness and optional boat example compilation without bikes | Passed |
| Artifact ownership, no duplicate classes/resources, exact adapter dependencies | Passed |
| Prebuilt Bluetooth resource path relative to repository root | Passed |
| Packaged generic fitness alone | World loaded, F8 settings and device screens opened, clean exit |
| Packaged bikes alone | Bicycle mounted and moved about 7 metres with keyboard input, clean exit |
| All three production jars | Bicycle HUD showed 200 W and gear 7; pedaling/braking and recording worked |
| Bicycle FIT export | 0.06 km / 20 seconds, valid FIT signature, length and CRC |
| Generic fitness + optional boat example, without bikes/GeckoLib | Boat HUD, 0.03 km recording and FIT export worked, clean exit |
| Adapter missing generic fitness | Fabric correctly rejected startup with the required version |
| Adapter missing bikes | Fabric correctly rejected startup with the required version |

The bicycle adapter's map button was available with its integrated server; generic-only recording correctly
had map export disabled. Map geometry/route thinning were covered by the existing ride checks.

Physical Bluetooth trainers/sensors are unavailable in this environment. Native Bluetooth libraries are not
built locally without Rust; CI still bundles the six platform libraries through `-PbleNatives` and verifies
that they reside in the generic fitness jar. Hardware connections, telemetry preference, reconnection, shared
connections, FTMS command responses/timeouts, virtual shifting and limits were checked with fake devices.
