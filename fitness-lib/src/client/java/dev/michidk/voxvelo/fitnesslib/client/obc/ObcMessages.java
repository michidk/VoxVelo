package dev.michidk.voxvelo.fitnesslib.client.obc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/** Encoding and decoding of OpenBikeControl messages. Pure functions, no I/O. */
public final class ObcMessages {
	/** Version of the App Information message layout written by {@link #encodeAppInfo}. */
	private static final int APP_INFO_VERSION = 0x01;
	/** Longest app id and version string the protocol allows, in UTF-8 bytes; longer ones are cut. */
	private static final int APP_INFO_MAX_STRING_BYTES = 32;

	public record ButtonState(int id, int state) {}

	private ObcMessages() {
	}

	/**
	 * Parses one complete button state message, as delivered by a single BLE notification:
	 * 0x01 followed by (id, state) pairs. Anything else, and an odd trailing byte, is ignored.
	 */
	public static List<ButtonState> parseButtonState(byte[] data) {
		List<ButtonState> buttons = new ArrayList<>();
		if (data == null || data.length < 3 || (data[0] & 0xFF) != Obc.MSG_BUTTON_STATE) {
			return buttons;
		}
		for (int i = 1; i + 1 < data.length; i += 2) {
			buttons.add(new ButtonState(data[i] & 0xFF, data[i + 1] & 0xFF));
		}
		return buttons;
	}

	/**
	 * Splits the TCP byte stream into messages. The protocol has no length prefix, and a device's messages often
	 * arrive glued together in one read (or split across two), so the bytes are segmented into whole messages
	 * using the protocol's own constraints: a button state message is 0x01 followed by (id, state) pairs, a
	 * device status message is 0x02, a battery level (0..100 or 0xFF) and a connected flag (0 or 1), and the
	 * shift buttons (ids 0x01 and 0x02, the same values as those message types) are digital and so never carry a
	 * state above 1. Of the segmentations that use every byte, the one with the fewest messages wins. Bytes at
	 * the end that do not yet complete a message wait for the next read. Bytes that can never become a message
	 * are dropped one at a time until the stream parses again.
	 *
	 * <p>Without this, a steering angle sent right after a shift (bytes 01 01 00 01 1B 86) read as the pairs
	 * (01, 00), (01, 1B) and, with a third message in the same read, (86, ..): a custom action id.
	 */
	public static final class StreamParser {
		/** The most bytes kept waiting for a message's remainder; a device never sends messages this long. */
		private static final int MAX_PENDING = 256;

		private byte[] pending = new byte[0];
		private boolean sawStatus;

		/** Whether the device has sent a device status message, which devices are asked to repeat every 30 to 60 seconds. */
		public boolean sawStatus() {
			return this.sawStatus;
		}

		public void feed(byte[] buf, int length, Consumer<ButtonState> sink) {
			byte[] data = Arrays.copyOf(this.pending, this.pending.length + length);
			System.arraycopy(buf, 0, data, this.pending.length, length);
			for (int start = 0; start < data.length; start++) {
				int end = segmentEnd(data, start);
				if (end >= 0) {
					this.emit(data, start, end, sink);
					this.pending = Arrays.copyOfRange(data, end, data.length);
					return;
				}
			}
			this.pending = new byte[0];
		}

		/**
		 * The furthest position up to which {@code data} from {@code start} splits into whole messages while the
		 * bytes after it are the beginning of a message still to come, or -1 when no such position exists.
		 */
		private static int segmentEnd(byte[] data, int start) {
			boolean[] reach = new boolean[data.length + 1];
			reach[start] = true;
			for (int p = start; p < data.length; p++) {
				if (!reach[p]) continue;
				int type = data[p] & 0xFF;
				if (type == Obc.MSG_DEVICE_STATUS) {
					if (p + 3 <= data.length && looksLikeStatus(data, p)) reach[p + 3] = true;
				} else if (type == Obc.MSG_BUTTON_STATE) {
					for (int q = p + 1; q + 1 < data.length && pairValid(data[q] & 0xFF, data[q + 1] & 0xFF); q += 2) {
						reach[q + 2] = true;
					}
				}
			}
			for (int end = data.length; end >= start; end--) {
				if (reach[end] && incompleteMessage(data, end)) return end;
			}
			return -1;
		}

