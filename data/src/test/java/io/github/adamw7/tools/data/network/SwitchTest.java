package io.github.adamw7.tools.data.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import org.junit.jupiter.api.Test;

public class SwitchTest {

	@Test
	public void happyPath() {
		Switch.off();
		
		RuntimeException thrown = assertThrows(RuntimeException.class, this::connectToInternet, "Expected connectToInternet() method to throw, but it didn't");

		assertEquals("java.lang.UnsupportedOperationException: The network if off",
				thrown.getMessage());
	}
	
	@Test
	public void testMultipleOff() {
		Switch.off();
		assertFalse(Switch.off());
		assertFalse(Switch.off());
	}

	private void connectToInternet() throws Exception {
		URL url = new URI("https://www.google.com").toURL();
        
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
