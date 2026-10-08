package dev.michidk.voxvelo.bikes.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.michidk.voxvelo.bikes.VoxVelo;
import dev.michidk.voxvelo.bikes.bike.RiderSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Client-side settings of the raw bike mod, stored as JSON in config/voxvelo-bikes-client.json. Add-ons keep their own
 * settings in their own files.
 */
public final class VoxVeloConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	/** Watts the pedal key can stand for. */
	public static final double MIN_VIRTUAL_POWER = 50.0, MAX_VIRTUAL_POWER = 600.0;
	/** Share of full steering lock the steering keys turn to. */
	public static final double MIN_STEERING_SENSITIVITY = 0.3, MAX_STEERING_SENSITIVITY = 1.0;
	private static final double DEFAULT_VIRTUAL_POWER = 200.0;

	/** Rider mass in kg and an optional personal speed limit in km/h (0 = none); both are sent to the server. */
	public double riderMassKg = RiderSettings.DEFAULT_MASS_KG;
	public double speedLimitKmh = 0.0;

	public double keyboardVirtualPower = DEFAULT_VIRTUAL_POWER;
	public double keyboardSteeringSensitivity = MAX_STEERING_SENSITIVITY;

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("voxvelo-bikes-client.json");
	}

	public static VoxVeloConfig load() {
		Path file = file();
		if (Files.isRegularFile(file)) {
			try {
				VoxVeloConfig loaded = GSON.fromJson(Files.readString(file), VoxVeloConfig.class);
				if (loaded != null) {
					loaded.sanitize();
					return loaded;
				}
			} catch (Exception e) {
				VoxVelo.LOGGER.warn("Could not read {}, using defaults", file, e);
			}
		}
		return new VoxVeloConfig();
	}

	/** Keeps hand-edited or older files within sane ranges. */
	private void sanitize() {
		RiderSettings.Settings rider = RiderSettings.sanitize(this.riderMassKg, this.speedLimitKmh);
		this.riderMassKg = rider.massKg();
		this.speedLimitKmh = rider.speedLimitKmh();
		this.keyboardVirtualPower = clamp(this.keyboardVirtualPower, MIN_VIRTUAL_POWER, MAX_VIRTUAL_POWER, DEFAULT_VIRTUAL_POWER);
		this.keyboardSteeringSensitivity = clamp(this.keyboardSteeringSensitivity, MIN_STEERING_SENSITIVITY, MAX_STEERING_SENSITIVITY,
			MAX_STEERING_SENSITIVITY);
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
			VoxVelo.LOGGER.warn("Could not save {}", file, e);
		}
	}
}
