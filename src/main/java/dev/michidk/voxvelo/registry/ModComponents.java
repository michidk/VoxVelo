package dev.michidk.voxvelo.registry;

import dev.michidk.voxvelo.VoxVelo;
import dev.michidk.voxvelo.bike.TireTread;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

/** Item data components. A real component (not custom data) so item models can select on it, see items/bike_wheel.json. */
public final class ModComponents {
	/** The tire on a Bike Wheel. Absent on wheels made before treads existed; those read as gravel. */
	public static final DataComponentType<TireTread> TREAD = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			VoxVelo.id("tread"), DataComponentType.<TireTread>builder()
					.persistent(TireTread.CODEC).networkSynchronized(TireTread.STREAM_CODEC).build());

	private ModComponents() {}

	public static void register() {
		// Loads the class, which registers the components above.
	}
}
