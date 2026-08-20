package io.github.adamw7.tools.mcp;

import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.Servlet;

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
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import io.modelcontextprotocol.spec.McpStreamableServerTransportProvider;

/**
 * Wires an MCP server over one of three transports, selected with
 * {@code --transport.mode}: stdio (the default); a streamable HTTP transport
 * ({@code streamable-http}) at {@code /mcp}; and a stateless HTTP transport
 * ({@code stateless-http}), also at {@code /mcp}, answering each JSON-RPC request
 * in isolation without keeping any session. Concrete servers supply only their
 * {@link #serverName() name} and their {@link #tools() tools}, and are the
 * {@code @Configuration} beans through which the {@code @Bean} methods below are
 * picked up.
 */
public abstract class AbstractMcpConfiguration {

	protected static final String MCP_ENDPOINT = "/mcp";

	private static final String SERVER_VERSION = ServerVersion.current();

	private static final Logger log = LogManager.getLogger(AbstractMcpConfiguration.class);

	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper();
	}

	@Bean
	@ConditionalOnProperty(prefix = "transport", name = "mode", havingValue = TransportMode.Value.STDIO,
			matchIfMissing = true)
	public StdioServerTransportProvider stdioServerTransport() {
		log.info("Creating StdioServerTransport");
		return new StdioServerTransportProvider(new JacksonMcpJsonMapper(objectMapper()));
	}

	@Bean
	@ConditionalOnProperty(prefix = "transport", name = "mode", havingValue = TransportMode.Value.STREAMABLE_HTTP)
	public HttpServletStreamableServerTransportProvider streamableServerTransport() {
		log.info("Creating HttpServletStreamableServerTransport");
		return HttpServletStreamableServerTransportProvider.builder()
				.jsonMapper(new JacksonMcpJsonMapper(objectMapper()))
				.mcpEndpoint(MCP_ENDPOINT)
				.build();
	}

	@Bean
	@ConditionalOnProperty(prefix = "transport", name = "mode", havingValue = TransportMode.Value.STREAMABLE_HTTP)
	public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> streamableServletRegistration(
			HttpServletStreamableServerTransportProvider transport) {
		return asyncRegistration(transport, MCP_ENDPOINT);
	}

	@Bean
	@ConditionalOnProperty(prefix = "transport", name = "mode", havingValue = TransportMode.Value.STATELESS_HTTP)
	public HttpServletStatelessServerTransport statelessServerTransport() {
		log.info("Creating HttpServletStatelessServerTransport");
		return HttpServletStatelessServerTransport.builder()
				.jsonMapper(new JacksonMcpJsonMapper(objectMapper()))
				.messageEndpoint(MCP_ENDPOINT)
				.build();
	}

	@Bean
	@ConditionalOnProperty(prefix = "transport", name = "mode", havingValue = TransportMode.Value.STATELESS_HTTP)
	public ServletRegistrationBean<HttpServletStatelessServerTransport> statelessServletRegistration(
			HttpServletStatelessServerTransport transport) {
		return asyncRegistration(transport, MCP_ENDPOINT);
	}

	/**
	 * Registers a transport servlet with async support on, which every HTTP transport
	 * needs so a response can be streamed after the request thread returns.
	 */
	private static <T extends Servlet> ServletRegistrationBean<T> asyncRegistration(T transport, String... endpoints) {
		ServletRegistrationBean<T> registration = new ServletRegistrationBean<>(transport, endpoints);
		registration.setAsyncSupported(true);
		return registration;
	}

	/**
	 * Built for the session-based transports, which is stdio here. Its condition and
	 * {@link #mcpSyncServerStreamable(McpStreamableServerTransportProvider)}'s are
	 * mutually exclusive — {@link McpStreamableServerTransportProvider} extends
	 * {@code McpServerTransportProviderBase}, not {@link McpServerTransportProvider},
	 * so this one cannot match in {@code streamable-http} mode — and the two carry
	 * different bean names regardless. Exactly one {@link McpSyncServer} is therefore
	 * ever defined, and no server needs
	 * {@code spring.main.allow-bean-definition-overriding}.
	 */
	@Bean(destroyMethod = "close")
	@ConditionalOnBean(McpServerTransportProvider.class)
	public McpSyncServer mcpSyncServer(McpServerTransportProvider transport) {
		log.info("Initializing McpSyncServer with transport: {}", transport);
		return buildServer(McpServer.sync(transport));
	}

	@Bean(destroyMethod = "close")
	@ConditionalOnProperty(prefix = "transport", name = "mode", havingValue = TransportMode.Value.STREAMABLE_HTTP)
	public McpSyncServer mcpSyncServerStreamable(McpStreamableServerTransportProvider transport) {
		log.info("Initializing McpSyncServer with streamable transport: {}", transport);
		return buildServer(McpServer.sync(transport));
	}

	@Bean(destroyMethod = "close")
	@ConditionalOnProperty(prefix = "transport", name = "mode", havingValue = TransportMode.Value.STATELESS_HTTP)
	public McpStatelessSyncServer mcpStatelessSyncServer(HttpServletStatelessServerTransport transport) {
		log.info("Initializing McpStatelessSyncServer with stateless transport: {}", transport);
		McpStatelessSyncServer statelessServer = McpServer.sync(transport)
				.serverInfo(serverName(), SERVER_VERSION)
				.capabilities(serverCapabilities())
				.build();
		tools().forEach(tool -> statelessServer.addTool(statelessSpecificationFor(tool)));
		return statelessServer;
	}

	private McpSyncServer buildServer(McpServer.SyncSpecification<?> spec) {
		McpSyncServer syncServer = spec
				.serverInfo(serverName(), SERVER_VERSION)
				.capabilities(serverCapabilities())
				.build();
		tools().forEach(tool -> syncServer.addTool(specificationFor(tool)));
		return syncServer;
	}

	private McpSchema.ServerCapabilities serverCapabilities() {
		return McpSchema.ServerCapabilities.builder()
				.tools(true).resources(false, false).prompts(false).build();
	}

	private SyncToolSpecification specificationFor(McpTool tool) {
		return SyncToolSpecification.builder()
				.tool(sdkTool(tool.getToolDefinition()))
				.callHandler((exchange, request) -> sdkResult(safeApply(tool, request.arguments())))
				.build();
	}

	private McpStatelessServerFeatures.SyncToolSpecification statelessSpecificationFor(McpTool tool) {
		return McpStatelessServerFeatures.SyncToolSpecification.builder()
				.tool(sdkTool(tool.getToolDefinition()))
				.callHandler((context, request) -> sdkResult(safeApply(tool, request.arguments())))
				.build();
	}

	/**
	 * Invokes a tool and turns any thrown exception into an error {@link ToolResult}
	 * rather than letting it surface as a protocol-level failure, so a bad argument or a
	 * denied path reaches the client as a clear, actionable tool error. Both what is
	 * logged and what is answered go through {@link FailureMessage}, since this one
	 * handler carries every tool's failure on every transport: the arguments a call
	 * failed on can name a clone URL or a JDBC URL, and the exception behind it can name
	 * where on disk the server keeps its files. Package-private so the behaviour can be
	 * exercised directly in tests.
	 */
	ToolResult safeApply(McpTool tool, Map<String, Object> arguments) {
		String name = tool.getToolDefinition().name();
		try {
			return tool.apply(arguments);
		} catch (RuntimeException e) {
			log.warn("MCP tool {} failed for {}", name, FailureMessage.forLog(arguments), e);
			return ToolResult.error(name + " failed: " + FailureMessage.forClient(e));
		}
	}

	/**
	 * Translates the SPI's transport-neutral {@link ToolDefinition} into the SDK's
	 * {@link Tool}, so tools describe themselves without depending on the SDK.
	 */
	private static Tool sdkTool(ToolDefinition definition) {
		return Tool.builder(definition.name(), definition.inputSchema())
				.description(definition.description())
				.build();
	}

	/**
	 * Translates the SPI's transport-neutral {@link ToolResult} into the SDK's
	 * {@link CallToolResult} returned to the client.
	 */
	private static CallToolResult sdkResult(ToolResult result) {
		return CallToolResult.builder()
				.content(List.of(TextContent.builder(result.text()).build()))
				.isError(result.isError())
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
