package dev.michidk.voxvelo.fitnesslib.client.ftms;

import dev.michidk.voxvelo.fitnesslib.client.FitnessRuntime;
import dev.michidk.voxvelo.fitnesslib.client.bluetooth.BluetoothConnection;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

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
	private volatile boolean indicationsReady;
	private volatile String status = "Initialising";
	private volatile String response = "No response yet";
	private volatile String lastError = "";
	private volatile String packet = "";
	private volatile TrainerSimulation.Target requested;
	private volatile TrainerSimulation.Target written;
	private volatile TrainerSimulation.Target acknowledged;
	private volatile long writtenAt;
	private volatile long acknowledgedAt;
	private volatile int writes;
	private volatile int acknowledgements;

	/** Transport writes and FTMS acknowledgements are deliberately separate. */
	public record Diagnostics(String status, String response, String error, String packet,
		TrainerSimulation.@Nullable Target requested, TrainerSimulation.@Nullable Target written,
		TrainerSimulation.@Nullable Target acknowledged, long writtenAt, long acknowledgedAt,
		int writes, int acknowledgements, boolean controlling, boolean indicationsReady, long retryAt) {}

	public Diagnostics diagnostics() {
		return new Diagnostics(this.status, this.response, this.lastError, this.packet, this.requested, this.written,
			this.acknowledged, this.writtenAt, this.acknowledgedAt, this.writes, this.acknowledgements,
			this.controlling, this.indicationsReady, this.retryNotBefore);
	}
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
	 * steps run one after the other (platform Bluetooth stacks dislike overlapping GATT operations). Without the
	 * indication subscription no control command is sent: a successful GATT write alone does not mean the trainer
	 * accepted anything.
	 */
	public void initialise() {
		// The Control Point is write plus indicate by definition, so ask for indications outright.
		this.connection.indicate(Ftms.SERVICE, Ftms.CONTROL_POINT, this::onIndication)
			.handle((ignored, error) -> {
				if (error != null) {
					this.indicationsReady = false;
					this.status = "Indication subscription failed";
					this.lastError = cause(error).toString();
					FitnessRuntime.LOGGER.info("Could not subscribe to trainer control answers: {}", cause(error));
				} else {
					this.indicationsReady = true;
				}
				return null;
			})
			.thenCompose(ignored -> this.connection.read(Ftms.SERVICE, Ftms.FEATURE))
			.whenComplete((data, error) -> {
				if (error != null) {
					FitnessRuntime.LOGGER.info("Trainer control point not available: {}", cause(error));
					this.support = Support.UNSUPPORTED;
					this.status = "Feature read failed";
					this.lastError = cause(error).toString();
					return;
				}
				if (data == null || data.length < TARGET_SETTING_FEATURES_OFFSET + Integer.BYTES) {
					this.status = "Invalid feature response";
					this.support = Support.UNSUPPORTED;
					return;
				}
				long targets = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt(TARGET_SETTING_FEATURES_OFFSET) & 0xFFFFFFFFL;
				this.support = (targets >> FEATURE_SIMULATION_BIT & 1L) != 0 ? Support.SUPPORTED : Support.UNSUPPORTED;
				if (this.indicationsReady) this.status = this.support == Support.SUPPORTED ? "Ready (SIM supported)" : "SIM unsupported";
				FitnessRuntime.LOGGER.info("Trainer resistance control: {}", this.support);
			});
	}

	/** Queues a target when ready; false means unsupported, initialising or waiting to retry. */
	public boolean apply(TrainerSimulation.Target target) {
		this.requested = target;
		if (!this.indicationsReady || this.support != Support.SUPPORTED || System.currentTimeMillis() < this.retryNotBefore) {
			return false;
		}
		this.queued.set(target);
		this.pump();
		return true;
	}

	/** Puts the trainer back to a flat road. Call when the player stops riding; false means it was not queued. */
	public boolean neutral() {
		return this.apply(TrainerSimulation.Target.NEUTRAL);
	}

	private void pump() {
		if (System.currentTimeMillis() < this.retryNotBefore || !this.busy.compareAndSet(false, true)) {
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
				this.queued.set(null);
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
			? this.command(OP_SET_SIMULATION, TrainerSimulation.encode(target)).thenApply(accepted -> {
				if (!accepted) return false;
				this.acknowledged = this.written;
				this.acknowledgedAt = System.currentTimeMillis();
				this.acknowledgements++;
				this.status = "SIM acknowledged";
				this.lastError = "";
				if (!this.announced) {
					this.announced = true;
					FitnessRuntime.LOGGER.info("Trainer acknowledged its first simulation parameters: grade {} %, rolling resistance {}",
						String.format(Locale.ROOT, "%.2f", target.gradePercent()), String.format(Locale.ROOT, "%.4f", target.crr()));
				}
				this.failureLogged = false;
				return true;
			})
			: CompletableFuture.completedFuture(false));
	}

	/** Waits for the matching FTMS response; transport acceptance alone is not success. */
	private CompletableFuture<Boolean> command(int opCode) {
		return this.command(opCode, new byte[] {(byte) opCode});
	}

	private CompletableFuture<Boolean> command(int opCode, byte[] bytes) {
		CompletableFuture<Integer> answer = new CompletableFuture<>();
		Pending request = new Pending(opCode, answer);
		this.pending.set(request);
		this.packet = HexFormat.ofDelimiter(" ").withUpperCase().formatHex(bytes);
		this.status = String.format(Locale.ROOT, "Writing 0x%02X", opCode);
		return this.connection.write(Ftms.SERVICE, Ftms.CONTROL_POINT, bytes, true)
			.thenCompose(ignored -> {
				this.status = String.format(Locale.ROOT, "Waiting for 0x%02X response", opCode);
				if (opCode == OP_SET_SIMULATION) {
					this.written = new TrainerSimulation.Target(
						(short) ((bytes[3] & 0xFF) | (bytes[4] & 0xFF) << 8) / 100.0,
						(bytes[5] & 0xFF) * TrainerLimits.CRR_RESOLUTION);
					this.writtenAt = System.currentTimeMillis();
					this.writes++;
				}
				return answer.completeOnTimeout(NO_ANSWER, RESPONSE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
			})
			.handle((result, error) -> {
				this.pending.compareAndSet(request, null);
				if (error != null) {
					this.status = "Bluetooth write failed";
					this.lastError = cause(error).toString();
				} else if (result == NO_ANSWER) {
					this.status = "FTMS response timed out";
					this.lastError = String.format(Locale.ROOT, "No acknowledgement for 0x%02X; acceptance unknown", opCode);
				} else if (result != RESULT_SUCCESS) {
					this.status = "Trainer refused command";
					this.lastError = String.format(Locale.ROOT, "0x%02X: %s (0x%02X)", opCode, resultName(result), result);
				} else {
					return true;
				}
				this.logFailure("Trainer control failed: {}", this.lastError);
				return false;
			});
	}

	private static String resultName(int result) {
		return switch (result) {
			case 1 -> "Success";
			case 2 -> "Opcode unsupported";
			case 3 -> "Invalid parameter";
			case 4 -> "Operation failed";
			case 5 -> "Control not permitted";
			default -> "Unknown result";
		};
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
		this.response = String.format(Locale.ROOT, "80 %02X %02X: %s", requestOpCode, result, resultName(result));
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
