package dev.michidk.voxelvelo.network;

import dev.michidk.voxelvelo.bike.BikeEntity;
import dev.michidk.voxelvelo.bike.RiderSettings;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class BikeNetworking {
	private BikeNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(BikeStatePayload.TYPE, BikeStatePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RiderSettingsPayload.TYPE, RiderSettingsPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(BikeStatePayload.TYPE, (payload, context) -> {
			if (!payload.isSupported()) {
				return;
			}
			ServerPlayer player = context.player();
			// Only the player currently riding a bike reports for it; anything else is silently dropped.
			if (player.getVehicle() instanceof BikeEntity bike && bike.getRider() == player) {
				bike.applyRiderState(payload.state());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(RiderSettingsPayload.TYPE, (payload, context) -> {
			if (payload.isSupported()) {
				RiderSettings.set(context.player().getUUID(), RiderSettings.sanitize(payload.massKg(), payload.speedLimitKmh()));
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			RiderSettings.forget(handler.getPlayer().getUUID());
		});
	}
}
