package io.github.adamw7.tools.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import org.junit.jupiter.api.Test;

public class NetworkSwitchTest {

	@Test
	public void happyPath() {
		NetworkSwitch.off();
		
		RuntimeException thrown = assertThrows(RuntimeException.class, this::connectToInternet, "Expected connectToInternet() method to throw, but it didn't");

		assertEquals("java.lang.UnsupportedOperationException: The network if off",
				thrown.getMessage());
	}

	private void connectToInternet() throws Exception {
		URL url = new URI("http://www.google.com").toURL();
        
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        connection.setRequestMethod("GET");
        
        int responseCode = connection.getResponseCode();
        
        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            
            in.close();
            connection.disconnect();
        }
	}
}
