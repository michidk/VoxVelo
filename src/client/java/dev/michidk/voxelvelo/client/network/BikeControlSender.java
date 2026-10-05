package dev.michidk.voxelvelo.client.network;

import dev.michidk.voxelvelo.bike.BikeControlState;
import dev.michidk.voxelvelo.bike.BikeEntity;
import dev.michidk.voxelvelo.client.input.BikeInputManager;
import dev.michidk.voxelvelo.client.input.ControlChannel;
import dev.michidk.voxelvelo.client.input.VoxelVeloKeys;
import dev.michidk.voxelvelo.network.BikeControlPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Samples the composed controls once per client tick while riding and sends them to the server when
 * they change, plus a periodic heartbeat so the server's input timeout never trips during steady riding.
 * Also turns the rider's camera along with the bike, since the rider is not the vehicle's controller.
 */
public final class BikeControlSender {
	private static final int HEARTBEAT_TICKS = 5;

	private final BikeInputManager inputManager;

	private BikeEntity currentBike;
	private BikeControlState lastSent;
	private int ticksSinceSend;
	private int sequence;
	private float lastBikeYaw;

	public BikeControlSender(BikeInputManager inputManager) {
		this.inputManager = inputManager;
	}

	public void tick(Minecraft client) {
		LocalPlayer player = client.player;
		BikeEntity bike = player != null && player.getVehicle() instanceof BikeEntity vehicle && vehicle.getRider() == player ? vehicle : null;

		if (bike == null) {
			if (this.currentBike != null) {
				this.currentBike = null;
				this.lastSent = null;
				this.sequence = 0;
				this.inputManager.reset();
			} else {
				this.inputManager.reset();
			}
			return;
		}

		if (bike != this.currentBike) {
			this.currentBike = bike;
			this.lastSent = null;
			this.ticksSinceSend = HEARTBEAT_TICKS;
			this.lastBikeYaw = bike.getYRot();
			this.inputManager.onMount(bike.getGear());
			if (!this.inputManager.selected(ControlChannel.PROPULSION).isActive()) {
				player.sendOverlayMessage(Component.translatable("voxelvelo.hint.trainer_not_connected", VoxelVeloKeys.OPEN_CONFIG.getTranslatedKeyMessage()));
			}
		}

		this.followBikeYaw(player, bike);

		BikeControlState state = this.inputManager.compose(client);
		this.ticksSinceSend++;
		if (!state.equals(this.lastSent) || this.ticksSinceSend >= HEARTBEAT_TICKS) {
			if (ClientPlayNetworking.canSend(BikeControlPayload.TYPE)) {
				ClientPlayNetworking.send(BikeControlPayload.of(this.sequence++, state));
			}
			this.lastSent = state;
			this.ticksSinceSend = 0;
		}
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
