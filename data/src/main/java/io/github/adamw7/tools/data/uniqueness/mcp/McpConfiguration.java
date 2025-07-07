package io.github.adamw7.tools.data.uniqueness.mcp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerTransportProvider;

@Configuration
public class McpConfiguration {

	private final static Logger log = LogManager.getLogger(McpConfiguration.class.getName());

	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper();
	}

    @Bean
    @ConditionalOnProperty(prefix = "transport", name = "mode", havingValue = "stdio")
    public StdioServerTransportProvider stdioServerTransport() {
        log.info("Creating StdioServerTransport");
        return new StdioServerTransportProvider();
    }

	@Bean(destroyMethod = "close")
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