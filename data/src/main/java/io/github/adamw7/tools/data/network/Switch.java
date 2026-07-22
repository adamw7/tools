package io.github.adamw7.tools.data.network;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketImpl;
import java.net.SocketImplFactory;
import java.net.URI;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A one-way, process-global network kill-switch. {@link #off()} seals the
 * network on two layers: a {@link ProxySelector} that rejects every selection
 * (stopping proxy-aware clients such as {@code HttpURLConnection} and
 * {@code HttpClient}) and a {@link SocketImplFactory} that refuses to create
 * any client {@link Socket} (stopping code that bypasses proxy selection by
 * dialling directly). NIO {@code SocketChannel}s obtained straight from a
 * {@code SelectorProvider} are the one remaining gap: the provider cannot be
 * replaced once the JVM has loaded it, so a library that opens raw channels is
 * not blocked by this switch.
 */
public class Switch {

	private static final Logger log = LogManager.getLogger(Switch.class.getName());

	private static volatile boolean isOff = false;

	private Switch() {}

	private static class BlockingProxySelector extends ProxySelector {
		@Override
		public List<Proxy> select(URI uri) {
			throw new UnsupportedOperationException("The network is off");
		}

		@Override
		public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
			// no-op
		}
	}

	private static class BlockingSocketImplFactory implements SocketImplFactory {
		@Override
		public SocketImpl createSocketImpl() {
			throw new UnsupportedOperationException("The network is off");
		}
	}

	/**
	 *
	 * @return true if this execution has turned off the network
	 */
	public static synchronized boolean off() {
		if (isOff) {
			log.warn("Network is already off. Nothing changed");
			return false;
		}
		ProxySelector.setDefault(new BlockingProxySelector());
		blockClientSockets();
		isOff = true;
		log.info("Network is off now");
		return true;
	}

	/**
	 * The factory mechanism is deprecated, but it is the only post-startup hook
	 * that covers every {@code new Socket(...)} in the JVM; the {@code isOff}
	 * guard ensures the one-shot {@code setSocketImplFactory} is called once.
	 */
	@SuppressWarnings({ "deprecation", "removal" })
	private static void blockClientSockets() {
		try {
			Socket.setSocketImplFactory(new BlockingSocketImplFactory());
		} catch (IOException e) {
			throw new UncheckedIOException("Could not install the socket-blocking factory", e);
		}
	}
}
