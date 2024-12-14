package io.github.adamw7.tools.data;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.net.SocketImpl;
import java.net.SocketImplFactory;

public class NetworkSwitch {

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
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to set custom socket factory", e);
		}
	}
}
