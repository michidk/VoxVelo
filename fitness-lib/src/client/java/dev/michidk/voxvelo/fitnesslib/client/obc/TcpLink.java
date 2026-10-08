package dev.michidk.voxvelo.fitnesslib.client.obc;

import dev.michidk.voxvelo.fitnesslib.client.FitnessRuntime;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * The OpenBikeControl network transport: a plain TCP connection to the device, read on its own thread.
 * Everything blocking (connect, read) happens there; the game thread never touches the socket.
 *
 * <p>A device that vanishes without closing the connection (a phone that drops off Wi-Fi) would leave the read blocked
 * for good, with a brake or steer still held. The protocol has no keepalive, but asks devices to send a device status
 * message every 30 to 60 seconds; a device that has sent one and then stays silent far longer is taken as gone.
 * Devices that never send status are left to TCP keepalive, since button messages only come when something changes.
 */
final class TcpLink {
	private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
	/** How often a waiting read wakes up to check how long the device has been silent. */
	private static final int READ_POLL_MILLIS = 5_000;
	/** Two and a half of the longest status intervals the protocol suggests. */
	private static final long STATUS_SILENCE_MILLIS = 150_000;

	private final Socket socket = new Socket();
	private volatile boolean closed;

	/**
	 * @param onButton    called for every button state the device sends, on the link thread
	 * @param onConnected called once after the socket is open and the App Information message was sent
	 * @param onClosed    called once when the link ends: with the failure, or with null if the device closed it or
	 *                    {@link #close()} was called
	 */
	static TcpLink open(String host, int port, byte[] appInfo, Consumer<ObcMessages.ButtonState> onButton, Runnable onConnected,
		Consumer<@Nullable IOException> onClosed) {
		TcpLink link = new TcpLink();
		Thread thread = new Thread(() -> link.run(host, port, appInfo, onButton, onConnected, onClosed), "voxvelo-obc-tcp");
		thread.setDaemon(true);
		thread.start();
		return link;
	}

	private void run(String host, int port, byte[] appInfo, Consumer<ObcMessages.ButtonState> onButton, Runnable onConnected,
		Consumer<@Nullable IOException> onClosed) {
		IOException failure = null;
		try {
			this.socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
			this.socket.setKeepAlive(true);
			this.socket.setTcpNoDelay(true);
			this.socket.setSoTimeout(READ_POLL_MILLIS);
			OutputStream out = this.socket.getOutputStream();
			out.write(appInfo);
			out.flush();
			onConnected.run();

			ObcMessages.StreamParser parser = new ObcMessages.StreamParser();
			InputStream in = this.socket.getInputStream();
			byte[] buffer = new byte[256];
			long lastHeard = System.currentTimeMillis();
			while (!this.closed) {
				int read;
				try {
					read = in.read(buffer);
				} catch (SocketTimeoutException e) {
					if (parser.sawStatus() && System.currentTimeMillis() - lastHeard > STATUS_SILENCE_MILLIS) {
						throw new SocketTimeoutException("No device status for " + STATUS_SILENCE_MILLIS / 1000 + " s");
					}
					continue;
				}
				if (read < 0) {
					break;
				}
				lastHeard = System.currentTimeMillis();
				parser.feed(buffer, read, onButton);
			}
		} catch (IOException e) {
			if (!this.closed) {
				failure = e;
				FitnessRuntime.LOGGER.debug("OpenBikeControl TCP link ended", e);
			}
		} finally {
			this.close();
			onClosed.accept(failure);
		}
	}

	void close() {
		this.closed = true;
		try {
			this.socket.close();
		} catch (IOException ignored) {
			// already closed
		}
	}
}
