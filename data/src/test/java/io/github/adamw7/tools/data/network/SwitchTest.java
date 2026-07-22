package io.github.adamw7.tools.data.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.net.URL;

import org.junit.jupiter.api.Test;

public class SwitchTest {

	@Test
	public void happyPath() {
		Switch.off();

		RuntimeException thrown = assertThrows(RuntimeException.class, this::connectToInternet, "Expected connectToInternet() method to throw, but it didn't");

		assertEquals("java.lang.UnsupportedOperationException: The network is off", thrown.getMessage());
	}

	@Test
	public void directSocketConnectionsAreBlocked() {
		Switch.off();

		// A plain client socket never consults the ProxySelector, so the switch's
		// SocketImplFactory must refuse it. The literal TEST-NET-1 address needs no
		// DNS lookup and would never be routable even if the block failed.
		UnsupportedOperationException thrown = assertThrows(UnsupportedOperationException.class,
				() -> new Socket("192.0.2.1", 80), "Expected the socket factory to block the connection");

		assertEquals("The network is off", thrown.getMessage());
	}

	@Test
	public void testMultipleOff() {
		Switch.off();
		assertFalse(Switch.off());
		assertFalse(Switch.off());
	}

	@Test
	public void switchIsAStatelessUtilityWithAPrivateConstructor() throws Exception {
		Constructor<Switch> constructor = Switch.class.getDeclaredConstructor();

		assertTrue(Modifier.isPrivate(constructor.getModifiers()),
				"Switch exposes only static behaviour, so it must not be instantiable");

		constructor.setAccessible(true);
		constructor.newInstance(); // exercise the guarded constructor for coverage
	}

	private void connectToInternet() throws Exception {
		// RFC 5737 TEST-NET-1: a reserved, non-routable address. The Switch is
		// expected to block the connection before it is ever attempted, so this
		// test must never reach a real external server even if the block fails.
		URL url = new URI("http://192.0.2.1").toURL();
        
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        connection.setRequestMethod("GET");
        
        int responseCode = connection.getResponseCode();
        
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                in.readLine();
                in.close();
            }
            finally {
                connection.disconnect();
            }
        }
	}
}
