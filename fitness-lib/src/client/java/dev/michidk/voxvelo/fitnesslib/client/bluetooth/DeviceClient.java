package dev.michidk.voxvelo.fitnesslib.client.bluetooth;

import dev.michidk.voxvelo.fitnesslib.client.FitnessRuntime;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The connection life cycle every device client shares: connecting, reconnecting with growing delays while a device is
 * saved (unless the player disconnected on purpose), and tearing down. Trainers, sensors and controllers differ only in
 * how they reach their device and what they do with its data.
 *
 * <p>All state belongs to the game thread. Callbacks from Bluetooth or socket threads are handed to the game thread
 * executor before they touch anything. Every attempt has a generation number, so callbacks of an attempt that has since
 * been torn down are dropped, and a connection that completes after its teardown is closed again.
 */
public abstract class DeviceClient {
	/** Runs tasks on the client thread. Looked up on use: clients are made before the game has finished starting. */
	public static final Executor GAME_THREAD = task -> Minecraft.getInstance().execute(task);

	private static final long FIND_TIMEOUT_MILLIS = 15_000;
	private static final long MIN_RETRY_MILLIS = 4_000;
	private static final long MAX_RETRY_MILLIS = 30_000;

	protected final BluetoothManager bluetooth;
	private final Executor gameThread;
	/** What the device is called in the log, such as "trainer". */
	private final String logName;

	private ConnectionState state = ConnectionState.DISCONNECTED;
	private String deviceName = "";
	private @Nullable Component message;
	private @Nullable BluetoothConnection connection;
	/** Incremented on every attempt and teardown, so late callbacks of an older attempt are recognised. */
	private int generation;

	private boolean userDisconnected;
	private long nextAttemptAt;
	private long retryDelay = MIN_RETRY_MILLIS;

	/** A standard Bluetooth service, the characteristic whose notifications carry its data, and the service's translated name. */
	public record BleProfile(String service, String characteristic, String nameKey) {}

	protected DeviceClient(BluetoothManager bluetooth, Executor gameThread, String logName) {
		this.bluetooth = bluetooth;
		this.gameThread = gameThread;
		this.logName = logName;
	}

	public ConnectionState state() {
		return this.state;
	}

	public boolean isConnected() {
		return this.state == ConnectionState.CONNECTED;
	}

	public String deviceName() {
		return this.deviceName;
	}

	/** What the current attempt is doing or why the last one ended, for the UI. Null when there is nothing to say. */
	public @Nullable Component message() {
		return this.message;
	}

	/** Disconnects on the player request and suppresses auto-reconnect until the next manual connect. */
	public void disconnect() {
		this.userDisconnected = true;
		this.teardown(null);
	}

	/** The open Bluetooth link, if this client made one. */
	protected final @Nullable BluetoothConnection connection() {
		return this.connection;
	}

	protected final void setMessage(@Nullable Component message) {
		this.message = message;
	}

	/** Call before an attempt the player asked for: forgets an earlier deliberate disconnect and the grown retry delay. */
	protected final void playerRequested() {
		this.userDisconnected = false;
		this.retryDelay = MIN_RETRY_MILLIS;
	}

	/**
	 * Whether an automatic attempt should start now: disconnected, wanted, not disconnected deliberately, and the retry
	 * delay is over. Each true answer doubles the delay up to a limit; connecting resets it.
	 */
	protected final boolean autoAttemptDue(boolean wanted) {
		if (this.state != ConnectionState.DISCONNECTED || this.userDisconnected || !wanted) {
			return false;
		}
		long now = System.currentTimeMillis();
		if (now < this.nextAttemptAt) {
			return false;
		}
		this.nextAttemptAt = now + this.retryDelay;
		this.retryDelay = Math.min(this.retryDelay * 2, MAX_RETRY_MILLIS);
		return true;
	}

	/** Ends whatever is open and starts a new attempt. Returns the generation its callbacks must carry. */
	protected final int begin(String name, Component progress) {
		this.teardown(null);
		this.state = ConnectionState.CONNECTING;
		this.deviceName = name;
		this.message = progress;
		return this.generation;
	}

	protected final boolean isCurrent(int gen) {
		return gen == this.generation;
	}

	/** Wraps a transport callback: it runs on the game thread, and only while its attempt is still the current one. */
	protected final Runnable callback(int gen, Runnable task) {
		return () -> this.gameThread.execute(() -> {
			if (this.isCurrent(gen)) {
				task.run();
			}
		});
	}

	/** Like {@link #callback(int, Runnable)}, for callbacks that carry a value. */
	protected final <T> Consumer<T> dataCallback(int gen, Consumer<T> task) {
		return value -> this.gameThread.execute(() -> {
			if (this.isCurrent(gen)) {
				task.accept(value);
			}
		});
	}

	/** The attempt reached its device and data flows. */
	protected final void connected(int gen) {
		if (!this.isCurrent(gen)) {
			return;
		}
		this.state = ConnectionState.CONNECTED;
		this.message = null;
		this.retryDelay = MIN_RETRY_MILLIS;
		FitnessRuntime.LOGGER.info("Connected to {} {}", this.logName, this.deviceName);
		this.onConnected();
	}

