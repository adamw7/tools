package io.github.adamw7.tools.mcp;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;

import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import io.modelcontextprotocol.spec.McpStreamableServerTransportProvider;

/**
 * Wires an MCP server over one of four transports, selected with
 * {@code --transport.mode}: stdio (the default), a streamable HTTP transport
 * ({@code streamable-http}) registered at {@code /mcp}, a stateless HTTP transport
 * ({@code stateless-http}) also registered at {@code /mcp} that answers each
 * JSON-RPC request in isolation without keeping any session, or the legacy HTTP+SSE
 * transport ({@code sse}) registered at {@code /sse} (the event stream) and
 * {@code /mcp/message} (the JSON-RPC POST endpoint) for clients that predate
 * streamable HTTP. Concrete servers only supply their {@link #serverName() name}
 * and their {@link #tools() tools}; everything else is shared here. Subclasses are
 * the {@code @Configuration} beans, so the {@code @Bean} methods below are picked
 * up through them.
 */
public abstract class AbstractMcpConfiguration {

	protected static final String SSE_ENDPOINT = "/sse";

	protected static final String SSE_MESSAGE_ENDPOINT = "/mcp/message";

	protected static final String MCP_ENDPOINT = "/mcp";

	private static final String SERVER_VERSION = "0.0.1";

	private static final Logger log = LogManager.getLogger(AbstractMcpConfiguration.class);

	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper();
	}

	@Bean
	@ConditionalOnProperty(prefix = "transport", name = "mode", havingValue = "stdio", matchIfMissing = true)
	public StdioServerTransportProvider stdioServerTransport() {
		log.info("Creating StdioServerTransport");
		return new StdioServerTransportProvider(new JacksonMcpJsonMapper(objectMapper()));
	}

	@Bean
	@ConditionalOnProperty(prefix = "transport", name = "mode", havingValue = "streamable-http")
	public HttpServletStreamableServerTransportProvider streamableServerTransport() {
		log.info("Creating HttpServletStreamableServerTransport");
		return HttpServletStreamableServerTransportProvider.builder()
				.jsonMapper(new JacksonMcpJsonMapper(objectMapper()))
				.mcpEndpoint(MCP_ENDPOINT)
				.build();
	}

	@Bean
	@ConditionalOnProperty(prefix = "transport", name = "mode", havingValue = "streamable-http")
	public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> streamableServletRegistration(
			HttpServletStreamableServerTransportProvider transport) {
		ServletRegistrationBean<HttpServletStreamableServerTransportProvider> registration =
				new ServletRegistrationBean<>(transport, MCP_ENDPOINT);
		registration.setAsyncSupported(true);
		return registration;
	}

	@Bean
	@ConditionalOnProperty(prefix = "transport", name = "mode", havingValue = "stateless-http")
	public HttpServletStatelessServerTransport statelessServerTransport() {
		log.info("Creating HttpServletStatelessServerTransport");
		return HttpServletStatelessServerTransport.builder()
				.jsonMapper(new JacksonMcpJsonMapper(objectMapper()))
				.messageEndpoint(MCP_ENDPOINT)
				.build();
	}

	@Bean
	@ConditionalOnProperty(prefix = "transport", name = "mode", havingValue = "stateless-http")
	public ServletRegistrationBean<HttpServletStatelessServerTransport> statelessServletRegistration(
			HttpServletStatelessServerTransport transport) {
		ServletRegistrationBean<HttpServletStatelessServerTransport> registration =
				new ServletRegistrationBean<>(transport, MCP_ENDPOINT);
		registration.setAsyncSupported(true);
		return registration;
	}

	@Bean
	@ConditionalOnProperty(prefix = "transport", name = "mode", havingValue = "sse")
	public HttpServletSseServerTransportProvider sseServerTransport() {
		log.info("Creating HttpServletSseServerTransport");
		return HttpServletSseServerTransportProvider.builder()
				.jsonMapper(new JacksonMcpJsonMapper(objectMapper()))
				.sseEndpoint(SSE_ENDPOINT)
				.messageEndpoint(SSE_MESSAGE_ENDPOINT)
				.build();
	}

	@Bean
	@ConditionalOnProperty(prefix = "transport", name = "mode", havingValue = "sse")
	public ServletRegistrationBean<HttpServletSseServerTransportProvider> sseServletRegistration(
			HttpServletSseServerTransportProvider transport) {
		ServletRegistrationBean<HttpServletSseServerTransportProvider> registration =
				new ServletRegistrationBean<>(transport, SSE_ENDPOINT, SSE_MESSAGE_ENDPOINT);
		registration.setAsyncSupported(true);
		return registration;
	}

	@Bean(destroyMethod = "close")
	@ConditionalOnBean(McpServerTransportProvider.class)
	public McpSyncServer mcpSyncServer(McpServerTransportProvider transport) {
		log.info("Initializing McpSyncServer with transport: {}", transport);
		return buildServer(McpServer.sync(transport));
	}

	@Bean(destroyMethod = "close")
	@ConditionalOnProperty(prefix = "transport", name = "mode", havingValue = "streamable-http")
	public McpSyncServer mcpSyncServerStreamable(McpStreamableServerTransportProvider transport) {
		log.info("Initializing McpSyncServer with streamable transport: {}", transport);
		return buildServer(McpServer.sync(transport));
	}

	@Bean(destroyMethod = "close")
	@ConditionalOnProperty(prefix = "transport", name = "mode", havingValue = "stateless-http")
	public McpStatelessSyncServer mcpStatelessSyncServer(HttpServletStatelessServerTransport transport) {
		log.info("Initializing McpStatelessSyncServer with stateless transport: {}", transport);
		McpStatelessSyncServer statelessServer = McpServer.sync(transport)
				.serverInfo(serverName(), SERVER_VERSION)
				.capabilities(serverCapabilities())
				.build();
		registerStatelessTools(statelessServer);
		return statelessServer;
	}

	private McpSyncServer buildServer(McpServer.SyncSpecification<?> spec) {
		McpSyncServer syncServer = spec
				.serverInfo(serverName(), SERVER_VERSION)
				.capabilities(serverCapabilities())
				.build();
		registerTools(syncServer);
		return syncServer;
	}

	private McpSchema.ServerCapabilities serverCapabilities() {
		return McpSchema.ServerCapabilities.builder()
				.tools(true).resources(false, false).prompts(false).build();
	}

	private void registerTools(McpSyncServer server) {
		tools().forEach(tool -> server.addTool(specificationFor(tool)));
	}

	private SyncToolSpecification specificationFor(McpTool tool) {
		return SyncToolSpecification.builder()
				.tool(tool.getToolDefinition())
				.callHandler((exchange, request) -> tool.apply(request.arguments()))
				.build();
	}

	private void registerStatelessTools(McpStatelessSyncServer server) {
		tools().forEach(tool -> server.addTool(statelessSpecificationFor(tool)));
	}

	private McpStatelessServerFeatures.SyncToolSpecification statelessSpecificationFor(McpTool tool) {
		return McpStatelessServerFeatures.SyncToolSpecification.builder()
				.tool(tool.getToolDefinition())
				.callHandler((context, request) -> tool.apply(request.arguments()))
				.build();
	}

	/**
	 * Name reported to clients in the server handshake.
	 */
	protected abstract String serverName();

	/**
	 * Tools this server exposes; registered against every transport.
	 */
	protected abstract List<McpTool> tools();
}
