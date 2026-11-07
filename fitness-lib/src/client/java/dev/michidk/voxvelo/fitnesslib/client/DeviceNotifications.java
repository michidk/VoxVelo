package dev.michidk.voxvelo.fitnesslib.client;

import dev.michidk.voxvelo.fitnesslib.client.bluetooth.BluetoothDevices.Capability;
import dev.michidk.voxvelo.fitnesslib.client.bluetooth.DeviceClient;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

/** Reports established device connection changes during play, without replaying events from settings. */
final class DeviceNotifications {
	private final Map<DeviceClient, Boolean> connected = new IdentityHashMap<>();
	private final Map<DeviceClient, SystemToast.SystemToastId> tokens = new IdentityHashMap<>();
	private final Map<Capability, String> sources = new java.util.EnumMap<>(Capability.class);
	private final Map<Capability, SystemToast.SystemToastId> sourceTokens = new java.util.EnumMap<>(Capability.class);

	void tick(Minecraft client, FitnessRuntime runtime) {
		boolean visible = client.level != null && !(client.gui.screen() instanceof OptionsSubScreen);
		// One toast per physical peripheral, even when it provides several capabilities.
		for (var device : runtime.bluetoothDevices.saved()) this.observe(client, device, visible);
		this.observe(client, runtime.obc, visible);
		for (Capability capability : Capability.values()) {
			var service = runtime.bluetoothDevices.client(capability);
			String current = service.isConnected() ? runtime.bluetoothDevices.selectedId(capability) : "";
			String previous = this.sources.put(capability, current);
			if (visible && !current.isEmpty() && !current.equals(previous)) {
				SystemToast.addOrUpdate(client.gui.toastManager(), this.sourceTokens.computeIfAbsent(capability, ignored -> new SystemToast.SystemToastId()),
					Component.translatable("voxvelo_fitness_lib.notification.source", Component.translatable("voxvelo_fitness_lib.bluetooth.role." + capability.key)),
					Component.literal(service.deviceName()));
			}
		}
	}

	private void observe(Minecraft client, DeviceClient device, boolean visible) {
		boolean current = device.isConnected();
		Boolean previous = this.connected.put(device, current);
		if (!visible || !changed(previous, current)) return;
		SystemToast.addOrUpdate(client.gui.toastManager(), this.tokens.computeIfAbsent(device, ignored -> new SystemToast.SystemToastId()),
			Component.translatable(current ? "voxvelo_fitness_lib.notification.connected" : "voxvelo_fitness_lib.notification.disconnected"),
			Component.literal(device.deviceName()));
	}

	/** Initial disconnected state and repeated failed attempts are silent. */
	static boolean changed(Boolean previous, boolean current) {
		return previous == null ? current : previous != current;
	}
}
