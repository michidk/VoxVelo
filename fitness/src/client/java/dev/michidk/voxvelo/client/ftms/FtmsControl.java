package dev.michidk.voxvelo.client.ftms;

import dev.michidk.voxelfitness.client.FitnessRuntime;
import dev.michidk.voxvelo.client.bluetooth.BluetoothConnection;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
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
	/** Set Indoor Bike Simulation Parameters; the command itself is built by {@link TrainerSimulation#encode}. */
	static final int OP_SET_SIMULATION = 0x11;
	private static final int OP_RESPONSE = 0x80;
	private static final int RESULT_SUCCESS = 0x01;
	private static final int RESULT_CONTROL_NOT_PERMITTED = 0x05;
	/** The Fitness Machine Feature value: uint32 machine features, then uint32 target setting features. */
	private static final int TARGET_SETTING_FEATURES_OFFSET = 4;
	/** Bit 13 of the Target Setting Features: Indoor Bike Simulation Parameters supported. */
	private static final int FEATURE_SIMULATION_BIT = 13;
	private static final long RESPONSE_TIMEOUT_MILLIS = 2_000;
	private static final long RETRY_AFTER_FAILURE_MILLIS = 10_000;
	/** Stands in for the result code when the trainer gave no answer in time. */
	private static final int NO_ANSWER = -1;

	/** The control command whose answer is awaited; an answer names the command it belongs to. */
	private record Pending(int opCode, CompletableFuture<Integer> answer) {}

	public enum Support {
		UNSUPPORTED,
		SUPPORTED
	}

	private final BluetoothConnection connection;
	private final AtomicReference<Pending> pending = new AtomicReference<>();
	private final AtomicReference<TrainerSimulation.Target> queued = new AtomicReference<>();
	private final AtomicBoolean busy = new AtomicBoolean();

	/** Unsupported until the trainer's features have been read. */
	private volatile Support support = Support.UNSUPPORTED;
	private volatile boolean controlling;
	/**
	 * Set once the trainer's answers cannot be relied on: it did not answer a command although the write itself went
	 * through, or the answers could not be subscribed to. Commands are then sent without waiting for an answer, so the
	 * trainer still gets its resistance; without this, one lost answer would stop everything.
	 */
	private volatile boolean unanswered;
	/** Whether the log already says that simulation parameters reached the trainer. */
	private volatile boolean announced;
	/** Whether a failed command was logged already; a trainer that keeps refusing would otherwise log every few seconds. */
	private volatile boolean failureLogged;
	private volatile long retryNotBefore;

	public FtmsControl(BluetoothConnection connection) {
		this.connection = connection;
	}

	public Support support() {
		return this.support;
	}

	/**
	 * Listens for control point responses, then reads which features the trainer offers. Fire and forget. The two
	 * steps run one after the other (platform Bluetooth stacks dislike overlapping GATT operations). If the answers
	 * cannot be subscribed to, control still works: commands are then sent without waiting for them.
	 */
	public void initialise() {
		// The Control Point is write plus indicate by definition, so ask for indications outright.
		this.connection.indicate(Ftms.SERVICE, Ftms.CONTROL_POINT, this::onIndication)
			.handle((ignored, error) -> {
				if (error != null) {
					this.unanswered = true;
					FitnessRuntime.LOGGER.info("Could not listen for the trainer's control answers ({}); control commands will be sent without waiting for them",
						cause(error));
				}
				return null;
			})
			.thenCompose(ignored -> this.connection.read(Ftms.SERVICE, Ftms.FEATURE))
			.whenComplete((data, error) -> {
				if (error != null) {
					FitnessRuntime.LOGGER.info("Trainer control point not available: {}", cause(error));
					this.support = Support.UNSUPPORTED;
					return;
				}
				if (data == null || data.length < TARGET_SETTING_FEATURES_OFFSET + Integer.BYTES) {
					this.support = Support.UNSUPPORTED;
					return;
				}
				long targets = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt(TARGET_SETTING_FEATURES_OFFSET) & 0xFFFFFFFFL;
				this.support = (targets >> FEATURE_SIMULATION_BIT & 1L) != 0 ? Support.SUPPORTED : Support.UNSUPPORTED;
				FitnessRuntime.LOGGER.info("Trainer resistance control: {}", this.support);
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
				this.logFailure("Trainer simulation command failed, trying again in {} s: {}", RETRY_AFTER_FAILURE_MILLIS / 1000,
					error == null ? "not accepted" : cause(error));
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
			? this.connection.write(Ftms.SERVICE, Ftms.CONTROL_POINT, TrainerSimulation.encode(target), true).thenApply(v -> {
				if (!this.announced) {
					this.announced = true;
					FitnessRuntime.LOGGER.info("Sent the trainer its first simulation parameters: grade {} %, rolling resistance {}",
						String.format(Locale.ROOT, "%.2f", target.gradePercent()), String.format(Locale.ROOT, "%.4f", target.crr()));
				}
				this.failureLogged = false;
				return true;
			})
			: CompletableFuture.completedFuture(false));
	}

	/**
	 * Writes a one-byte command and completes with whether it went through: the trainer answered with success, or it did
	 * not answer at all although the write was accepted (see {@link #unanswered}). A refusal or a failed write is false.
	 */
	private CompletableFuture<Boolean> command(int opCode) {
		CompletableFuture<Integer> answer = new CompletableFuture<>();
		this.pending.set(new Pending(opCode, answer));
		return this.connection.write(Ftms.SERVICE, Ftms.CONTROL_POINT, new byte[] { (byte) opCode }, true)
			.thenCompose(written -> this.unanswered ? CompletableFuture.completedFuture(RESULT_SUCCESS)
				: answer.completeOnTimeout(NO_ANSWER, RESPONSE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
			.handle((result, error) -> {
				if (error != null) {
					this.logFailure("Trainer control command 0x{} could not be written: {}", Integer.toHexString(opCode), cause(error));
					return false;
				}
				if (result == NO_ANSWER) {
					this.unanswered = true;
					FitnessRuntime.LOGGER.info("The trainer did not answer control command 0x{}; sending control commands without waiting for answers from now on",
						Integer.toHexString(opCode));
					return true;
				}
				if (result != RESULT_SUCCESS) {
					this.logFailure("Trainer refused control command 0x{} (result 0x{})", Integer.toHexString(opCode), Integer.toHexString(result));
					return false;
				}
				return true;
			});
	}

	/** The first failure since the last success is logged as information, repeats only at debug level. */
	private void logFailure(String format, Object... args) {
		if (this.failureLogged) {
			FitnessRuntime.LOGGER.debug(format, args);
		} else {
			this.failureLogged = true;
			FitnessRuntime.LOGGER.info(format, args);
		}
	}

	private static Throwable cause(Throwable error) {
		return error.getCause() != null ? error.getCause() : error;
	}

	private void onIndication(byte[] data) {
		if (data == null || data.length < 3 || (data[0] & 0xFF) != OP_RESPONSE) {
			return;
		}
		int requestOpCode = data[1] & 0xFF;
		int result = data[2] & 0xFF;
		if (result == RESULT_CONTROL_NOT_PERMITTED) {
			// Lost control (for example another app took over): ask again with the next command.
			this.controlling = false;
		}
		Pending waiting = this.pending.get();
		if (waiting != null && waiting.opCode() == requestOpCode) {
			waiting.answer().complete(result);
		}
	}
}