	/** An established link went away. */
	protected final void lost(int gen, Component reason) {
		if (this.isCurrent(gen)) {
			FitnessRuntime.LOGGER.info("The {} {} disconnected", this.logName, this.deviceName);
			this.teardown(reason);
		}
	}

	/** The attempt failed. The player sees a translated reason; the log gets the exception's own text. */
	protected final void fail(int gen, Throwable error) {
		if (this.isCurrent(gen)) {
			FitnessRuntime.LOGGER.info("Connecting to the {} failed: {}", this.logName, logText(error));
			this.teardown(describe(error));
		}
	}

	/** Drops the current link and resets to DISCONNECTED. */
	protected final void teardown(@Nullable Component reason) {
		this.generation++;
		BluetoothConnection old = this.connection;
		this.connection = null;
		this.state = ConnectionState.DISCONNECTED;
		this.message = reason;
		this.onTeardown();
		if (old != null) {
			this.disconnectQuietly(old);
		}
	}

	/** Runs on the game thread once the device is connected. */
	protected void onConnected() {
	}

	/** Runs on the game thread whenever the client resets: data of the old link must go now. */
	protected void onTeardown() {
	}

	/**
	 * The Bluetooth path: finds the device (scanning for it if needed), connects, checks that it offers the service,
	 * subscribes to the characteristic and reports CONNECTED once data can flow. {@code onData} runs on the game thread.
	 */
	protected final void connectBluetooth(int gen, String deviceId, BleProfile profile, Consumer<byte[]> onData) {
		if (!this.bluetooth.isAvailable()) {
			String reason = this.bluetooth.unavailableReason();
			FitnessRuntime.LOGGER.info("Cannot connect to the {}: {}", this.logName, reason);
			this.teardown(Component.translatable("voxvelo_fitness_lib.bluetooth.unavailable", reason));
			return;
		}
		this.bluetooth.find(deviceId, FIND_TIMEOUT_MILLIS)
			.thenComposeAsync(device -> {
				this.bluetooth.releaseScan();
				if (!this.isCurrent(gen)) {
					return CompletableFuture.failedFuture(new CancellationException());
				}
				this.message = Component.translatable("voxvelo_fitness_lib.connection.connecting");
				return this.bluetooth.connect(device.id(), this.callback(gen, () -> this.lost(gen, Component.translatable("voxvelo_fitness_lib.connection.lost"))));
			}, this.gameThread)
			.thenComposeAsync(conn -> this.subscribe(gen, conn, profile, onData), this.gameThread)
			.whenCompleteAsync((ignored, error) -> {
				if (error != null) {
					this.fail(gen, error);
				}
			}, this.gameThread);
	}

	private CompletableFuture<Void> subscribe(int gen, BluetoothConnection conn, BleProfile profile, Consumer<byte[]> onData) {
		if (!this.isCurrent(gen)) {
			// Torn down while connecting: nobody uses this link any more.
			this.disconnectQuietly(conn);
			return CompletableFuture.completedFuture(null);
		}
		if (!conn.serviceUuids().contains(profile.service())) {
			this.disconnectQuietly(conn);
			throw new ConnectionFailure("Device does not offer service " + profile.service(),
				Component.translatable("voxvelo_fitness_lib.connection.missing_service", Component.translatable(profile.nameKey())));
		}
		this.connection = conn;
		Consumer<byte[]> deliver = this.dataCallback(gen, onData);
		// The data is used later on another thread; do not rely on the platform leaving its buffer alone meanwhile.
		return conn.subscribe(profile.service(), profile.characteristic(), data -> deliver.accept(data == null ? null : data.clone()))
			.thenRunAsync(() -> this.connected(gen), this.gameThread);
	}

	private void disconnectQuietly(BluetoothConnection conn) {
		try {
			conn.disconnect();
		} catch (Throwable t) {
			FitnessRuntime.LOGGER.debug("Error while disconnecting the {}", this.logName, t);
		}
	}

	private static Throwable unwrap(Throwable error) {
		Throwable cause = error;
		while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) {
			cause = cause.getCause();
		}
		return cause;
	}

	/** A translated reason for the player. Exception text is often English platform jargon, so it goes to the log only. */
	protected static Component describe(Throwable error) {
		Throwable cause = unwrap(error);
		if (cause instanceof ConnectionFailure failure) {
			return failure.reason();
		}
		if (cause instanceof DeviceNotFoundException) {
			return Component.translatable("voxvelo_fitness_lib.connection.not_found");
		}
		if (cause instanceof TimeoutException || cause instanceof SocketTimeoutException) {
			return Component.translatable("voxvelo_fitness_lib.connection.no_answer");
		}
		if (cause instanceof ConnectException) {
			return Component.translatable("voxvelo_fitness_lib.connection.refused");
		}
		if (cause instanceof UnknownHostException) {
			return Component.translatable("voxvelo_fitness_lib.connection.unknown_host");
		}
		return Component.translatable("voxvelo_fitness_lib.connection.failed");
	}

	protected static String logText(Throwable error) {
		Throwable cause = unwrap(error);
		String text = cause.getMessage();
		return text == null || text.isBlank() ? cause.getClass().getSimpleName() : cause.getClass().getSimpleName() + ": " + text;
	}
}
