package dev.michidk.voxelvelo.network;

import dev.michidk.voxelvelo.bike.BikeEntity;
import dev.michidk.voxelvelo.bike.RiderSettings;
import dev.michidk.voxelvelo.bike.RiderVitals;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class BikeNetworking {
	/** The rider list is sent twice a second; a rider's numbers change slowly enough for that. */
	private static final int STATS_INTERVAL_TICKS = 10;

	private BikeNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(BikeControlPayload.TYPE, BikeControlPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RiderSettingsPayload.TYPE, RiderSettingsPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RiderVitalsPayload.TYPE, RiderVitalsPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RiderStatsPayload.TYPE, RiderStatsPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(BikeControlPayload.TYPE, (payload, context) -> {
			if (!payload.isSupported()) {
				return;
			}
			ServerPlayer player = context.player();
			// Only the player currently seated on a bike can steer it; anything else is silently dropped.
			if (player.getVehicle() instanceof BikeEntity bike && bike.getRider() == player) {
				bike.applyRiderControl(payload.sequence(), payload.control());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(RiderSettingsPayload.TYPE, (payload, context) -> {
			if (payload.isSupported()) {
				RiderSettings.set(context.player().getUUID(), RiderSettings.sanitize(payload.massKg(), payload.speedLimitKmh()));
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(RiderVitalsPayload.TYPE, (payload, context) -> {
			if (payload.isSupported()) {
				RiderVitals.set(context.player().getUUID(),
					RiderVitals.sanitize(payload.flags(), payload.heartRate(), context.server().getTickCount()));
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			RiderSettings.forget(handler.getPlayer().getUUID());
			RiderVitals.forget(handler.getPlayer().getUUID());
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % STATS_INTERVAL_TICKS == 0) {
				broadcastStats(server);
			}
		});
	}

	/** Sends every player the watts and heart rate of the riders in their dimension, as far as those riders share. */
	private static void broadcastStats(MinecraftServer server) {
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		if (players.isEmpty()) {
			return;
		}
		int tick = server.getTickCount();
		List<RiderStatsPayload.Entry> entries = new ArrayList<>();
		List<ServerPlayer> entryPlayers = new ArrayList<>();
		for (ServerPlayer player : players) {
			RiderVitals.Vitals vitals = RiderVitals.fresh(player.getUUID(), tick);
			// Without any vitals yet the rider is assumed to share watts (the mod is installed, they are riding).
			boolean shareWatts = vitals == null || vitals.shareWatts();
			int watts = RiderStatsPayload.NO_WATTS;
			if (shareWatts && player.getVehicle() instanceof BikeEntity bike && bike.getRider() == player) {
				watts = (int) Math.round(Math.min(2000.0, Math.max(0.0, bike.getAppliedPower())));
			}
			int heartRate = vitals != null && vitals.shareHeartRate() ? vitals.heartRate() : RiderStatsPayload.NO_HEART_RATE;
			if (watts != RiderStatsPayload.NO_WATTS || heartRate != RiderStatsPayload.NO_HEART_RATE) {
				entries.add(new RiderStatsPayload.Entry(player.getUUID(), watts, heartRate));
				entryPlayers.add(player);
			}
		}
		if (entries.isEmpty()) {
			return;
		}
		for (ServerPlayer receiver : players) {
			if (!ServerPlayNetworking.canSend(receiver, RiderStatsPayload.TYPE)) {
				continue;
			}
			List<RiderStatsPayload.Entry> visible = new ArrayList<>();
			for (int i = 0; i < entries.size(); i++) {
				if (entryPlayers.get(i).level() == receiver.level()) {
					visible.add(entries.get(i));
				}
			}
			if (!visible.isEmpty()) {
				ServerPlayNetworking.send(receiver, RiderStatsPayload.of(visible));
			}
		}
	}
}
