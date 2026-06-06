package io.github.adamw7.tools.data.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class SwitchTest {

	@AfterEach
	public void restoreNetwork() {
		Switch.on();
	}

	@Test
	public void happyPath() {
		Switch.off();

		RuntimeException thrown = assertThrows(RuntimeException.class, this::connectToInternet, "Expected connectToInternet() method to throw, but it didn't");

		assertEquals("java.lang.UnsupportedOperationException: The network is off", thrown.getMessage());
	}

	@Test
	public void testMultipleOff() {
		Switch.off();
		assertFalse(Switch.off());
		assertFalse(Switch.off());
	}

	@Test
	public void testOnWhenAlreadyOn() {
		assertFalse(Switch.on());
	}

	@Test
	public void testOnRestoresNetwork() {
		assertTrue(Switch.off());
		assertTrue(Switch.on());
		assertFalse(Switch.on());
	}

	@Test
	public void testOffOnOffCycle() {
		assertTrue(Switch.off());
		assertTrue(Switch.on());
		assertTrue(Switch.off());

		RuntimeException thrown = assertThrows(RuntimeException.class, this::connectToInternet, "Expected connectToInternet() method to throw, but it didn't");
		assertEquals("java.lang.UnsupportedOperationException: The network is off", thrown.getMessage());
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
