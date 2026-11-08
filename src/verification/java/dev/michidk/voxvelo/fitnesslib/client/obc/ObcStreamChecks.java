package dev.michidk.voxvelo.fitnesslib.client.obc;

import java.util.ArrayList;
import java.util.List;

/** The OpenBikeControl TCP stream parser against reads that glue messages together or split them. */
public final class ObcStreamChecks {
	public static void main(String[] args) {
		// One three-byte message per read, the simple case.
		require(parse(new int[][] {{0x01, 0x01, 0x01}}).equals(List.of(pair(0x01, 0x01))), "a single shift press");
		// A shift and a steering angle sent together, as BikeControl does, must not shift the pair alignment.
		require(parse(new int[][] {{0x01, 0x01, 0x00, 0x01, 0x1B, 0x86}}).equals(List.of(pair(0x01, 0x00), pair(0x1B, 0x86))),
			"two glued messages are parsed as two messages");
		// Three glued messages: the old parser produced the custom action 0x86 here, which opened the settings.
		List<ObcMessages.ButtonState> three = parse(new int[][] {{0x01, 0x01, 0x01, 0x01, 0x1B, 0x86, 0x01, 0x1B, 0x87}});
		require(three.equals(List.of(pair(0x01, 0x01), pair(0x1B, 0x86), pair(0x1B, 0x87))), "three glued messages");
		require(three.stream().noneMatch(button -> button.id() >= 0x80), "no custom action from glued steering bytes");
		// A press and its release glued together: the release must survive, or the next press is ignored.
		require(parse(new int[][] {{0x01, 0x01, 0x01, 0x01, 0x01, 0x00}}).equals(List.of(pair(0x01, 0x01), pair(0x01, 0x00))),
			"press and release in one read");
		// A multi-pair message as in the specification stays one message.
		require(parse(new int[][] {{0x01, 0x01, 0x01, 0x02, 0x00}}).equals(List.of(pair(0x01, 0x01), pair(0x02, 0x00))),
			"a message with several pairs");
		// A device status message between button messages.
		ObcMessages.StreamParser parser = new ObcMessages.StreamParser();
		List<ObcMessages.ButtonState> seen = new ArrayList<>();
		feed(parser, seen, 0x01, 0x18, 0x01, 0x02, 0x55, 0x01, 0x01, 0x18, 0x00);
		require(seen.equals(List.of(pair(0x18, 0x01), pair(0x18, 0x00))), "status message between button messages");
		require(parser.sawStatus(), "status message is noticed");
		// The status message coalesced at the end of a button message, which the old parser already handled.
		require(parse(new int[][] {{0x01, 0x10, 0x01, 0x02, 0x64, 0x01}}).equals(List.of(pair(0x10, 0x01))), "status after a button message");
		// A message split across two reads is completed by the second read.
		parser = new ObcMessages.StreamParser();
		seen.clear();
		feed(parser, seen, 0x01, 0x1B);
		require(seen.isEmpty(), "half a message waits");
		feed(parser, seen, 0x94, 0x01, 0x1A, 0x01);
		require(seen.equals(List.of(pair(0x1B, 0x94), pair(0x1A, 0x01))), "the rest of the message completes it");
		feed(parser, seen, 0x02);
		require(!parser.sawStatus(), "a status message is not counted before it is complete");
		feed(parser, seen, 0xFF, 0x01);
		require(parser.sawStatus(), "a split status message completes");
		// Bytes that cannot be a message are skipped until the stream parses again.
		parser = new ObcMessages.StreamParser();
		seen.clear();
		feed(parser, seen, 0x07, 0x09, 0x01, 0x10, 0x01);
		require(seen.equals(List.of(pair(0x10, 0x01))), "resynchronises after unknown bytes");
		// Something that looks like the start of a message but never completes is dropped once it grows too long.
		parser = new ObcMessages.StreamParser();
		seen.clear();
		int[] endless = new int[300];
		endless[0] = 0x01;
		for (int i = 1; i < endless.length; i++) endless[i] = 0x1B;
		feed(parser, seen, endless);
		feed(parser, seen, 0x01, 0x10, 0x01);
		require(seen.contains(pair(0x10, 0x01)), "recovers from an overlong pending message");
		// A single-notification BLE parse is untouched.
		require(ObcMessages.parseButtonState(new byte[] {1, 0x11, 1, 0x14, 1}).equals(List.of(pair(0x11, 1), pair(0x14, 1))), "BLE parse");
		System.out.println("OBC stream checks passed");
	}

	private static List<ObcMessages.ButtonState> parse(int[][] reads) {
		ObcMessages.StreamParser parser = new ObcMessages.StreamParser();
		List<ObcMessages.ButtonState> seen = new ArrayList<>();
		for (int[] read : reads) feed(parser, seen, read);
		return seen;
	}

	private static void feed(ObcMessages.StreamParser parser, List<ObcMessages.ButtonState> seen, int... bytes) {
		byte[] buf = new byte[bytes.length + 8]; // the reader's buffer is larger than what it read
		for (int i = 0; i < bytes.length; i++) buf[i] = (byte) bytes[i];
		parser.feed(buf, bytes.length, seen::add);
	}

	private static ObcMessages.ButtonState pair(int id, int state) {
		return new ObcMessages.ButtonState(id, state);
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
