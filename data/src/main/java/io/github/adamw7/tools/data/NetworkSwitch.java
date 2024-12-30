package io.github.adamw7.tools.data;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.net.SocketImpl;
import java.net.SocketImplFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NetworkSwitch {
	
	private final static Logger log = LogManager.getLogger(NetworkSwitch.class.getName());

	private static volatile boolean isOff = false;
	
	private NetworkSwitch() {}
	
	private static class BlockExternalSocketFactory implements SocketImplFactory {
		@Override
		public SocketImpl createSocketImpl() {
			throw new UnsupportedOperationException("The network if off");
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
		try {
			Socket.setSocketImplFactory(new BlockExternalSocketFactory());
			isOff = true;
			log.info("Network is off now");
			return true;
		} catch (IOException e) {
			log.error(e);
			throw new UncheckedIOException("Failed to set custom socket factory", e);
		}
	}
}
