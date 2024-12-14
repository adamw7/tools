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

	private NetworkSwitch() {}
	
	private static class BlockExternalSocketFactory implements SocketImplFactory {
		@Override
		public SocketImpl createSocketImpl() {
			throw new UnsupportedOperationException("The network if off");
		}
	}

	public static void off() {
		try {
			Socket.setSocketImplFactory(new BlockExternalSocketFactory());
			log.info("Network is off now");
		} catch (IOException e) {
			log.error(e);
			throw new UncheckedIOException("Failed to set custom socket factory", e);
		}
	}
}
