package io.github.adamw7.tools.data.uniqueness.mcp;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import io.modelcontextprotocol.spec.McpStreamableServerTransportProvider;

@Configuration
public class McpConfiguration {

	static final String SSE_ENDPOINT = "/sse";

	static final String SSE_MESSAGE_ENDPOINT = "/mcp/message";

	private static final Logger log = LogManager.getLogger(McpConfiguration.class.getName());

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
				.mcpEndpoint("/mcp")
				.build();
	}

	@Bean
	@ConditionalOnProperty(prefix = "transport", name = "mode", havingValue = "streamable-http")
	public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> streamableServletRegistration(
			HttpServletStreamableServerTransportProvider transport) {
		ServletRegistrationBean<HttpServletStreamableServerTransportProvider> registration =
				new ServletRegistrationBean<>(transport, "/mcp");
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
		McpSyncServer syncServer = McpServer.sync(transport)
				.serverInfo("custom-server", "0.0.1")
				.capabilities(McpSchema.ServerCapabilities.builder().tools(true).resources(false, false).prompts(false).build())
				.build();
		registerTools(syncServer);
		return syncServer;
	}

	@Bean(destroyMethod = "close")
	@ConditionalOnProperty(prefix = "transport", name = "mode", havingValue = "streamable-http")
	public McpSyncServer mcpSyncServerStreamable(McpStreamableServerTransportProvider transport) {
		log.info("Initializing McpSyncServer with streamable transport: {}", transport);
		McpSyncServer syncServer = McpServer.sync(transport)
				.serverInfo("custom-server", "0.0.1")
				.capabilities(McpSchema.ServerCapabilities.builder().tools(true).resources(false, false).prompts(false).build())
				.build();
		registerTools(syncServer);
		return syncServer;
	}

	private void registerTools(McpSyncServer server) {
		UniquenessTool uniquenessTool = new UniquenessTool();
		SyncToolSpecification toolSpec = SyncToolSpecification.builder()
				.tool(uniquenessTool.getToolDefinition())
				.callHandler((exchange, request) -> uniquenessTool.apply(request.arguments()))
				.build();
		server.addTool(toolSpec);
	}
}
