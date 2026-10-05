package dev.michidk.voxelvelo.client.obc;

import dev.michidk.voxelvelo.VoxelVelo;
import dev.michidk.voxelvelo.client.bluetooth.BluetoothConnection;
import dev.michidk.voxelvelo.client.bluetooth.BluetoothDevice;
import dev.michidk.voxelvelo.client.bluetooth.BluetoothManager;
import dev.michidk.voxelvelo.client.fitness.FitnessConfig;
import dev.michidk.voxelvelo.client.ftms.TrainerState;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Connects to one OpenBikeControl device over the network (TCP, found via mDNS or typed in) or over
 * Bluetooth LE, and feeds its button states into {@link ObcControls}. Nothing here blocks the game thread.
 * Reconnection works like for the trainer: automatic with growing delays unless the player disconnected.
 */
public final class ObcClient {
	private static final long FIND_TIMEOUT_MILLIS = 15_000;
	private static final long MIN_RETRY_MILLIS = 4_000;
	private static final long MAX_RETRY_MILLIS = 30_000;

	private final BluetoothManager bluetooth;
	private final MdnsDiscovery mdns = new MdnsDiscovery();
	private final ObcControls controls = new ObcControls();

	private volatile TrainerState state = TrainerState.DISCONNECTED;
	private volatile String deviceName = "";
	private volatile String message = "";
	private volatile BluetoothConnection bleConnection;
	private volatile TcpLink tcpLink;
	private volatile int generation;

	private boolean userDisconnected;
	private long nextAttemptAt;
	private long retryDelay = MIN_RETRY_MILLIS;

	public ObcClient(BluetoothManager bluetooth) {
		this.bluetooth = bluetooth;
	}

	public ObcControls controls() {
		return this.controls;
	}

	public TrainerState state() {
		return this.state;
	}

	public boolean isConnected() {
		return this.state == TrainerState.CONNECTED;
	}

	public String deviceName() {
		return this.deviceName;
	}

	public String message() {
		return this.message;
	}

	public MdnsDiscovery mdns() {
		return this.mdns;
	}

	/** Devices the player can pick: network devices from mDNS and Bluetooth devices advertising the OBC service. */
	public List<ObcDevice> discovered(FitnessConfig config) {
		List<ObcDevice> list = new ArrayList<>();
		if (config.obcNetworkEnabled()) {
			list.addAll(this.mdns.devices());
		}
		if (config.obcBluetoothEnabled()) {
			for (BluetoothDevice device : this.bluetooth.devices(Obc.BLE_SERVICE)) {
				if (device.advertises(Obc.BLE_SERVICE)) {
					list.add(ObcDevice.bluetooth(device.id(), device.name()));
				}
			}
		}
		return list;
	}

	/** Starts discovery for the transports the config enables (called while the picker is open). */
	public void startDiscovery(FitnessConfig config) {
		if (config.obcNetworkEnabled()) {
			this.mdns.start();
		}
		if (config.obcBluetoothEnabled()) {
			this.bluetooth.scan(20_000);
		}
	}

	public void stopDiscovery() {
		this.mdns.stop();
		this.bluetooth.releaseScan();
	}

	/** Connects on the player request. Clears any earlier deliberate disconnect. */
	public void connect(ObcDevice device) {
		this.userDisconnected = false;
		this.retryDelay = MIN_RETRY_MILLIS;
		this.start(device);
	}

	public void disconnect() {
		this.userDisconnected = true;
		this.teardown("");
	}

	/** Called every client tick while a world is loaded. Starts auto-connect and reconnect attempts. */
	public void tick(FitnessConfig config) {
		if (config.obcMode.equals("off")) {
			if (this.state != TrainerState.DISCONNECTED) {
				this.teardown("");
			}
			return;
		}
		if (this.state != TrainerState.DISCONNECTED || this.userDisconnected || !config.obcAutoConnect || config.obcPreferredKey.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now < this.nextAttemptAt) {
			return;
		}
		this.nextAttemptAt = now + this.retryDelay;
		this.retryDelay = Math.min(this.retryDelay * 2, MAX_RETRY_MILLIS);
		ObcDevice device = this.resolvePreferred(config);
		if (device != null) {
			this.start(device);
		}
	}

