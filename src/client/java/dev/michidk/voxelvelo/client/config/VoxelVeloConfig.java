package dev.michidk.voxelvelo.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.michidk.voxelvelo.VoxelVelo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Client-side settings of the raw bike mod, stored as JSON in config/voxelvelo-client.json. Add-ons keep their own
 * settings in their own files.
 */
public final class VoxelVeloConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Rider mass in kg and an optional personal speed limit in km/h (0 = none); both are sent to the server. */
	public double riderMassKg = 75.0;
	public double speedLimitKmh = 0.0;

	public boolean hudEnabled = true;
	public double keyboardVirtualPower = 200.0;
	public double keyboardSteeringSensitivity = 1.0;

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
		this.riderMassKg = clamp(this.riderMassKg, 40.0, 150.0, 75.0);
		this.speedLimitKmh = this.speedLimitKmh < 5.0 ? 0.0 : clamp(this.speedLimitKmh, 5.0, 100.0, 0.0);
		this.keyboardVirtualPower = clamp(this.keyboardVirtualPower, 50.0, 600.0, 200.0);
		this.keyboardSteeringSensitivity = clamp(this.keyboardSteeringSensitivity, 0.3, 1.0, 1.0);
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
