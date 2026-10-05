package dev.michidk.voxelvelo.network;

import dev.michidk.voxelvelo.bike.BikeEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class BikeNetworking {
	private BikeNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(BikeControlPayload.TYPE, BikeControlPayload.CODEC);
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
	}
}
