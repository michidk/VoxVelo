package dev.michidk.voxvelo.bikes.network;

import dev.michidk.voxvelo.bikes.bike.BikeEntity;
import dev.michidk.voxvelo.bikes.bike.RiderSettings;
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
			BikeEntity bike = BikeEntity.riddenBy(player);
			if (bike != null) {
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
