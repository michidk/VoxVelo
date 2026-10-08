package dev.michidk.voxvelo.fitnesslib.client.bluetooth;

import dev.michidk.voxvelo.fitnesslib.client.fitness.FitnessConfig;
import dev.michidk.voxvelo.fitnesslib.client.ftms.*;
import dev.michidk.voxvelo.fitnesslib.client.heartrate.HeartRate;
import dev.michidk.voxvelo.fitnesslib.client.heartrate.HeartRateClient;
import dev.michidk.voxvelo.fitnesslib.client.obc.*;
import dev.michidk.voxvelo.fitnesslib.client.powermeter.CyclingPower;
import dev.michidk.voxvelo.fitnesslib.client.powermeter.PowerMeterClient;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Drives real connection clients with delayed discovery, connection and notification callbacks. */
public final class ConnectionChecks {
	/** Stands in for the game thread: callbacks run at once, so every check is deterministic. */
	private static final Executor GAME = Runnable::run;

	public static void main(String[] args) {
		trainer();
		controlPoint();
		controller();
		heartRate();
		gameThreadOnly();
		sharedDevice();
		shutdownClosesLinks();
		deviceLists();
		System.out.println("Trainer, heart rate sensor, game thread, shared device, OpenBikeControl and device list checks passed.");
	}

	/** Bluetooth callbacks wait for the game thread, and a teardown before they get there leaves nothing open. */
	private static void gameThreadOnly() {
		Backend backend = new Backend();
		Queue<Runnable> game = new ArrayDeque<>();
		HeartRateClient client = new HeartRateClient(new BluetoothManager(backend), game::add);
		BluetoothDevice device = new BluetoothDevice("strap", "Strap", -50, Set.of(HeartRate.SERVICE));
		client.connect(device);
		backend.discover(device);
		require(backend.attempts.isEmpty(), "a device found on a Bluetooth thread is acted on by the game thread");
		runAll(game);
		require(backend.attempts.size() == 1, "the game thread goes on to connect");
		Connection link = backend.last().complete(HeartRate.SERVICE);
		require(!client.isConnected(), "the new link is taken up on the game thread");
		client.disconnect();
		runAll(game);
		require(!link.connected && !client.isConnected(), "a link that completes after a teardown is closed again");
	}

	private static void runAll(Queue<Runnable> game) {
		Runnable task;
		while ((task = game.poll()) != null) {
			task.run();
		}
	}

	/** Whether a status message is the given translation. */
	private static boolean says(Component message, String key) {
		return message != null && message.getContents() instanceof TranslatableContents contents && contents.getKey().equals(key);
	}

	/** One device that is a trainer, a power meter and a heart rate source at once: its roles share one connection. */
	private static void sharedDevice() {
		Backend backend = new Backend();
		BluetoothManager manager = new BluetoothManager(backend);
		FtmsClient trainer = new FtmsClient(manager, GAME);
		PowerMeterClient meter = new PowerMeterClient(manager, GAME);
		HeartRateClient strap = new HeartRateClient(manager, GAME);
		BluetoothDevice device = new BluetoothDevice("combo", "Combo", -30, Set.of(Ftms.SERVICE, CyclingPower.SERVICE, HeartRate.SERVICE));
		trainer.connect(device);
		meter.connect(device);
		strap.connect(device);
		backend.discover(device);
		require(backend.attempts.size() == 1, "roles on one device share a single connection attempt");
		Connection link = backend.last().complete(Ftms.SERVICE, CyclingPower.SERVICE, HeartRate.SERVICE);
		require(trainer.isConnected() && meter.isConnected() && strap.isConnected(), "every role connects over the shared link");
		link.emit(HeartRate.MEASUREMENT, new byte[] {0, 64});
		link.emit(CyclingPower.MEASUREMENT, new byte[] {0, 0, (byte) 200, 0});
		require(strap.freshReading() != null && strap.freshReading().beatsPerMinute() == 64, "heart rate arrives over the shared link");
		require(meter.freshReading() != null && meter.freshReading().powerWatts() == 200, "power arrives over the shared link");

		strap.disconnect();
		require(link.connected && trainer.isConnected() && meter.isConnected() && !strap.isConnected(), "one role letting go keeps the link for the others");
		meter.disconnect();
		require(link.connected && trainer.isConnected(), "the link stays while a role still uses it");
		trainer.disconnect();
		require(!link.connected, "the last role letting go closes the link");

		// Connecting while the platform is still closing the old link waits until it has let go.
		trainer.connect(device);
		Connection second = backend.last().complete(Ftms.SERVICE, CyclingPower.SERVICE, HeartRate.SERVICE);
		second.closing = new CompletableFuture<>();
		trainer.disconnect();
		int attempts = backend.attempts.size();
		trainer.connect(device);
		require(backend.attempts.size() == attempts, "a reconnect waits for the platform to finish disconnecting");
		second.closing.complete(null);
		require(backend.attempts.size() == attempts + 1, "the reconnect goes ahead once the old link is closed");
		backend.last().complete(Ftms.SERVICE, CyclingPower.SERVICE, HeartRate.SERVICE);
		meter.connect(device);
		require(backend.attempts.size() == attempts + 1 && meter.isConnected(), "a role joining an existing link needs no new attempt");

		// Losing the link reaches every role that uses it.
		backend.last().lost.run();
		require(!trainer.isConnected() && !meter.isConnected(), "link loss reaches every role on the device");
	}

