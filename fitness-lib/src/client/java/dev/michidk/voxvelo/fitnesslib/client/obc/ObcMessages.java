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
	 * Splits the TCP byte stream into messages. The protocol has no length prefix, so, like the reference
	 * implementation, each chunk handed to feed is treated as starting on a message boundary: a button state
	 * message runs to the end of the chunk. The one tolerated exception is a device status message
	 * (0x02, battery, connected) coalesced at the end of a button state chunk; it can be told apart because the
	 * remaining three bytes cannot be button pairs. Unknown message types end the chunk.
	 */
	public static final class StreamParser {
		private boolean sawStatus;

		/** Whether the device has sent a device status message, which devices are asked to repeat every 30 to 60 seconds. */
		public boolean sawStatus() {
			return this.sawStatus;
		}

		public void feed(byte[] buf, int length, Consumer<ButtonState> sink) {
			int pos = 0;
			while (pos < length) {
				int type = buf[pos] & 0xFF;
				if (type == Obc.MSG_BUTTON_STATE) {
					int end = length;
					if (length - 3 > pos && looksLikeStatus(buf, length - 3)) {
						end = length - 3;
					}
					for (int i = pos + 1; i + 1 < end; i += 2) {
						sink.accept(new ButtonState(buf[i] & 0xFF, buf[i + 1] & 0xFF));
					}
					pos = end;
				} else if (type == Obc.MSG_DEVICE_STATUS) {
					this.sawStatus = true;
					pos += 3;
				} else {
					return;
				}
			}
		}

		private static boolean looksLikeStatus(byte[] buf, int from) {
			if ((buf[from] & 0xFF) != Obc.MSG_DEVICE_STATUS) {
				return false;
			}
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
