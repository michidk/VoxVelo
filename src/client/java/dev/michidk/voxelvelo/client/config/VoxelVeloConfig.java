package dev.michidk.voxelvelo.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.michidk.voxelvelo.VoxelVelo;
import dev.michidk.voxelvelo.client.input.ControlChannel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Client-side settings, stored as JSON in config/voxelvelo-client.json. Source fields hold a
 * BikeInputSource id; unknown or unsupported ids fall back to the keyboard when composing input.
 */
public final class VoxelVeloConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public String propulsionSource = "keyboard";
	public String steeringSource = "keyboard";
	public String brakeSource = "keyboard";
	public String gearSource = "keyboard";

	/** Stable Bluetooth id and display name of the trainer to reconnect to. Empty when none is chosen. */
	public String preferredTrainerId = "";
	public String preferredTrainerName = "";
	public boolean autoConnectTrainer = true;

	/** Which OpenBikeControl transports are used: off, network, bluetooth or both. */
	public String obcMode = "both";
	public String obcPreferredKey = "";
	public String obcPreferredName = "";
	public boolean obcAutoConnect = true;

	/** Send terrain gradient and surface to the trainer as resistance. Intensity 1.0 matches the game physics. */
	public boolean trainerResistanceEnabled = true;
	public double trainerResistanceIntensity = 1.0;

	public double keyboardVirtualPower = 200.0;
	public double keyboardSteeringSensitivity = 1.0;

	public String sourceFor(ControlChannel channel) {
		return switch (channel) {
			case PROPULSION -> this.propulsionSource;
			case STEERING -> this.steeringSource;
			case BRAKE -> this.brakeSource;
			case GEAR -> this.gearSource;
		};
	}

	public void setSource(ControlChannel channel, String sourceId) {
		switch (channel) {
			case PROPULSION -> this.propulsionSource = sourceId;
			case STEERING -> this.steeringSource = sourceId;
			case BRAKE -> this.brakeSource = sourceId;
			case GEAR -> this.gearSource = sourceId;
		}
	}

	public boolean obcNetworkEnabled() {
		return this.obcMode.equals("both") || this.obcMode.equals("network");
	}

	public boolean obcBluetoothEnabled() {
		return this.obcMode.equals("both") || this.obcMode.equals("bluetooth");
	}

	public boolean hasPreferredTrainer() {
		return !this.preferredTrainerId.isEmpty();
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("voxelvelo-client.json");
	}

	public static VoxelVeloConfig load() {
		Path file = file();
		if (Files.isRegularFile(file)) {
			try {
				VoxelVeloConfig loaded = GSON.fromJson(Files.readString(file), VoxelVeloConfig.class);
				if (loaded != null) {
					loaded.sanitize();
					return loaded;
				}
			} catch (Exception e) {
				VoxelVelo.LOGGER.warn("Could not read {}, using defaults", file, e);
			}
		}
		return new VoxelVeloConfig();
	}

	/** Keeps hand-edited or older files within sane ranges. */
	private void sanitize() {
		this.propulsionSource = orDefault(this.propulsionSource);
		this.steeringSource = orDefault(this.steeringSource);
		this.brakeSource = orDefault(this.brakeSource);
		this.gearSource = orDefault(this.gearSource);
		this.preferredTrainerId = this.preferredTrainerId == null ? "" : this.preferredTrainerId;
		this.preferredTrainerName = this.preferredTrainerName == null ? "" : this.preferredTrainerName;
		this.obcMode = switch (this.obcMode == null ? "" : this.obcMode) {
			case "off", "network", "bluetooth" -> this.obcMode;
			default -> "both";
		};
		this.obcPreferredKey = this.obcPreferredKey == null ? "" : this.obcPreferredKey;
		this.obcPreferredName = this.obcPreferredName == null ? "" : this.obcPreferredName;
		this.trainerResistanceIntensity = clamp(this.trainerResistanceIntensity, 0.0, 2.0, 1.0);
		this.keyboardVirtualPower = clamp(this.keyboardVirtualPower, 50.0, 600.0, 200.0);
		this.keyboardSteeringSensitivity = clamp(this.keyboardSteeringSensitivity, 0.3, 1.0, 1.0);
	}

	private static String orDefault(String id) {
		return id == null || id.isBlank() ? "keyboard" : id;
	}

	private static double clamp(double value, double min, double max, double fallback) {
		return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
	}

	public void save() {
		Path file = file();
		try {
			Files.createDirectories(file.getParent());
			Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(temp, GSON.toJson(this));
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			VoxelVelo.LOGGER.warn("Could not save {}", file, e);
		}
	}
}
