package dev.michidk.voxvelo.bikes.client.network;

import dev.michidk.voxvelo.bikes.bike.BikeControlState;
import dev.michidk.voxvelo.bikes.bike.BikeDriver;
import dev.michidk.voxvelo.bikes.bike.BikeEntity;
import dev.michidk.voxvelo.bikes.bike.BikeRiderState;
import dev.michidk.voxvelo.bikes.bike.RiderSettings;
import dev.michidk.voxvelo.bikes.client.config.VoxVeloConfig;
import dev.michidk.voxvelo.bikes.client.input.BikeInputManager;
import dev.michidk.voxvelo.bikes.network.BikeStatePayload;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Drives the bike the local player rides. Once per client tick it composes the controls from every input source;
 * the bike simulates its next tick with them (the rider controls it like a horse) and reports back what it did,
 * which is passed on to the server. Also turns the rider's camera along with the bike.
 */
public final class BikeControlSender implements BikeDriver {
	private final BikeInputManager inputManager;
	private final VoxVeloConfig config;
	/** Called when the local player gets on a bike, e.g. for hints from add-ons. */
	private final List<Consumer<LocalPlayer>> mountListeners;

	private @Nullable BikeEntity currentBike;
	private BikeControlState lastComposed = BikeControlState.NEUTRAL;
	private float lastBikeYaw;

	public BikeControlSender(BikeInputManager inputManager, VoxVeloConfig config, List<Consumer<LocalPlayer>> mountListeners) {
		this.inputManager = inputManager;
		this.config = config;
		this.mountListeners = mountListeners;
	}

	/** The controls composed on the last tick while riding, for the HUD. */
	public BikeControlState lastComposed() {
		return this.lastComposed;
	}

	@Override
	public @Nullable BikeControlState input(BikeEntity bike) {
		return bike == this.currentBike ? this.lastComposed : null;
	}

	@Override
	public RiderSettings.Settings riderSettings() {
		return RiderSettings.sanitize(this.config.riderMassKg, this.config.speedLimitKmh);
	}

	@Override
	public void report(BikeEntity bike, BikeRiderState state) {
		if (bike == this.currentBike && ClientPlayNetworking.canSend(BikeStatePayload.TYPE)) {
			ClientPlayNetworking.send(BikeStatePayload.of(state));
		}
	}

	public void tick(Minecraft client) {
		LocalPlayer player = client.player;
		BikeEntity bike = BikeEntity.riddenBy(player);

		if (bike == null) {
			this.currentBike = null;
			this.lastComposed = BikeControlState.NEUTRAL;
			this.inputManager.reset();
			return;
		}

		if (bike != this.currentBike) {
			this.currentBike = bike;
			this.lastBikeYaw = bike.getYRot();
			this.inputManager.onMount(bike.getGear());
			for (Consumer<LocalPlayer> listener : this.mountListeners) {
				listener.accept(player);
			}
		}

		this.followBikeYaw(player, bike);
		this.lastComposed = this.inputManager.compose(client);
	}

	private void followBikeYaw(LocalPlayer player, BikeEntity bike) {
		float delta = Mth.wrapDegrees(bike.getYRot() - this.lastBikeYaw);
		this.lastBikeYaw = bike.getYRot();
		if (delta != 0.0F) {
			player.setYRot(player.getYRot() + delta);
			player.setYHeadRot(player.getYHeadRot() + delta);
		}
	}
}
