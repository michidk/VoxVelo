package dev.michidk.voxvelo.client.obc;

import dev.michidk.voxelfitness.client.FitnessRuntime;
import dev.michidk.voxvelo.client.bluetooth.BluetoothConnection;
import dev.michidk.voxvelo.client.bluetooth.BluetoothDevice;
import dev.michidk.voxvelo.client.bluetooth.BluetoothManager;
import dev.michidk.voxvelo.client.bluetooth.ConnectionState;
import dev.michidk.voxvelo.client.bluetooth.DeviceClient;
import dev.michidk.voxvelo.client.fitness.FitnessConfig;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Connects to one OpenBikeControl device over the network (TCP, found via mDNS or typed in) or over
 * Bluetooth LE, and feeds its button states into {@link ObcControls}. Nothing here blocks the game thread.
 * Reconnection works like for the trainer: automatic with growing delays unless the player disconnected.
 */
public final class ObcClient extends DeviceClient {
	private static final BleProfile PROFILE = new BleProfile(Obc.BLE_SERVICE, Obc.BLE_BUTTON_STATE, "voxvelo.connection.service.obc");
	/** How long the device picker scans for Bluetooth controllers. */
	private static final long DISCOVERY_SCAN_MILLIS = 20_000;
	private static final String NETWORK_KEY = "net:";

	private final MdnsDiscovery mdns = new MdnsDiscovery();
	private final ObcControls controls = new ObcControls();

	private @Nullable TcpLink tcpLink;
	/** Network discovery runs while the device picker is open, or while auto-connect looks for a saved network device. */
	private boolean pickerDiscovery;
	private boolean autoDiscovery;

	public ObcClient(BluetoothManager bluetooth, Executor gameThread) {
		super(bluetooth, gameThread, "OpenBikeControl device");
	}

	public ObcControls controls() {
		return this.controls;
	}

	public MdnsDiscovery mdns() {
		return this.mdns;
	}

	/** Devices the player can pick: network devices from mDNS and Bluetooth devices advertising the OBC service. */
	public List<ObcDevice> discovered(FitnessConfig config) {
		List<ObcDevice> list = new ArrayList<>();
		if (config.obcMode.network()) {
			list.addAll(this.mdns.devices());
		}
		if (config.obcMode.bluetooth()) {
			for (BluetoothDevice device : this.bluetooth.devices(Obc.BLE_SERVICE)) {
				list.add(ObcDevice.bluetooth(device.id(), device.name()));
			}
		}
		return list;
	}

	/** Starts discovery for the transports the config enables (called while the picker is open). */
	public void startDiscovery(FitnessConfig config) {
		this.pickerDiscovery = config.obcMode.network();
		this.updateMdns();
		if (config.obcMode.bluetooth()) {
			this.bluetooth.scan(DISCOVERY_SCAN_MILLIS);
		}
	}

	public void stopDiscovery() {
		this.pickerDiscovery = false;
		this.updateMdns();
		this.bluetooth.releaseScan();
	}

	/** Restarts discovery: fresh mDNS queries and a new Bluetooth scan window. */
	public void rescan(FitnessConfig config) {
		this.stopDiscovery();
		this.startDiscovery(config);
	}

	/** Whether the picker is still looking for devices on any enabled transport. */
	public boolean searching(FitnessConfig config) {
		boolean network = config.obcMode.network() && this.pickerDiscovery && !this.mdns.failed();
		boolean bluetooth = config.obcMode.bluetooth() && this.bluetooth.isScanning();
		return network || bluetooth;
	}

	private void setAutoDiscovery(boolean wanted) {
		if (this.autoDiscovery != wanted) {
			this.autoDiscovery = wanted;
			this.updateMdns();
		}
	}

	private void updateMdns() {
		if (this.pickerDiscovery || this.autoDiscovery) {
			this.mdns.start();
		} else {
			this.mdns.stop();
		}
	}

	/** Connects on the player request. Clears any earlier deliberate disconnect. */
	public void connect(ObcDevice device) {
		this.playerRequested();
		this.start(device);
	}

