package dev.michidk.voxvelo.fitness;

import dev.michidk.voxvelo.bike.BikeControlState;
import dev.michidk.voxvelo.bike.BikeEntity;
import dev.michidk.voxvelo.bike.RiderVitals;
import dev.michidk.voxvelo.network.RideMapPayload;
import dev.michidk.voxvelo.network.RiderStatsPayload;
import dev.michidk.voxvelo.network.RiderVitalsPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Packets of the fitness integration: riders report heart rate and what they share, the server relays power and
 * pulse, and riders can have a recorded ride drawn on a map.
 */
final class FitnessNetworking {
	/** The rider list is sent twice a second; a rider's numbers change slowly enough for that. */
	private static final int STATS_INTERVAL_TICKS = 10;

	private FitnessNetworking() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(RiderVitalsPayload.TYPE, RiderVitalsPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RiderStatsPayload.TYPE, RiderStatsPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RideMapPayload.TYPE, RideMapPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(RiderVitalsPayload.TYPE, (payload, context) -> {
			if (payload.isSupported()) {
				RiderVitals.set(context.player().getUUID(),
					RiderVitals.sanitize(payload.flags(), payload.heartRate(), payload.cadence(), context.server().getTickCount()));
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(RideMapPayload.TYPE, (payload, context) -> {
			if (payload.isSupported()) {
				RideMaps.handle(payload, context.player(), context.server());
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			RiderVitals.forget(handler.getPlayer().getUUID());
			RideMaps.forget(handler.getPlayer().getUUID());
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			RideMaps.tick(server);
			if (server.getTickCount() % STATS_INTERVAL_TICKS == 0) {
				broadcastStats(server);
			}
		});
	}

	/** Sends every player the current bike riders, respecting watts and heart-rate sharing. */
	private static void broadcastStats(MinecraftServer server) {
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		if (players.isEmpty()) {
			return;
		}
		int tick = server.getTickCount();
		List<RiderStatsPayload.Entry> entries = new ArrayList<>();
		for (ServerPlayer player : players) {
			if (!(player.getVehicle() instanceof BikeEntity bike) || bike.getRider() != player) {
				continue;
			}
			RiderVitals.Vitals vitals = RiderVitals.fresh(player.getUUID(), tick);
			// Without any vitals yet the rider is assumed to share watts (the mod is installed, they are riding).
			boolean shareWatts = vitals == null || vitals.shareWatts();
			int watts = RiderStatsPayload.NO_WATTS;
			if (shareWatts) {
				watts = (int) Math.round(Math.min(BikeControlState.MAX_POWER_WATTS, Math.max(0.0, bike.getAppliedPower())));
			}
			int heartRate = vitals != null && vitals.shareHeartRate() ? vitals.heartRate() : RiderStatsPayload.NO_HEART_RATE;
			int cadence = vitals != null && vitals.cadence() >= 0 ? vitals.cadence()
				: bike.isPedaling() ? Math.round(bike.getCadenceRevsPerSecond() * 60.0F) : 0;
			entries.add(new RiderStatsPayload.Entry(player.getUUID(), watts, heartRate, bike.getSpeed() * 3.6F, cadence, bike.getGear()));
		}
		for (ServerPlayer receiver : players) {
			if (!ServerPlayNetworking.canSend(receiver, RiderStatsPayload.TYPE)) {
				continue;
			}
			List<RiderStatsPayload.Entry> others = entries.stream()
				.filter(entry -> !entry.player().equals(receiver.getUUID()))
				.toList();
			ServerPlayNetworking.send(receiver, RiderStatsPayload.of(others));
		}
	}
}
