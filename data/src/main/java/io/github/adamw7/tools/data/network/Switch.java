package io.github.adamw7.tools.data.network;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Switch {

	private final static Logger log = LogManager.getLogger(Switch.class.getName());

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
		isOff = true;
		log.info("Network is off now");
		return true;
	}
}
