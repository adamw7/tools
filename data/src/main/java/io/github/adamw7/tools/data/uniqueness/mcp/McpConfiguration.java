package io.github.adamw7.tools.data.uniqueness.mcp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerTransportProvider;

public class McpConfiguration {

	private final static Logger log = LogManager.getLogger(McpConfiguration.class.getName());

	public ObjectMapper objectMapper() {
		return new ObjectMapper();
	}

//    @Bean
//    @ConditionalOnProperty(prefix = "transport", name = "mode", havingValue = "webflux", matchIfMissing = true)
//    public WebFluxSseServerTransport webFluxSseServerTransport(ObjectMapper mapper) {
//        String endpoint = "/mcp/message";
//        log.info("Creating WebFluxSseServerTransport with endpoint: {}", endpoint);
//        return new WebFluxSseServerTransport(mapper, endpoint);
//    }
//
//    @Bean
//    @ConditionalOnProperty(prefix = "transport", name = "mode", havingValue = "stdio")
//    public StdioServerTransport stdioServerTransport() {
//        log.info("Creating StdioServerTransport");
//        return new StdioServerTransport();
//    }
//
//    @Bean
//    @ConditionalOnProperty(prefix = "transport", name = "mode", havingValue = "webflux", matchIfMissing = true)
//    public RouterFunction<?> mcpRouterFunction(WebFluxSseServerTransport transport) {
//        log.info("Registering RouterFunction for MCP endpoint");
//        return transport.getRouterFunction();
//    }

	public McpSyncServer mcpSyncServer(McpServerTransportProvider transport) {
		log.info("Initializing McpSyncServer with transport: {}", transport);

		McpSyncServer syncServer = McpServer.sync(transport).serverInfo("custom-server", "0.0.1").capabilities(
				McpSchema.ServerCapabilities.builder().tools(true).resources(false, false).prompts(false).build())
				.build();

		UniquenessTool uniquenessTool = new UniquenessTool();
		var toolDefinition = uniquenessTool.getToolDefinition();

		McpServerFeatures.SyncToolSpecification syncToolSpecification = new McpServerFeatures.SyncToolSpecification(
				toolDefinition, (mcpSyncServerExchange, stringObjectMap) -> uniquenessTool.apply(stringObjectMap));

		syncServer.addTool(syncToolSpecification);
		return syncServer;
	}
}