package dev.michidk.voxvelo.fitnesslib.client.bluetooth;

import dev.michidk.voxvelo.fitnesslib.client.FitnessRuntime;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The bundled {@code voxvelo_ble} native library (a small C ABI over the Rust library btleplug, see
 * {@code native/ble}), called through the FFM API. There is one per process: the library has a single callback.
 *
 * <p>Every call returns at once; its result completes the returned future from a Bluetooth thread. Device, disconnect
 * and notification events go to the registered {@link Events}.
 */
// Calling native code is this class's purpose; the FFM API marks those calls as restricted.
@SuppressWarnings("restricted")
final class BtleplugNative {
	private static final int RESULT = 1;
	private static final int DEVICE = 2;
	private static final int DISCONNECTED = 3;
	private static final int NOTIFICATION = 4;

	private static final ValueLayout.OfLong REQUEST = ValueLayout.JAVA_LONG;
	private static final ValueLayout.OfInt STATUS = ValueLayout.JAVA_INT;

	private static volatile BtleplugNative instance;

	/** Receives events from Bluetooth threads. Implementations must not throw. */
	interface Events {
		void device(BluetoothDevice device);

		void disconnected(String deviceId);

		void notification(String deviceId, String serviceUuid, String characteristicUuid, byte[] value);
	}

	private final Map<Long, CompletableFuture<ByteBuffer>> pending = new ConcurrentHashMap<>();
	private final AtomicLong nextRequest = new AtomicLong(1);
	private volatile Events events;

	private final MethodHandle open;
	private final MethodHandle scanStart;
	private final MethodHandle scanStop;
	private final MethodHandle connect;
	private final MethodHandle disconnect;
	private final MethodHandle services;
	private final MethodHandle read;
	private final MethodHandle write;
	private final MethodHandle subscribe;

	/** Loads the library for this platform. Throws if there is none or it does not load. */
	static synchronized BtleplugNative load() throws Throwable {
		if (instance == null) {
			instance = new BtleplugNative(SymbolLookup.libraryLookup(extract(), Arena.global()));
		}
		return instance;
	}

