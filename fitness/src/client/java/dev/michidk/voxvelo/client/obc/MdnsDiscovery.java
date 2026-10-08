package dev.michidk.voxvelo.client.obc;

import dev.michidk.voxelfitness.client.FitnessRuntime;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

/**
 * Finds OpenBikeControl devices on the local network (service type _openbikecontrol._tcp.local.).
 * Starting and stopping happen on a background thread because JmDNS blocks while binding sockets.
 */
public final class MdnsDiscovery {
	private static final long SHUTDOWN_WAIT_MILLIS = 2_000;

	private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "voxvelo-mdns");
		thread.setDaemon(true);
		return thread;
	});
	private final Map<String, ObcDevice> devices = new ConcurrentHashMap<>();
	/** One browser per usable network interface: mDNS only travels on the local link, so the default address may be the wrong one. */
	private final List<JmDNS> browsers = new CopyOnWriteArrayList<>();
	private volatile boolean wanted;
	private volatile boolean failed;

	private final ServiceListener listener = new ServiceListener() {
		@Override
		public void serviceAdded(ServiceEvent event) {
			event.getDNS().requestServiceInfo(event.getType(), event.getName(), true);
		}

		@Override
		public void serviceRemoved(ServiceEvent event) {
			MdnsDiscovery.this.devices.remove(event.getName());
		}

		@Override
		public void serviceResolved(ServiceEvent event) {
			MdnsDiscovery.this.onResolved(event.getName(), event.getInfo());
		}
	};

	/** Starts browsing if it is not already running. Safe to call repeatedly. */
	public void start() {
		if (this.wanted) {
			return;
		}
		this.wanted = true;
		this.executor.execute(() -> {
			if (!this.wanted || !this.browsers.isEmpty()) {
				return;
			}
			try {
				for (InetAddress address : localAddresses()) {
					try {
						JmDNS created = JmDNS.create(address);
						created.addServiceListener(Obc.MDNS_TYPE, this.listener);
						this.browsers.add(created);
						FitnessRuntime.LOGGER.debug("Browsing for OpenBikeControl devices on {}", address.getHostAddress());
					} catch (Throwable t) {
						FitnessRuntime.LOGGER.warn("Could not browse on {}", address.getHostAddress(), t);
					}
				}
				if (this.browsers.isEmpty()) {
					JmDNS created = JmDNS.create();
					created.addServiceListener(Obc.MDNS_TYPE, this.listener);
					this.browsers.add(created);
				}
				FitnessRuntime.LOGGER.info("Browsing for OpenBikeControl devices on {} network interface(s)", this.browsers.size());
				this.failed = false;
			} catch (Throwable t) {
				FitnessRuntime.LOGGER.warn("Could not start network discovery", t);
				this.failed = true;
				this.wanted = false;
			}
		});
	}

	public void stop() {
		if (!this.wanted) {
			return;
		}
		this.wanted = false;
		this.executor.execute(() -> {
			this.devices.clear();
			for (JmDNS browser : this.browsers) {
				try {
					browser.close();
				} catch (Throwable t) {
					FitnessRuntime.LOGGER.debug("Error closing mDNS", t);
				}
			}
			this.browsers.clear();
		});
	}

	/** Whether discovery could not start; the reason is in the log. */
	public boolean failed() {
		return this.failed;
	}

	public List<ObcDevice> devices() {
		return new ArrayList<>(this.devices.values());
	}

	/** IPv4 addresses of the interfaces that are up and can multicast, without loopback, link-local and VPN-style (100.64.0.0/10) ones. */
	static List<InetAddress> localAddresses() {
		List<InetAddress> found = new ArrayList<>();
		try {
			for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
				if (!network.isUp() || network.isLoopback() || !network.supportsMulticast()) {
					continue;
				}
				for (InetAddress address : Collections.list(network.getInetAddresses())) {
					if (address instanceof Inet4Address && !address.isLoopbackAddress() && !address.isLinkLocalAddress() && !isSharedAddressSpace(address)) {
						found.add(address);
					}
				}
			}
		} catch (SocketException e) {
			FitnessRuntime.LOGGER.debug("Could not list network interfaces", e);
		}
		return found;
	}

	private static boolean isSharedAddressSpace(InetAddress address) {
		byte[] octets = address.getAddress();
		return (octets[0] & 0xFF) == 100 && (octets[1] & 0xC0) == 64;
	}

	private void onResolved(String serviceName, ServiceInfo info) {
		if (info == null) {
			return;
		}
		String host = null;
		for (Inet4Address address : info.getInet4Addresses()) {
			host = address.getHostAddress();
			break;
		}
		if (host == null) {
			InetAddress[] any = info.getInetAddresses();
			if (any.length > 0) {
				host = any[0].getHostAddress();
			}
		}
		if (host == null || info.getPort() <= 0) {
			return;
		}
		String id = info.getPropertyString("id");
		String name = info.getPropertyString("name");
		this.devices.put(serviceName, ObcDevice.network(
			id == null || id.isBlank() ? serviceName : id,
			name == null || name.isBlank() ? serviceName : name,
			host,
			info.getPort()
		));
	}

	/** Closes the browsers, waiting briefly so their goodbye packets go out before the game exits. */
	public void shutdown() {
		this.stop();
		this.executor.shutdown();
		try {
			if (!this.executor.awaitTermination(SHUTDOWN_WAIT_MILLIS, TimeUnit.MILLISECONDS)) {
				FitnessRuntime.LOGGER.debug("Network discovery did not stop in time");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
