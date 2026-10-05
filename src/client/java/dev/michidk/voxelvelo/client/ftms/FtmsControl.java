package dev.michidk.voxelvelo.client.ftms;

import dev.michidk.voxelvelo.VoxelVelo;
import dev.michidk.voxelvelo.client.bluetooth.BluetoothConnection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The Fitness Machine Control Point of one connected trainer: takes control, then sends simulation
 * parameters. Entirely optional; if the trainer lacks the feature or anything fails, riding is unaffected.
 *
 * <p>Calls never block. Only one command is in flight at a time and newer targets replace queued older ones,
 * so a slow trainer is never flooded.
 */
public final class FtmsControl {
	private static final int OP_REQUEST_CONTROL = 0x00;
	private static final int OP_START = 0x07;
	private static final int OP_RESPONSE = 0x80;
	private static final int RESULT_SUCCESS = 0x01;
	private static final int RESULT_CONTROL_NOT_PERMITTED = 0x05;
	/** Bit 13 of the Target Setting Features: Indoor Bike Simulation Parameters supported. */
	private static final int FEATURE_SIMULATION_BIT = 13;
	private static final long RESPONSE_TIMEOUT_MILLIS = 3_000;
	private static final long RETRY_AFTER_FAILURE_MILLIS = 10_000;

	public enum Support {
		UNKNOWN,
		UNSUPPORTED,
		SUPPORTED
	}

	private final BluetoothConnection connection;
	private final AtomicReference<CompletableFuture<Integer>> pendingResponse = new AtomicReference<>();
	private final AtomicReference<TrainerSimulation.Target> queued = new AtomicReference<>();
	private final AtomicBoolean busy = new AtomicBoolean();

	private volatile Support support = Support.UNKNOWN;
	private volatile boolean controlling;
	private volatile long retryNotBefore;

	public FtmsControl(BluetoothConnection connection) {
		this.connection = connection;
	}

	public Support support() {
		return this.support;
	}

	/** Listens for control point responses and reads which features the trainer offers. Fire and forget. */
	public void initialise() {
		this.connection.subscribe(Ftms.SERVICE, Ftms.CONTROL_POINT, this::onIndication)
			.exceptionally(error -> {
				VoxelVelo.LOGGER.debug("Trainer control point not available", error);
				this.support = Support.UNSUPPORTED;
				return null;
			});
		this.connection.read(Ftms.SERVICE, Ftms.FEATURE).whenComplete((data, error) -> {
			if (error != null || data == null || data.length < 8) {
				this.support = Support.UNSUPPORTED;
				return;
			}
			long targets = (data[4] & 0xFFL) | (data[5] & 0xFFL) << 8 | (data[6] & 0xFFL) << 16 | (data[7] & 0xFFL) << 24;
			this.support = (targets >> FEATURE_SIMULATION_BIT & 1L) != 0 ? Support.SUPPORTED : Support.UNSUPPORTED;
			VoxelVelo.LOGGER.info("Trainer resistance control: {}", this.support);
		});
	}

	/** Asks the trainer to simulate this target. Safe to call every tick; a no-op unless supported. */
	public void apply(TrainerSimulation.Target target) {
		if (this.support != Support.SUPPORTED || System.currentTimeMillis() < this.retryNotBefore) {
			return;
		}
		this.queued.set(target);
		this.pump();
	}

	/** Puts the trainer back to a flat road. Call when the player stops riding. */
	public void neutral() {
		this.apply(TrainerSimulation.Target.NEUTRAL);
	}

	private void pump() {
		if (!this.busy.compareAndSet(false, true)) {
			return;
		}
		TrainerSimulation.Target next = this.queued.getAndSet(null);
		if (next == null) {
			this.busy.set(false);
			return;
		}
		this.send(next).whenComplete((ok, error) -> {
			if (error != null || !Boolean.TRUE.equals(ok)) {
				this.controlling = false;
				this.retryNotBefore = System.currentTimeMillis() + RETRY_AFTER_FAILURE_MILLIS;
				VoxelVelo.LOGGER.debug("Trainer simulation command failed", error);
			}
			this.busy.set(false);
			if (this.queued.get() != null) {
				this.pump();
			}
		});
	}

	private CompletableFuture<Boolean> send(TrainerSimulation.Target target) {
		CompletableFuture<Boolean> ready = this.controlling
			? CompletableFuture.completedFuture(true)
			: this.command(OP_REQUEST_CONTROL)
				.thenCompose(ok -> ok ? this.command(OP_START) : CompletableFuture.completedFuture(false))
				.thenApply(ok -> {
					this.controlling = ok;
					return ok;
				});
		return ready.thenCompose(ok -> ok
			? this.connection.write(Ftms.SERVICE, Ftms.CONTROL_POINT, TrainerSimulation.encode(target), true).thenApply(v -> true)
			: CompletableFuture.completedFuture(false));
	}

	/** Writes a one-byte command and completes with whether the trainer answered with success. */
	private CompletableFuture<Boolean> command(int opCode) {
		CompletableFuture<Integer> response = new CompletableFuture<Integer>().orTimeout(RESPONSE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
		this.pendingResponse.set(response);
		return this.connection.write(Ftms.SERVICE, Ftms.CONTROL_POINT, new byte[] { (byte) opCode }, true)
			.thenCompose(v -> response)
			.thenApply(result -> result == RESULT_SUCCESS);
	}

	private void onIndication(byte[] data) {
		if (data == null || data.length < 3 || (data[0] & 0xFF) != OP_RESPONSE) {
			return;
		}
		if ((data[2] & 0xFF) == RESULT_CONTROL_NOT_PERMITTED) {
			// Lost control (for example another app took over): ask again with the next command.
			this.controlling = false;
		}
		CompletableFuture<Integer> waiting = this.pendingResponse.get();
		if (waiting != null) {
			waiting.complete(data[2] & 0xFF);
		}
	}
}