	private BtleplugNative(SymbolLookup library) throws Throwable {
		Linker linker = Linker.nativeLinker();
		this.open = downcall(linker, library, "vvble_open", REQUEST);
		this.scanStart = downcall(linker, library, "vvble_scan_start", REQUEST);
		this.scanStop = downcall(linker, library, "vvble_scan_stop", REQUEST);
		this.connect = downcall(linker, library, "vvble_connect", REQUEST, ValueLayout.ADDRESS);
		this.disconnect = downcall(linker, library, "vvble_disconnect", REQUEST, ValueLayout.ADDRESS);
		this.services = downcall(linker, library, "vvble_services", REQUEST, ValueLayout.ADDRESS);
		this.read = downcall(linker, library, "vvble_read", REQUEST, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
		this.write = downcall(linker, library, "vvble_write", REQUEST, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
			ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_BOOLEAN);
		this.subscribe = downcall(linker, library, "vvble_subscribe", REQUEST, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

		MethodHandle onEvent = MethodHandles.lookup()
			.findVirtual(BtleplugNative.class, "onEvent",
				MethodType.methodType(void.class, int.class, long.class, int.class, MemorySegment.class, long.class))
			.bindTo(this);
		MemorySegment callback = linker.upcallStub(onEvent,
			FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, REQUEST, STATUS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG), Arena.global());
		MethodHandle init = linker.downcallHandle(library.findOrThrow("vvble_init"), FunctionDescriptor.of(STATUS, ValueLayout.ADDRESS));
		if ((int) init.invokeExact(callback) != 0) {
			throw new IllegalStateException("Bluetooth runtime failed to start");
		}
	}

	private static MethodHandle downcall(Linker linker, SymbolLookup library, String name, ValueLayout... arguments) {
		return linker.downcallHandle(library.findOrThrow(name), FunctionDescriptor.of(STATUS, arguments));
	}

	/**
	 * Copies the library for this OS and architecture out of the mod jar, since it can only be loaded from a file. Throws
	 * {@link UnsupportedOperationException} when the jar has no library for this platform.
	 */
	private static Path extract() throws IOException {
		String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
		String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
		String platform;
		String file;
		if (os.contains("win")) {
			platform = "windows";
			file = "voxvelo_ble.dll";
		} else if (os.contains("mac")) {
			platform = "macos";
			file = "libvoxvelo_ble.dylib";
		} else if (os.contains("linux")) {
			platform = "linux";
			file = "libvoxvelo_ble.so";
		} else {
			throw new UnsupportedOperationException("Bluetooth is not supported on " + os);
		}
		String cpu = switch (arch) {
			case "amd64", "x86_64" -> "x86_64";
			case "aarch64", "arm64" -> "aarch64";
			default -> throw new UnsupportedOperationException("Bluetooth is not supported on " + arch);
		};
		String resource = "/natives/" + platform + "-" + cpu + "/" + file;
		try (InputStream in = BtleplugNative.class.getResourceAsStream(resource)) {
			if (in == null) {
				throw new UnsupportedOperationException("No Bluetooth library for " + platform + "-" + cpu);
			}
			Path dir = Files.createTempDirectory("voxvelo-ble");
			Path target = dir.resolve(file);
			Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
			target.toFile().deleteOnExit();
			dir.toFile().deleteOnExit();
			return target;
		}
	}

	void setEvents(Events events) {
		this.events = events;
	}

	/** Completes with the adapter description, or fails with a reason fit for the settings screen. */
	CompletableFuture<String> open() {
		return this.call(id -> (int) this.open.invokeExact(id)).thenApply(BtleplugNative::string);
	}

	CompletableFuture<Void> startScan() {
		return this.call(id -> (int) this.scanStart.invokeExact(id)).thenApply(b -> null);
	}

	CompletableFuture<Void> stopScan() {
		return this.call(id -> (int) this.scanStop.invokeExact(id)).thenApply(b -> null);
	}

	CompletableFuture<Void> connect(String deviceId) {
		return this.call(id -> {
			try (Arena arena = Arena.ofConfined()) {
				return (int) this.connect.invokeExact(id, arena.allocateFrom(deviceId));
			}
		}).thenApply(b -> null);
	}

	CompletableFuture<Void> disconnect(String deviceId) {
		return this.call(id -> {
			try (Arena arena = Arena.ofConfined()) {
				return (int) this.disconnect.invokeExact(id, arena.allocateFrom(deviceId));
			}
		}).thenApply(b -> null);
	}

	/** Lower-case service UUID to its characteristics, each lower-case UUID to its GATT property flags. */
	CompletableFuture<Map<String, Map<String, Integer>>> services(String deviceId) {
		return this.call(id -> {
			try (Arena arena = Arena.ofConfined()) {
				return (int) this.services.invokeExact(id, arena.allocateFrom(deviceId));
			}
		}).thenApply(buffer -> {
			Map<String, Map<String, Integer>> result = new ConcurrentHashMap<>();
			int serviceCount = Short.toUnsignedInt(buffer.getShort());
			for (int s = 0; s < serviceCount; s++) {
				String service = string(buffer).toLowerCase(Locale.ROOT);
				Map<String, Integer> characteristics = new ConcurrentHashMap<>();
				int characteristicCount = Short.toUnsignedInt(buffer.getShort());
				for (int c = 0; c < characteristicCount; c++) {
					characteristics.put(string(buffer).toLowerCase(Locale.ROOT), Byte.toUnsignedInt(buffer.get()));
				}
				result.put(service, characteristics);
			}
			return result;
		});
	}

	CompletableFuture<byte[]> read(String deviceId, String serviceUuid, String characteristicUuid) {
		return this.call(id -> {
			try (Arena arena = Arena.ofConfined()) {
				return (int) this.read.invokeExact(id, arena.allocateFrom(deviceId), arena.allocateFrom(serviceUuid),
					arena.allocateFrom(characteristicUuid));
			}
		}).thenApply(BtleplugNative::remaining);
	}

	CompletableFuture<Void> write(String deviceId, String serviceUuid, String characteristicUuid, byte[] data, boolean withResponse) {
		return this.call(id -> {
			try (Arena arena = Arena.ofConfined()) {
				MemorySegment bytes = data.length == 0 ? MemorySegment.NULL : arena.allocateFrom(ValueLayout.JAVA_BYTE, data);
				return (int) this.write.invokeExact(id, arena.allocateFrom(deviceId), arena.allocateFrom(serviceUuid),
					arena.allocateFrom(characteristicUuid), bytes, (long) data.length, withResponse);
			}
		}).thenApply(b -> null);
	}

	CompletableFuture<Void> subscribe(String deviceId, String serviceUuid, String characteristicUuid) {
		return this.call(id -> {
			try (Arena arena = Arena.ofConfined()) {
				return (int) this.subscribe.invokeExact(id, arena.allocateFrom(deviceId), arena.allocateFrom(serviceUuid),
					arena.allocateFrom(characteristicUuid));
			}
		}).thenApply(b -> null);
	}

	@FunctionalInterface
	private interface Submit {
		int submit(long request) throws Throwable;
	}

	private CompletableFuture<ByteBuffer> call(Submit submit) {
		long request = this.nextRequest.getAndIncrement();
		CompletableFuture<ByteBuffer> future = new CompletableFuture<>();
		this.pending.put(request, future);
		try {
			if (submit.submit(request) != 0) {
				throw new IllegalStateException("Bluetooth call was rejected");
			}
		} catch (Throwable t) {
			this.pending.remove(request);
			future.completeExceptionally(t);
		}
		return future;
	}

	/** The upcall from native code. Must never throw: an exception escaping an upcall ends the whole process. */
	private void onEvent(int kind, long request, int status, MemorySegment payload, long length) {
		try {
			ByteBuffer buffer = ByteBuffer.wrap(length == 0 ? new byte[0] : payload.reinterpret(length).toArray(ValueLayout.JAVA_BYTE))
				.order(ByteOrder.LITTLE_ENDIAN);
			switch (kind) {
				case RESULT -> {
					CompletableFuture<ByteBuffer> future = this.pending.remove(request);
					if (future == null) {
						return;
					}
					if (status == 0) {
						future.complete(buffer);
					} else {
						future.completeExceptionally(new BluetoothException(new String(remaining(buffer), StandardCharsets.UTF_8)));
					}
				}
				case DEVICE -> {
					Events listener = this.events;
					String id = string(buffer);
					String name = string(buffer);
					int rssi = buffer.getShort();
					int count = Short.toUnsignedInt(buffer.getShort());
					Set<String> services = new HashSet<>();
					for (int i = 0; i < count; i++) {
						services.add(string(buffer).toLowerCase(Locale.ROOT));
					}
					if (listener != null) {
						// No reading yet counts as the weakest possible signal.
						listener.device(new BluetoothDevice(id, name, rssi == Short.MIN_VALUE ? -127 : rssi, Set.copyOf(services)));
					}
				}
				case DISCONNECTED -> {
					Events listener = this.events;
					if (listener != null) {
						listener.disconnected(string(buffer));
					}
				}
				case NOTIFICATION -> {
					Events listener = this.events;
					if (listener != null) {
						String id = string(buffer);
						String service = string(buffer).toLowerCase(Locale.ROOT);
						String characteristic = string(buffer).toLowerCase(Locale.ROOT);
						listener.notification(id, service, characteristic, remaining(buffer));
					}
				}
				default -> {
					// unknown event from a newer library; ignore
				}
			}
		} catch (Throwable t) {
			try {
				FitnessRuntime.LOGGER.warn("Bluetooth event failed", t);
			} catch (Throwable ignored) {
				// never let anything escape into native code
			}
		}
	}

	private static String string(ByteBuffer buffer) {
		byte[] bytes = new byte[Short.toUnsignedInt(buffer.getShort())];
		buffer.get(bytes);
		return new String(bytes, StandardCharsets.UTF_8);
	}

	private static byte[] remaining(ByteBuffer buffer) {
		byte[] bytes = new byte[buffer.remaining()];
		buffer.get(bytes);
		return bytes;
	}

	/** A Bluetooth operation the platform reported as failed. */
	static final class BluetoothException extends RuntimeException {
		BluetoothException(String message) {
			super(message);
		}
	}
}