	@Override
	public void disconnect() {
		super.disconnect();
		this.setAutoDiscovery(false);
	}

	/** Called every client tick while a world is loaded. Starts auto-connect and reconnect attempts. */
	public void tick(FitnessConfig config) {
		if (config.obcMode == ObcMode.OFF) {
			if (this.state() != ConnectionState.DISCONNECTED) {
				this.teardown(null);
			}
			this.setAutoDiscovery(false);
			return;
		}
		String key = config.obcPreferredKey;
		if (!config.obcAutoConnect || !key.startsWith(NETWORK_KEY) || !config.obcMode.network()) {
			// Auto-connect has nothing to look for on the network.
			this.setAutoDiscovery(false);
		}
		if (!this.autoAttemptDue(config.obcAutoConnect && !key.isEmpty())) {
			return;
		}
		ObcDevice device = this.resolvePreferred(config);
		if (device != null) {
			this.start(device);
		}
	}

	/** Turns the stored key back into a device, if it is currently reachable by its transport. */
	private @Nullable ObcDevice resolvePreferred(FitnessConfig config) {
		String key = config.obcPreferredKey;
		String name = config.obcPreferredName;
		if (key.startsWith("tcp:")) {
			int split = key.lastIndexOf(':');
			try {
				return ObcDevice.manual(key.substring(4, split), Integer.parseInt(key.substring(split + 1)));
			} catch (RuntimeException e) {
				return null;
			}
		}
		if (key.startsWith("ble:")) {
			return config.obcMode.bluetooth() ? ObcDevice.bluetooth(key.substring(4), name) : null;
		}
		if (key.startsWith(NETWORK_KEY) && config.obcMode.network()) {
			this.setAutoDiscovery(true);
			for (ObcDevice found : this.mdns.devices()) {
				if (found.key().equals(key)) {
					return found;
				}
			}
			this.setMessage(Component.translatable("voxvelo.connection.searching_network", name));
		}
		return null;
	}

	private void start(ObcDevice device) {
		int gen = this.begin(device.name(), Component.translatable("voxvelo.connection.connecting"));
		if (device.transport() == ObcDevice.Transport.NETWORK) {
			this.tcpLink = TcpLink.open(device.host(), device.port(), appInfo(),
				this.dataCallback(gen, button -> this.controls.apply(button.id(), button.state())),
				this.callback(gen, () -> this.connected(gen)),
				this.dataCallback(gen, (@Nullable IOException error) -> this.onTcpClosed(gen, error)));
		} else {
			this.connectBluetooth(gen, device.bluetoothAddress(), PROFILE, data -> this.controls.apply(ObcMessages.parseButtonState(data)));
		}
	}

	private void onTcpClosed(int gen, @Nullable IOException error) {
		if (error == null) {
			this.lost(gen, Component.translatable("voxvelo.connection.closed"));
		} else if (this.isConnected()) {
			this.lost(gen, describe(error));
		} else {
			this.fail(gen, error);
		}
	}

	private static byte[] appInfo() {
		String version = FabricLoader.getInstance().getModContainer(FitnessRuntime.MOD_ID)
			.map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("0");
		return ObcMessages.encodeAppInfo(FitnessRuntime.MOD_ID, version, Obc.SUPPORTED_BUTTONS);
	}

	@Override
	protected void onConnected() {
		this.controls.releaseAll();
		this.setAutoDiscovery(false);
		BluetoothConnection connection = this.connection();
		if (connection != null) {
			// Telling the device what we consume is optional; ignore failures.
			connection.write(Obc.BLE_SERVICE, Obc.BLE_APP_INFO, appInfo(), false).exceptionally(error -> null);
		}
	}

	/** Releases every button, so a held steer or brake cannot get stuck. */
	@Override
	protected void onTeardown() {
		this.controls.releaseAll();
		TcpLink old = this.tcpLink;
		this.tcpLink = null;
		if (old != null) {
			old.close();
		}
	}

	public void shutdown() {
		this.teardown(null);
		this.pickerDiscovery = false;
		this.autoDiscovery = false;
		this.mdns.shutdown();
	}
}
