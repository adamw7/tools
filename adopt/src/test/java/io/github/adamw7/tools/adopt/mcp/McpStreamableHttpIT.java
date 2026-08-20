package io.github.adamw7.tools.adopt.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Integration test that serves the adoption MCP server over the streamable HTTP
 * transport, on a random port, and drives {@code adopt_repo} across it with a real
 * client.
 *
 * <p>Beyond the discovery and the real run every transport is held to, this one asserts
 * what only a client can see: the tool's schema as it arrives on the wire, and the two
 * refusals an agent is most likely to earn — a call naming no repository, and one asking
 * for more parallelism than a batch will take. Both are answered as error results
 * carrying the argument's own name, rather than as protocol-level failures, which is
 * what lets a client correct the call instead of only reporting that something threw.
 */
@SpringBootTest(
		classes = Main.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"transport.mode=streamable-http",
				"spring.main.banner-mode=off" })
public class McpStreamableHttpIT extends AbstractAdoptMcpIT {

	/**
	 * Every argument the tool takes, as a client reads them off the wire. Pinned whole
	 * rather than sampled: an argument dropped in the translation from the SPI's
	 * transport-neutral definition to the SDK's tool is invisible from inside the
	 * pipeline — the server keeps answering, and the call silently runs with the default
	 * of whatever the client thought it had asked for.
	 */
	private static final Set<String> ARGUMENTS = Set.of("repository_url", "repository_urls", "workspace",
			"branch", "title", "body", "reviewers", "labels", "assignees", "draft", "assets", "rule_version",
			"parallel", "verify_only", "keep_workspace", "dry_run", "timeout_minutes", "retries", "rules",
			"claude_md_sections");

	@Override
	protected HttpClientStreamableHttpTransport transport() {
		return HttpClientStreamableHttpTransport.builder("http://localhost:" + port).build();
	}

	@Override
	protected String clientName() {
		return "integration-test-client";
	}

	@Test
	void describesEveryArgumentTheToolTakes() {
		McpSchema.Tool tool = client.listTools().tools().getFirst();

		assertEquals(ADOPT_REPO, tool.name());
		assertTrue(tool.description().contains("Adopt Claude Code into one or more GitHub repositories"),
				tool.description());
		Object properties = tool.inputSchema().get("properties");
		assertInstanceOf(Map.class, properties);
		assertEquals(ARGUMENTS, ((Map<?, ?>) properties).keySet());
	}

	/**
	 * The one requirement the schema cannot express as a required field — a call must
	 * name a repository through one argument or the other — so it is the one an agent
	 * reaches by sending a well-formed call the pipeline cannot run.
	 */
	@Test
	void refusesACallNamingNoRepository() {
		McpSchema.CallToolResult result = call(ADOPT_REPO, Map.of("verify_only", true));

		assertTrue(result.isError());
		String message = singleText(result);
		assertTrue(message.contains(ADOPT_REPO + " failed"), message);
		assertTrue(message.contains("repository_url or repository_urls"), message);
	}

	/**
	 * A bound the client is answered with by name. The batch enforces it too, but its
	 * complaint is about a field the client never sent; refusing here says which
	 * argument to change.
	 */
	@Test
	void refusesMoreParallelismThanABatchWillTake() {
		McpSchema.CallToolResult result = call(ADOPT_REPO, Map.of(
				"repository_url", HELLO_WORLD,
				"verify_only", true,
				"parallel", 50));

		assertTrue(result.isError());
		String message = singleText(result);
		assertTrue(message.contains("parallel"), message);
	}
}