	private static void heartRate() {
		Backend backend = new Backend();
		HeartRateClient client = new HeartRateClient(new BluetoothManager(backend), GAME);
		BluetoothDevice device = new BluetoothDevice("strap", "Strap", -50, Set.of(HeartRate.SERVICE));
		FitnessConfig config = new FitnessConfig();
		config.autoConnectHeartRate = true;
		config.preferredHeartRateId = device.id();
		client.connect(device);
		client.disconnect();
		backend.discover(device);
		require(backend.attempts.isEmpty(), "cancelled discovery must not start a heart rate connection");
		client.connect(device);
		Attempt old = backend.last();
		client.disconnect();
		require(!old.complete(HeartRate.SERVICE).connected && !client.isConnected(), "late heart rate connection is closed");
		client.connect(device);
		Connection active = backend.last().complete(HeartRate.SERVICE);
		require(client.isConnected(), "heart rate sensor connects after subscription");
		active.emit(HeartRate.MEASUREMENT, new byte[] {0, 64});
		require(client.freshReading() != null && client.freshReading().beatsPerMinute() == 64, "sensor notifications provide heart rate");
		old.lost.run();
		require(client.isConnected(), "old sensor disconnect cannot tear down replacement");
		backend.last().lost.run();
		require(!client.isConnected() && client.freshReading() == null, "link loss clears heart rate");
		client.tick(config);
		backend.last().complete(HeartRate.SERVICE);
		require(client.isConnected() && client.freshReading() == null, "automatic reconnect does not inherit a stale heart rate");
		client.disconnect();
		int count = backend.attempts.size();
		client.tick(config);
		require(backend.attempts.size() == count, "manual sensor disconnect suppresses reconnect");
		client.connect(device);
		backend.last().complete(Ftms.SERVICE);
		require(!client.isConnected() && says(client.message(), "voxvelo_fitness_lib.connection.missing_service"), "a device without the service is rejected visibly");
	}

	/** Leaving the game must not leave a device connected: that ties up the device and the exiting process. */
	private static void shutdownClosesLinks() {
		Backend backend = new Backend();
		BluetoothManager manager = new BluetoothManager(backend);
		HeartRateClient strap = new HeartRateClient(manager, GAME);
		BluetoothDevice device = new BluetoothDevice("strap", "Strap", -50, Set.of(HeartRate.SERVICE));
		strap.connect(device);
		backend.discover(device);
		Connection link = backend.last().complete(HeartRate.SERVICE);
		require(link.connected && strap.isConnected(), "the sensor is connected before shutdown");
		manager.shutdown();
		require(!link.connected, "shutting down closes the open Bluetooth link");
		require(!backend.scanning, "shutting down stops scanning");
	}

