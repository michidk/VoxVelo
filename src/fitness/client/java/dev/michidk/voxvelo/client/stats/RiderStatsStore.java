package dev.michidk.voxvelo.client.stats;

import dev.michidk.voxvelo.network.RiderStatsPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The latest watts and heart rate other riders have shared, as received from the server. Entries expire quickly. */
public final class RiderStatsStore {
	/** The server sends twice a second; a rider not heard from for this long is no longer shown. */
	private static final long FRESH_MILLIS = 3_000;

	/** @param watts -1 when the rider does not share (or is not riding); @param heartRate 0 when unknown or private */
	public record Stats(UUID player, int watts, int heartRate, float speedKmh, int cadence, int gear, long receivedAt) {}

	private final Map<UUID, Stats> byPlayer = new ConcurrentHashMap<>();

	public void update(RiderStatsPayload payload) {
		long now = System.currentTimeMillis();
		this.byPlayer.clear();
		for (RiderStatsPayload.Entry entry : payload.entries()) {
			int watts = entry.watts() == RiderStatsPayload.NO_WATTS ? -1 : entry.watts();
			this.byPlayer.put(entry.player(), new Stats(entry.player(), watts, entry.heartRate(), entry.speedKmh(), entry.cadence(), entry.gear(), now));
		}
	}

	/** Fresh entries, strongest first. */
	public List<Stats> fresh() {
		long now = System.currentTimeMillis();
		List<Stats> list = new ArrayList<>();
		this.byPlayer.values().removeIf(stats -> now - stats.receivedAt() > FRESH_MILLIS);
		list.addAll(this.byPlayer.values());
		list.sort((a, b) -> Integer.compare(b.watts(), a.watts()));
		return list;
	}

	public void clear() {
		this.byPlayer.clear();
	}
}
