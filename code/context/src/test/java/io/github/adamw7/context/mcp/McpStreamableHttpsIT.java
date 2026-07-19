package io.github.adamw7.context.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Integration test that serves the context-engineering MCP server over HTTPS so
 * the {@link TlsConfiguration} TLS 1.3 pinning can be asserted end-to-end against
 * a live server: a real MCP tool call must succeed over the secure channel, the
 * negotiated protocol on the MCP endpoint must be {@code TLSv1.3}, and a client
 * that only offers {@code TLSv1.2} must be refused. A throwaway self-signed
 * keystore is generated before the Spring context starts and referenced through
 * the {@code mcp.test.keystore} system property.
 */
@SpringBootTest(
		classes = Main.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"transport.mode=streamable-http",
				"spring.main.banner-mode=off",
				"server.ssl.enabled=true",
				"server.ssl.key-store=file:${mcp.test.keystore}",
				"server.ssl.key-store-password=changeit",
				"server.ssl.key-store-type=PKCS12",
				"server.ssl.key-alias=mcp",
				"context.allowed-roots=${java.io.tmpdir}" })
public class McpStreamableHttpsIT {

	static {
		generateKeystore();
	}

	@LocalServerPort
	private int port;

	@TempDir
	Path projectRoot;

	private McpSyncClient client;

	@BeforeEach
	void setUp() throws IOException {
		Files.writeString(projectRoot.resolve("A.java"), "public class A {}");
		Files.writeString(projectRoot.resolve("B.java"), "public class B { A a; }");
		HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
				.builder("https://localhost:" + port)
				.customizeClient(builder -> builder.sslContext(testTrustContext()))
				.build();
		client = McpClient.sync(transport)
				.clientInfo(McpSchema.Implementation.builder("integration-test-https-client", "1.0").build())
				.build();
		client.initialize();
	}

	@AfterEach
	void tearDown() {
		client.close();
	}

	@Test
	void negotiatesTls13OnTheMcpEndpoint() throws IOException {
		SSLSocketFactory factory = testTrustContext().getSocketFactory();
		try (SSLSocket socket = (SSLSocket) factory.createSocket("localhost", port)) {
			socket.startHandshake();
			assertEquals(TlsConfiguration.TLS_1_3, socket.getSession().getProtocol());
		}
	}

	@Test
	void prefersHybridKeyExchangeGroup() {
		assertEquals(TlsConfiguration.PREFERRED_NAMED_GROUPS,
				System.getProperty(TlsConfiguration.NAMED_GROUPS_PROPERTY));
	}

	@Test
	void refusesTls12Handshakes() throws IOException {
		SSLSocketFactory factory = testTrustContext().getSocketFactory();
		try (SSLSocket socket = (SSLSocket) factory.createSocket("localhost", port)) {
			socket.setEnabledProtocols(new String[] { "TLSv1.2" });
			assertThrows(IOException.class, socket::startHandshake);
		}
	}

	@Test
	void findContextToolWorksOverTls13() {
		McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder("find_context")
				.arguments(Map.of("path", projectRoot.toString(), "class_name", "B"))
				.build();

		McpSchema.CallToolResult result = client.callTool(request);

		assertFalse(result.isError());
		String dependencies = ((McpSchema.TextContent) result.content().getFirst()).text();
		assertEquals("A.java", parse(dependencies).get(0).asText());
	}

	private JsonNode parse(String json) {
		try {
			return new ObjectMapper().readTree(json);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Invalid JSON: " + json, e);
		}
	}

	private static void generateKeystore() {
		try {
			Path keystore = Files.createTempFile("mcp-test-keystore-", ".p12");
			Files.delete(keystore);
			runKeytool(keystore);
			keystore.toFile().deleteOnExit();
			System.setProperty("mcp.test.keystore", keystore.toString());
		} catch (IOException e) {
			throw new IllegalStateException("Could not create the HTTPS test keystore", e);
		}
	}

	private static void runKeytool(Path keystore) throws IOException {
		String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
		Process process = new ProcessBuilder(keytool, "-genkeypair", "-alias", "mcp", "-keyalg", "RSA",
				"-keysize", "2048", "-storetype", "PKCS12", "-keystore", keystore.toString(),
				"-storepass", "changeit", "-keypass", "changeit", "-dname", "CN=localhost",
				"-validity", "1", "-ext", "SAN=dns:localhost,ip:127.0.0.1")
				.redirectErrorStream(true)
				.start();
		awaitSuccess(process);
	}

	private static void awaitSuccess(Process process) throws IOException {
		try {
			int exitCode = process.waitFor();
			if (exitCode != 0) {
				throw new IllegalStateException("keytool exited with code " + exitCode);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while generating the HTTPS test keystore", e);
		}
	}

	private static SSLContext testTrustContext() {
		try {
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, trustManagersForTestKeystore(), null);
			return context;
		} catch (GeneralSecurityException | IOException e) {
			throw new IllegalStateException("Could not build the test SSL context", e);
		}
	}

	private static TrustManager[] trustManagersForTestKeystore() throws GeneralSecurityException, IOException {
		KeyStore keyStore = KeyStore.getInstance("PKCS12");
		Path keystore = Path.of(System.getProperty("mcp.test.keystore"));
		try (InputStream in = Files.newInputStream(keystore)) {
			keyStore.load(in, "changeit".toCharArray());
		}
		TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		factory.init(keyStore);
		return factory.getTrustManagers();
	}
}
