# Third-party notices

VoxVelo's own code is licensed under the MIT License (see `LICENSE_voxel_fitness` in the mod jar).

The fitness jar (`voxel-fitness-<version>.jar`) bundles the following third-party code. It keeps its own
licences, whose full texts are included in this directory. All of them are permissive (MIT, Apache-2.0,
BSD-3-Clause) and allow commercial use. The bikes jar (`voxvelo-bikes-<version>.jar`) bundles no third-party code.

| Library | Version | Licence | Licence text | Project |
|---|---|---|---|---|
| btleplug and its Rust dependencies, compiled into `natives/*/voxvelo_ble` | 0.13 | BSD-3-Clause; dependencies MIT or Apache-2.0 | `voxvelo_ble-THIRD-PARTY.txt` | https://github.com/deviceplug/btleplug |
| JmDNS (`org.jmdns:jmdns`) | 3.6.3 | Apache License, Version 2.0 | `JmDNS-LICENSE.txt` | https://github.com/jmdns/jmdns |

## btleplug

Bluetooth LE access for smart trainers, power meters, heart rate sensors and OpenBikeControl devices, through
WinRT on Windows, BlueZ on Linux and CoreBluetooth on macOS. VoxVelo's own small native library (`native/ble`
in the source repository, MIT) wraps it and is built for Windows, Linux and macOS. `voxvelo_ble-THIRD-PARTY.txt`
lists every crate compiled into it, with its licence text.

## JmDNS

mDNS/DNS-SD discovery of OpenBikeControl devices on the local network.
Licensed under the Apache License, Version 2.0: https://www.apache.org/licenses/LICENSE-2.0