		/** Whether the bytes from {@code at} are empty or the beginning of a message whose rest has not arrived. */
		private static boolean incompleteMessage(byte[] data, int at) {
			int remaining = data.length - at;
			if (remaining == 0) return true;
			if (remaining > MAX_PENDING) return false;
			int type = data[at] & 0xFF;
			if (type == Obc.MSG_DEVICE_STATUS) {
				int battery = remaining > 1 ? data[at + 1] & 0xFF : 0;
				return remaining < 3 && (battery <= 100 || battery == 0xFF);
			}
			if (type != Obc.MSG_BUTTON_STATE) return false;
			for (int q = at + 1; q + 1 < data.length; q += 2) {
				if (!pairValid(data[q] & 0xFF, data[q + 1] & 0xFF)) return false;
			}
			return true;
		}

		/** Emits the messages in {@code data[from, end)}, choosing the segmentation with the fewest messages. */
		private void emit(byte[] data, int from, int end, Consumer<ButtonState> sink) {
			int[] count = new int[end + 1]; // fewest messages from each position to the end
			int[] next = new int[end + 1];
			Arrays.fill(count, Integer.MAX_VALUE);
			count[end] = 0;
			for (int p = end - 1; p >= from; p--) {
				int type = data[p] & 0xFF;
				if (type == Obc.MSG_DEVICE_STATUS) {
					if (p + 3 <= end && looksLikeStatus(data, p) && count[p + 3] != Integer.MAX_VALUE) {
						count[p] = count[p + 3] + 1;
						next[p] = p + 3;
					}
				} else if (type == Obc.MSG_BUTTON_STATE) {
					for (int q = p + 1; q + 1 < end && pairValid(data[q] & 0xFF, data[q + 1] & 0xFF); q += 2) {
						if (count[q + 2] != Integer.MAX_VALUE && count[q + 2] + 1 <= count[p]) {
							count[p] = count[q + 2] + 1;
							next[p] = q + 2;
						}
					}
				}
			}
			for (int p = from; p < end; p = next[p]) {
				if ((data[p] & 0xFF) == Obc.MSG_DEVICE_STATUS) {
					this.sawStatus = true;
					continue;
				}
				for (int i = p + 1; i + 1 < next[p]; i += 2) {
					sink.accept(new ButtonState(data[i] & 0xFF, data[i + 1] & 0xFF));
				}
			}
		}

		/** The shift buttons share their ids with the message types and are digital, which tells the two apart. */
		private static boolean pairValid(int id, int state) {
			return (id != Obc.BUTTON_SHIFT_UP && id != Obc.BUTTON_SHIFT_DOWN) || state <= 1;
		}

		private static boolean looksLikeStatus(byte[] buf, int from) {
			int battery = buf[from + 1] & 0xFF;
			int connected = buf[from + 2] & 0xFF;
			return (battery <= 100 || battery == 0xFF) && connected <= 1;
		}
	}

	/** App Information message (type 0x04) telling the device which actions this app consumes. */
	public static byte[] encodeAppInfo(String appId, String appVersion, int[] buttonIds) {
		byte[] id = truncate(appId.getBytes(StandardCharsets.UTF_8));
		byte[] version = truncate(appVersion.getBytes(StandardCharsets.UTF_8));
		byte[] out = new byte[3 + id.length + 1 + version.length + 1 + buttonIds.length];
		int p = 0;
		out[p++] = (byte) Obc.MSG_APP_INFO;
		out[p++] = APP_INFO_VERSION;
		out[p++] = (byte) id.length;
		System.arraycopy(id, 0, out, p, id.length);
		p += id.length;
		out[p++] = (byte) version.length;
		System.arraycopy(version, 0, out, p, version.length);
		p += version.length;
		out[p++] = (byte) buttonIds.length;
		for (int button : buttonIds) {
			out[p++] = (byte) button;
		}
		return out;
	}

	private static byte[] truncate(byte[] bytes) {
		return bytes.length <= APP_INFO_MAX_STRING_BYTES ? bytes : Arrays.copyOf(bytes, APP_INFO_MAX_STRING_BYTES);
	}
}
