# Track generation performance

Measured on 2026-10-06 with Minecraft 26.3, Fabric Loader 0.19.5, Java 25 and a 2 GiB server heap.
Baseline: `b455b40`. Each version used a separate fresh world with seed `824671`, view/simulation distance 2,
no connected players, and `pause-when-empty-seconds=0`. These are individual samples on a shared development
host, not benchmark medians or a promise for other hardware. Times are the sum of the existing phase logs;
they exclude initial route rasterization.

| Scenario | Survey before / after | Grading before / after | Placement before / after | Total before / after |
| --- | ---: | ---: | ---: | ---: |
| Fresh 1 km, X/Z = 10000 | 9.947 / 8.426 s | 0.268 / 0.181 s | 3.045 / 1.573 s | 13.260 / 10.180 s |
| Preloaded 0.5 km, X/Z = 20000 | 0.010 / 0.005 s | 0.035 / 0.036 s | 0.721 / 0.766 s | 0.766 / 0.807 s |
| Fresh 1 km, X/Z = -10000 | 8.049 / 9.152 s | 0.156 / 0.079 s | 2.598 / 1.203 s | 10.803 / 10.434 s |

The fresh-terrain builds finished about 23% and 3% sooner. Surveying still dominates, and the second survey
was slower; placement improved substantially on both fresh routes. The preloaded case stayed near 0.8 s,
with a 41 ms regression in this sample. Both baseline fresh-terrain builds logged server-overload warnings;
neither optimized fresh-terrain build did. Absence of that warning does not establish a worst-case tick latency.

## Implementation

- Group road and shoulder columns by chunk, preserving first-visit order and visiting every column exactly once.
- Request up to 32 chunks ahead with nonpersistent loading tickets. Only the active job acquires tickets.
  Minecraft may load additional dependency chunks for generation.
- Reuse the loaded `LevelChunk` for height and block reads. World writes retain `Level.setBlock` and
  `Block.UPDATE_ALL`, including normal lighting, neighbor and client updates.
- Pump available chunk-loading callbacks within the existing 20 ms work budget, briefly yielding to generation
  workers when there is no callback ready. Only join completed futures. Checking readiness just once per tick
  was slower in the negative-coordinate case; an eight-chunk window was also insufficient in that sample.
- Release each ticket after its batch, and release outstanding tickets on completion, failure or server shutdown.
- Start survey timing when the job first runs so time waiting behind another job is not counted as survey work.

The budget is checked between operations; an individual Minecraft operation can still exceed it. Grading
continues to operate on a snapshot in the background. Adaptive tick budgets and suppression of block updates
are separate follow-ups, not part of this change.

## Reproduction

Use a disposable world for each version; never run the baseline and new version sequentially on the same
edited terrain. Run these console commands in order, waiting for each track to finish:

```text
execute positioned 10000 80 10000 run voxelvelo track 1
forceload add 19808 19920 20031 20063
```

Wait for the force-load completion message and let the server settle before the preloaded case:

```text
execute positioned 20000 80 20000 run voxelvelo track 0.5
forceload remove all
execute positioned -10000 80 -10000 run voxelvelo track 1
stop
```

Compare `Track survey took`, `Track grading took` and `Track placement took` in `logs/latest.log`.
The explicit force-load command itself can stall the server; exclude that warning from the track comparison.

Verification covers positive and negative chunk boundaries, complete road coverage, deterministic chunk order,
unchanged planned heights after reordering, and the existing track geometry/grade checks. Saved-world checks
compare all 16,710 road columns from these routes: identical road surface heights, continuous solid foundations,
and five air blocks above the road. Live block updates can change foundation depth, so this is not a claim of
bit-for-bit world equality.