	/** Turns the stored key back into a device, if it is currently reachable by its transport. */
	private ObcDevice resolvePreferred(FitnessConfig config) {
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
			return config.obcBluetoothEnabled() ? ObcDevice.bluetooth(key.substring(4), name) : null;
		}
		if (key.startsWith("net:") && config.obcNetworkEnabled()) {
			this.mdns.start();
			for (ObcDevice found : this.mdns.devices()) {
				if (found.key().equals(key)) {
					return found;
				}
			}
			this.message = "Looking for " + name + " on the network...";
		}
		return null;
	}

	private void start(ObcDevice device) {
		this.teardown("");
		int gen = this.generation;
		this.state = TrainerState.CONNECTING;
		this.deviceName = device.name();
		this.message = "Connecting...";
		if (device.transport() == ObcDevice.Transport.NETWORK) {
			this.startNetwork(gen, device);
		} else {
			this.startBluetooth(gen, device);
		}
	}

	private static byte[] appInfo() {
		String version = FabricLoader.getInstance().getModContainer(VoxelVelo.MOD_ID)
			.map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("0");
		return ObcMessages.encodeAppInfo(VoxelVelo.MOD_ID, version, Obc.SUPPORTED_BUTTONS);
	}

	private void startNetwork(int gen, ObcDevice device) {
		this.tcpLink = TcpLink.open(device.host(), device.port(), appInfo(), this.controls,
			() -> this.onConnected(gen),
			reason -> this.onLinkLost(gen, reason));
	}

	private void startBluetooth(int gen, ObcDevice device) {
		if (!this.bluetooth.isAvailable()) {
			this.fail(gen, this.bluetooth.unavailableReason());
			return;
		}
		String address = device.bluetoothAddress();
		this.bluetooth.find(address, FIND_TIMEOUT_MILLIS)
			.thenCompose(found -> {
				this.bluetooth.releaseScan();
				return this.bluetooth.backend().connect(found.id(), () -> this.onLinkLost(gen, "Controller disconnected"));
			})
			.thenCompose(conn -> this.setUpBluetooth(gen, conn))
			.whenComplete((ignored, error) -> {
				if (error != null) {
					this.fail(gen, describe(error));
				}
			});
	}

	private CompletableFuture<Void> setUpBluetooth(int gen, BluetoothConnection conn) {
		if (gen != this.generation) {
			conn.disconnect();
			return CompletableFuture.completedFuture(null);
		}
		if (!conn.serviceUuids().contains(Obc.BLE_SERVICE)) {
			conn.disconnect();
			throw new IllegalStateException("Device does not offer the OpenBikeControl service");
		}
		this.bleConnection = conn;
		return conn.subscribe(Obc.BLE_SERVICE, Obc.BLE_BUTTON_STATE, data -> {
			if (gen == this.generation) {
				this.controls.apply(ObcMessages.parseButtonState(data));
			}
		}).thenRun(() -> {
			// Telling the device what we consume is optional; ignore failures.
			conn.write(Obc.BLE_SERVICE, Obc.BLE_APP_INFO, appInfo(), false).exceptionally(error -> null);
			this.onConnected(gen);
		});
	}

	private void onConnected(int gen) {
		if (gen != this.generation) {
			return;
		}
		this.controls.releaseAll();
		this.state = TrainerState.CONNECTED;
		this.message = "";
		this.retryDelay = MIN_RETRY_MILLIS;
		VoxelVelo.LOGGER.info("Connected to OpenBikeControl device {}", this.deviceName);
	}

	private void onLinkLost(int gen, String reason) {
		if (gen == this.generation) {
			VoxelVelo.LOGGER.info("OpenBikeControl device {} disconnected: {}", this.deviceName, reason);
			this.teardown(reason);
		}
	}

	private void fail(int gen, String reason) {
		if (gen == this.generation) {
			VoxelVelo.LOGGER.info("OpenBikeControl connection failed: {}", reason);
			this.teardown(reason);
		}
	}

	/** Drops the link and releases every button, so a held steer or brake cannot get stuck. */
	private void teardown(String reason) {
		this.generation++;
		BluetoothConnection oldBle = this.bleConnection;
		TcpLink oldTcp = this.tcpLink;
		this.bleConnection = null;
		this.tcpLink = null;
		this.state = TrainerState.DISCONNECTED;
		this.message = reason;
		this.controls.releaseAll();
		if (oldBle != null) {
			try {
				oldBle.disconnect();
			} catch (Throwable t) {
				VoxelVelo.LOGGER.debug("Error while disconnecting controller", t);
			}
		}
		if (oldTcp != null) {
			oldTcp.close();
		}
	}

	public void shutdown() {
		this.teardown("");
		this.mdns.shutdown();
	}

	private static String describe(Throwable error) {
		Throwable cause = error;
		while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) {
			cause = cause.getCause();
		}
		if (cause instanceof TimeoutException) {
			return "Device not found nearby";
		}
		String text = cause.getMessage();
		return text == null || text.isBlank() ? cause.getClass().getSimpleName() : text;
	}
}