	private static void deviceLists() {
		Backend backend = new Backend();
		BluetoothManager manager = new BluetoothManager(backend);
		manager.scan(10_000);
		backend.discover(new BluetoothDevice("trainer", "Trainer", -60, Set.of(Ftms.SERVICE)));
		backend.discover(new BluetoothDevice("strap", "Strap", -50, Set.of(HeartRate.SERVICE)));
		backend.discover(new BluetoothDevice("combo", "Combo", -40, Set.of(Ftms.SERVICE, CyclingPower.SERVICE)));
		backend.discover(new BluetoothDevice("headphones", "Headphones", -30, Set.of()));
		require(ids(manager.devices(Ftms.SERVICE)).equals(List.of("combo", "trainer")), "a service list holds only devices advertising it, strongest first");
		require(ids(manager.devices(HeartRate.SERVICE)).equals(List.of("strap")), "heart rate list holds only heart rate sensors");
		require(ids(manager.devicesAdvertisingAny(List.of(Ftms.SERVICE, CyclingPower.SERVICE, HeartRate.SERVICE))).equals(List.of("combo", "strap", "trainer")),
			"the combined list holds every device that fits a role and nothing else");
		backend.discover(new BluetoothDevice("strap", "Strap", -45, Set.of()));
		require(ids(manager.devices(HeartRate.SERVICE)).equals(List.of("strap")), "a later packet without services does not drop a sensor from the list");
	}

	private static List<String> ids(List<BluetoothDevice> devices) {
		return devices.stream().map(BluetoothDevice::id).toList();
	}

	private static void trainer() {
		Backend backend = new Backend();
		FtmsClient client = new FtmsClient(new BluetoothManager(backend), GAME);
		BluetoothDevice device = new BluetoothDevice("trainer", "Trainer", -40, Set.of(Ftms.SERVICE));
		FitnessConfig config = new FitnessConfig();
		config.autoConnectTrainer = true;
		config.preferredTrainerId = device.id();
		client.connect(device);
		client.disconnect();
		backend.discover(device);
		require(backend.attempts.isEmpty(), "cancelled discovery must not start a trainer connection");
		client.connect(device);
		Attempt old = backend.last();
		client.disconnect();
		Connection stale = old.complete(Ftms.SERVICE);
		require(!stale.connected && !client.isConnected(), "late trainer connection is closed");
		client.connect(device);
		Connection active = backend.last().complete(Ftms.SERVICE);
		require(client.isConnected(), "trainer connects after subscription");
		active.emit(Ftms.INDOOR_BIKE_DATA, new byte[] {0x40, 0, 0, 0, (byte) 200, 0});
		require(client.freshTelemetry() != null && client.freshTelemetry().powerWatts() == 200, "trainer notifications provide power");
		old.lost.run();
		require(client.isConnected(), "old trainer disconnect cannot tear down replacement");
		backend.last().lost.run();
		require(!client.isConnected() && client.freshTelemetry() == null && client.control() == null, "link loss clears trainer state");
		active.emit(Ftms.INDOOR_BIKE_DATA, new byte[] {0x40, 0, 0, 0, (byte) 250, 0});
		client.tick(config);
		backend.last().complete(Ftms.SERVICE);
		require(client.isConnected() && client.freshTelemetry() == null, "automatic reconnect does not inherit stale power");
		client.disconnect();
		int count = backend.attempts.size();
		client.tick(config);
		require(backend.attempts.size() == count, "manual trainer disconnect suppresses reconnect");
		client.connect(device);
		backend.last().result.completeExceptionally(new IllegalStateException("connection failed"));
		require(!client.isConnected() && says(client.message(), "voxvelo_fitness_lib.connection.failed"), "trainer failure is visible and recoverable");
	}

