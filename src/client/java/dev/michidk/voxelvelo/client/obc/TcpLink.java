package dev.michidk.voxelvelo.client.obc;

import dev.michidk.voxelvelo.VoxelVelo;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.function.Consumer;

/**
 * The OpenBikeControl network transport: a plain TCP connection to the device, read on its own thread.
 * Everything blocking (connect, read) happens there; the game thread never touches the socket.
 */
final class TcpLink {
	private static final int CONNECT_TIMEOUT_MILLIS = 5_000;

	private final Socket socket = new Socket();
	private volatile boolean closed;

	/**
	 * @param onConnected called once after the socket is open and the App Information message was sent
	 * @param onClosed    called once when the link ends, with a short reason (never null)
	 */
	static TcpLink open(String host, int port, byte[] appInfo, ObcControls controls, Runnable onConnected, Consumer<String> onClosed) {
		TcpLink link = new TcpLink();
		Thread thread = new Thread(() -> link.run(host, port, appInfo, controls, onConnected, onClosed), "voxelvelo-obc-tcp");
		thread.setDaemon(true);
		thread.start();
		return link;
	}

	private void run(String host, int port, byte[] appInfo, ObcControls controls, Runnable onConnected, Consumer<String> onClosed) {
		String reason = "Connection closed";
		try {
			this.socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
			this.socket.setKeepAlive(true);
			this.socket.setTcpNoDelay(true);
			OutputStream out = this.socket.getOutputStream();
			out.write(appInfo);
			out.flush();
			onConnected.run();

			ObcMessages.StreamParser parser = new ObcMessages.StreamParser();
			InputStream in = this.socket.getInputStream();
			byte[] buffer = new byte[256];
			int read;
			while (!this.closed && (read = in.read(buffer)) >= 0) {
				parser.feed(buffer, read, button -> controls.apply(button.id(), button.state()));
			}
		} catch (IOException e) {
			if (!this.closed) {
				reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
				VoxelVelo.LOGGER.debug("OpenBikeControl TCP link ended", e);
			}
		} finally {
			this.close();
			onClosed.accept(reason);
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
