package dev.michidk.voxvelo.fitnesslib.client.bluetooth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One physical device often plays several roles at once (a smart trainer that is also a power meter and a heart rate
 * source), and a platform lets only one connection exist per device. This hands every caller its own view of a single
 * shared connection: the link is made for the first caller, closed when the last one lets go, and its loss is reported
 * to everyone using it.
 *
 * <p>GATT operations of all views run one at a time, because platform Bluetooth stacks do not like overlapping them.
 * After a link is closed the next connection to the same device waits (briefly) until the platform has let go of it,
 * since connecting while the device is still disconnecting fails.
 */
final class SharedConnections {
	private static final long OPERATION_TIMEOUT_MILLIS = 15_000;
	private static final long CLOSE_WAIT_MILLIS = 5_000;

	private final BluetoothBackend backend;
	private final Map<String, Link> links = new HashMap<>();
	private final Map<String, CompletableFuture<Void>> closing = new HashMap<>();

	SharedConnections(BluetoothBackend backend) {
		this.backend = backend;
	}

	/** Connects to the device, or joins the connection already made for it. {@code onLost} fires once if the link drops. */
	CompletableFuture<BluetoothConnection> connect(String deviceId, Runnable onLost) {
		Link link;
		boolean created;
		CompletableFuture<Void> before;
		Handle[] handle = new Handle[1];
		synchronized (this) {
			link = this.links.get(deviceId);
			created = link == null;
			if (created) {
				link = new Link(deviceId);
				this.links.put(deviceId, link);
			}
			handle[0] = new Handle(link, onLost);
			link.handles.add(handle[0]);
			before = this.closing.getOrDefault(deviceId, CompletableFuture.completedFuture(null));
		}
		if (created) {
			Link made = link;
			before.thenCompose(ignored -> this.backend.connect(deviceId, () -> this.lost(made))).whenComplete((connection, error) -> {
				if (error != null) {
					synchronized (this) {
						this.links.remove(deviceId, made);
					}
					made.ready.completeExceptionally(error);
				} else {
					made.ready.complete(connection);
				}
			});
		}
		return link.ready.thenApply(connection -> {
			handle[0].connection = connection;
			return handle[0];
		});
	}

	/**
	 * Closes every link and waits, for at most the given time, until the platform has let go of them. Leaving a
	 * connection open when the game exits keeps the device (and the exiting process) tied up. A link still being made
	 * is closed as soon as it is up, even if that is after the wait.
	 */
	void closeAll(long timeoutMillis) {
		List<Link> open;
		synchronized (this) {
			open = new ArrayList<>(this.links.values());
			this.links.clear();
		}
		List<CompletableFuture<Void>> closing = new ArrayList<>();
		for (Link link : open) {
			link.lost = true;
			closing.add(link.ready.thenCompose(SharedConnections::disconnectQuietly).exceptionally(error -> null));
		}
		try {
			CompletableFuture.allOf(closing.toArray(new CompletableFuture<?>[0])).get(timeoutMillis, TimeUnit.MILLISECONDS);
		} catch (Exception ignored) {
			// the platform did not answer in time; do not hold the game up any longer
		}
	}

	private static CompletableFuture<Void> disconnectQuietly(BluetoothConnection connection) {
		try {
			return connection.disconnect();
		} catch (Throwable ignored) {
			// shutting down anyway
			return CompletableFuture.completedFuture(null);
		}
	}

	private void lost(Link link) {
		List<Handle> affected;
		synchronized (this) {
			this.links.remove(link.deviceId, link);
			link.lost = true;
			affected = new ArrayList<>(link.handles);
			link.handles.clear();
		}
		for (Handle handle : affected) {
			handle.onLost.run();
		}
	}

	private CompletableFuture<Void> release(Handle handle) {
		Link link = handle.link;
		synchronized (this) {
			if (!link.handles.remove(handle) || !link.handles.isEmpty()) {
				return CompletableFuture.completedFuture(null);
			}
			this.links.remove(link.deviceId, link);
		}
		CompletableFuture<Void> closed;
		try {
			closed = handle.connection.disconnect();
		} catch (Throwable t) {
			closed = CompletableFuture.completedFuture(null);
		}
		CompletableFuture<Void> done = new CompletableFuture<Void>().completeOnTimeout(null, CLOSE_WAIT_MILLIS, TimeUnit.MILLISECONDS);
		closed.whenComplete((ignored, error) -> done.complete(null));
		synchronized (this) {
			this.closing.put(link.deviceId, done);
		}
		done.whenComplete((ignored, error) -> {
			synchronized (this) {
				this.closing.remove(link.deviceId, done);
			}
		});
		return done;
	}

	private static final class Link {
		final String deviceId;
		final CompletableFuture<BluetoothConnection> ready = new CompletableFuture<>();
		/** Guarded by the owning SharedConnections. */
		final List<Handle> handles = new ArrayList<>();
		volatile boolean lost;
		private CompletableFuture<?> tail = CompletableFuture.completedFuture(null);

		Link(String deviceId) {
			this.deviceId = deviceId;
		}

		/** Runs the operation after every earlier one has finished, whether those worked or not. */
		synchronized <T> CompletableFuture<T> serial(Supplier<CompletableFuture<T>> operation) {
			CompletableFuture<T> result = new CompletableFuture<>();
			CompletableFuture<?> previous = this.tail;
			this.tail = result.handle((value, error) -> null);
			previous.whenComplete((ignored, ignoredError) -> {
				try {
					operation.get().whenComplete((value, error) -> {
						if (error != null) {
							result.completeExceptionally(error);
						} else {
							result.complete(value);
						}
					});
				} catch (Throwable t) {
					result.completeExceptionally(t);
				}
			});
			return result.orTimeout(OPERATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
		}
	}

	/** One caller's view of a shared link. Disconnecting only gives up this caller's share. */
	private final class Handle implements BluetoothConnection {
		private final Link link;
		private final Runnable onLost;
		volatile BluetoothConnection connection;

		Handle(Link link, Runnable onLost) {
			this.link = link;
			this.onLost = onLost;
		}

		@Override
		public String deviceId() {
			return this.link.deviceId;
		}

		@Override
		public boolean isConnected() {
			return !this.link.lost && this.connection != null && this.connection.isConnected();
		}

		@Override
		public Set<String> serviceUuids() {
			return this.connection.serviceUuids();
		}

		@Override
		public CompletableFuture<byte[]> read(String serviceUuid, String characteristicUuid) {
			return this.link.serial(() -> this.connection.read(serviceUuid, characteristicUuid));
		}

		@Override
		public CompletableFuture<Void> write(String serviceUuid, String characteristicUuid, byte[] data, boolean withResponse) {
			return this.link.serial(() -> this.connection.write(serviceUuid, characteristicUuid, data, withResponse));
		}

		@Override
		public CompletableFuture<Void> subscribe(String serviceUuid, String characteristicUuid, Consumer<byte[]> handler) {
			return this.link.serial(() -> this.connection.subscribe(serviceUuid, characteristicUuid, handler));
		}

		@Override
		public CompletableFuture<Void> indicate(String serviceUuid, String characteristicUuid, Consumer<byte[]> handler) {
			return this.link.serial(() -> this.connection.indicate(serviceUuid, characteristicUuid, handler));
		}

		@Override
		public CompletableFuture<Void> disconnect() {
			return SharedConnections.this.release(this);
		}
	}
}