	/** Resistance reaches the trainer: the replies are asked for as indications, then control is taken and the grade sent. */
	private static void controlPoint() {
		Backend backend = new Backend();
		FtmsClient client = new FtmsClient(new BluetoothManager(backend), GAME);
		BluetoothDevice device = new BluetoothDevice("trainer", "Trainer", -40, Set.of(Ftms.SERVICE));
		client.connect(device);
		backend.discover(device);
		backend.last().features[5] = 0x20; // Target Setting Features bit 13: indoor bike simulation parameters
		backend.last().respond = true;
		Connection link = backend.last().complete(Ftms.SERVICE);
		require(link.indicating.contains(Ftms.CONTROL_POINT), "the control point is subscribed to as indications, not notifications");
		require(client.control() != null && client.control().support() == FtmsControl.Support.SUPPORTED, "the trainer is offered resistance control");
		client.control().apply(new TrainerSimulation.Target(4.0, 0.004));
		require(link.commands.equals(List.of(0x00, 0x07, 0x11)), "request control, start, then the simulation parameters, got " + link.commands);
		client.control().apply(new TrainerSimulation.Target(6.0, 0.004));
		require(link.commands.equals(List.of(0x00, 0x07, 0x11, 0x11)), "control is kept: only the new parameters are sent, got " + link.commands);

		// A trainer whose answers never reach us (the indication subscription does not take) still gets its resistance:
		// after one unanswered command the rest is sent without waiting, and later updates go out at once.
		Backend silent = new Backend();
		FtmsClient quiet = new FtmsClient(new BluetoothManager(silent), GAME);
		quiet.connect(device);
		silent.discover(device);
		silent.last().features[5] = 0x20;
		Connection mute = silent.last().complete(Ftms.SERVICE);
		quiet.control().apply(new TrainerSimulation.Target(4.0, 0.004));
		awaitCommands(mute, 3);
		require(mute.commands.equals(List.of(0x00, 0x07, 0x11)), "an unanswered trainer still gets start and the grade, got " + mute.commands);
		quiet.control().apply(new TrainerSimulation.Target(-3.0, 0.004));
		awaitCommands(mute, 4);
		require(mute.commands.equals(List.of(0x00, 0x07, 0x11, 0x11)), "updates go out without waiting for answers, got " + mute.commands);

		// Answers to some other command do not stand in for the one being waited for.
		Backend mixed = new Backend();
		FtmsClient confused = new FtmsClient(new BluetoothManager(mixed), GAME);
		confused.connect(device);
		mixed.discover(device);
		mixed.last().features[5] = 0x20;
		Connection wrong = mixed.last().complete(Ftms.SERVICE);
		wrong.indicating.add(Ftms.CONTROL_POINT);
		wrong.handlers.get(Ftms.CONTROL_POINT).accept(new byte[] {(byte) 0x80, 0x07, 0x01});
		confused.control().apply(new TrainerSimulation.Target(2.0, 0.004));
		awaitCommands(wrong, 3);
		require(wrong.commands.equals(List.of(0x00, 0x07, 0x11)), "a stray answer is not mistaken for the awaited one, got " + wrong.commands);
	}

