package dev.michidk.voxvelo.fitnesslib.client.obc;

import com.google.gson.annotations.SerializedName;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/** Which transports OpenBikeControl devices are looked for and connected over. Stored in the config in lower case. */
public enum ObcMode {
	@SerializedName("off")
	OFF(false, false),
	@SerializedName("network")
	NETWORK(true, false),
	@SerializedName("bluetooth")
	BLUETOOTH(false, true),
	@SerializedName("both")
	BOTH(true, true);

	private final boolean network;
	private final boolean bluetooth;

	ObcMode(boolean network, boolean bluetooth) {
		this.network = network;
		this.bluetooth = bluetooth;
	}

	public boolean network() {
		return this.network;
	}

	public boolean bluetooth() {
		return this.bluetooth;
	}

	public Component label() {
		return Component.translatable("voxvelo_fitness_lib.obc.mode." + this.name().toLowerCase(Locale.ROOT));
	}
}
