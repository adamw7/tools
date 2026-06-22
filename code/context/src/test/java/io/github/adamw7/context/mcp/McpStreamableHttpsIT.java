package io.github.adamw7.context.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.json.JSONArray;
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
				.customizeClient(builder -> builder.sslContext(trustAllContext()))
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
		SSLSocketFactory factory = trustAllContext().getSocketFactory();
		try (SSLSocket socket = (SSLSocket) factory.createSocket("localhost", port)) {
			socket.startHandshake();
			assertEquals(TlsConfiguration.TLS_1_3, socket.getSession().getProtocol());
		}
	}

	@Test
	void refusesTls12Handshakes() throws IOException {
		SSLSocketFactory factory = trustAllContext().getSocketFactory();
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
		assertEquals("A.java", new JSONArray(dependencies).getString(0));
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

	private static SSLContext trustAllContext() {
		try {
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, new TrustManager[] { trustEverything() }, new SecureRandom());
			return context;
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Could not build the trust-all SSL context", e);
		}
	}

	private static X509TrustManager trustEverything() {
		return new X509TrustManager() {

			@Override
			public void checkClientTrusted(X509Certificate[] chain, String authType) {
				// Self-signed test certificate: client identity is not verified.
			}

			@Override
			public void checkServerTrusted(X509Certificate[] chain, String authType) {
				// Self-signed test certificate: trust is intentionally unconditional.
			}

			@Override
			public X509Certificate[] getAcceptedIssuers() {
				return new X509Certificate[0];
			}
		};
	}
}