	/** Waits for control commands that go out after a timeout on another thread. */
	private static void awaitCommands(Connection link, int count) {
		long end = System.currentTimeMillis() + 8_000;
		while (link.commands.size() < count && System.currentTimeMillis() < end) {
			try {
				Thread.sleep(25);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	private static void controller() {
		Backend backend = new Backend();
		ObcClient client = new ObcClient(new BluetoothManager(backend), GAME);
		BluetoothDevice advertised = new BluetoothDevice("controller", "Controller", -40, Set.of(Obc.BLE_SERVICE));
		ObcDevice device = ObcDevice.bluetooth(advertised.id(), advertised.name());
		FitnessConfig config = new FitnessConfig();
		config.obcMode = ObcMode.BLUETOOTH;
		config.obcAutoConnect = true;
		config.obcPreferredKey = device.key();
		try {
			client.connect(device);
			client.disconnect();
			backend.discover(advertised);
			require(backend.attempts.isEmpty(), "cancelled discovery must not connect a controller");
			client.connect(device);
			Attempt old = backend.last();
			client.disconnect();
			require(!old.complete(Obc.BLE_SERVICE).connected, "late controller connection is closed");
			client.connect(device);
			Connection active = backend.last().complete(Obc.BLE_SERVICE);
			require(client.isConnected(), "controller connects");
			byte[] held = {1, 0x18, 1, 0x1A, 1, 1, 1, 3, 5};
			active.emit(Obc.BLE_BUTTON_STATE, held);
			require(client.controls().steering() == -1 && client.controls().brake() == 1, "controller notifications hold steering and brake");
			old.lost.run();
			require(client.isConnected(), "old controller disconnect cannot tear down replacement");
			backend.last().lost.run();
			assertReleased(client);
			active.emit(Obc.BLE_BUTTON_STATE, held);
			assertReleased(client);
			client.tick(config);
			backend.last().complete(Obc.BLE_SERVICE);
			require(client.isConnected(), "controller automatically reconnects after link loss");
			client.disconnect();
			int count = backend.attempts.size();
			client.tick(config);
			require(count == backend.attempts.size(), "manual controller disconnect suppresses reconnect");
			client.connect(device);
			backend.last().complete(Obc.BLE_SERVICE);
			config.obcMode = ObcMode.OFF;
			client.tick(config);
			require(!client.isConnected(), "disabling controller disconnects it");
			assertReleased(client);
		} finally {
			client.shutdown();
		}
	}

	private static void assertReleased(ObcClient client) {
		require(client.controls().steering() == 0 && client.controls().brake() == 0
			&& client.controls().consumeShifts() == 0 && client.controls().consumeGearSet() == 0,
			"disconnect releases held controls and queued gear changes");
	}

	private static final class Backend implements BluetoothBackend {
		final List<Attempt> attempts = new ArrayList<>();
		Consumer<BluetoothDevice> discovery;
		boolean scanning;
		public boolean isAvailable() { return true; }
		public String unavailableReason() { return ""; }
		public void startScan(Consumer<BluetoothDevice> listener) { discovery = listener; scanning = true; }
		public void stopScan() { scanning = false; }
		public boolean isScanning() { return scanning; }
		public void shutdown() { scanning = false; }
		void discover(BluetoothDevice device) { discovery.accept(device); }
		Attempt last() { return attempts.getLast(); }
		public CompletableFuture<BluetoothConnection> connect(String id, Runnable lost) {
			Attempt attempt = new Attempt(lost);
			attempts.add(attempt);
			return attempt.result;
		}
	}

	private static final class Attempt {
		final Runnable lost;
		final CompletableFuture<BluetoothConnection> result = new CompletableFuture<>();
		/** What the trainer reports as its Fitness Machine Feature; set before completing the attempt. */
		final byte[] features = new byte[8];
		/** Whether the trainer answers control point commands, as a real one does. */
		boolean respond;
		Attempt(Runnable lost) { this.lost = lost; }
		Connection complete(String... services) {
			Connection connection = new Connection(services);
			System.arraycopy(features, 0, connection.features, 0, features.length);
			connection.respond = respond;
			result.complete(connection);
			return connection;
		}
	}

	private static final class Connection implements BluetoothConnection {
		final Set<String> services;
		final Map<String, Consumer<byte[]>> handlers = new HashMap<>();
		/** Characteristics subscribed to as indications rather than notifications. */
		final Set<String> indicating = new HashSet<>();
		boolean connected = true;
		/** When set, disconnecting stays pending until the test completes it, like a platform that is slow to let go. */
		CompletableFuture<Void> closing;
		Connection(String... services) { this.services = Set.of(services); }
		public String deviceId() { return "fake"; }
		public boolean isConnected() { return connected; }
		public Set<String> serviceUuids() { return services; }
		final byte[] features = new byte[8];
		boolean respond;
		/** First byte (the op code) of every command written to the control point. */
		final List<Integer> commands = new java.util.concurrent.CopyOnWriteArrayList<>();
		public CompletableFuture<byte[]> read(String service, String characteristic) {
			return CompletableFuture.completedFuture(Ftms.FEATURE.equals(characteristic) ? features.clone() : new byte[8]);
		}
		public CompletableFuture<Void> write(String service, String characteristic, byte[] data, boolean response) {
			if (Ftms.CONTROL_POINT.equals(characteristic)) {
				commands.add(data[0] & 0xFF);
				// A trainer answers on the control point with response code 0x80, the op code and success; it can only do so
				// to a client that asked for indications.
				if (respond && indicating.contains(characteristic)) {
					handlers.get(characteristic).accept(new byte[] {(byte) 0x80, data[0], 0x01});
				}
			}
			return CompletableFuture.completedFuture(null);
		}
		public CompletableFuture<Void> subscribe(String service, String characteristic, Consumer<byte[]> handler) {
			handlers.put(characteristic, handler);
			return CompletableFuture.completedFuture(null);
		}
		public CompletableFuture<Void> indicate(String service, String characteristic, Consumer<byte[]> handler) {
			indicating.add(characteristic);
			return subscribe(service, characteristic, handler);
		}
		void emit(String characteristic, byte[] data) { handlers.get(characteristic).accept(data); }
		public CompletableFuture<Void> disconnect() {
			connected = false;
			return closing != null ? closing : CompletableFuture.completedFuture(null);
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
