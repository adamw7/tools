package io.github.adamw7.tools.adopt.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.web.server.LocalServerPort;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * What every adoption MCP integration test asserts, whichever HTTP transport the
 * subclass's {@code @SpringBootTest} started the server with: a synchronous client
 * reaches the {@code /mcp} endpoint, {@code adopt_repo} is discoverable there, and one
 * real call runs the pipeline end to end and answers with the run's JSON report. Only
 * the transport and the client name differ between the streamable-HTTP and the
 * stateless server, so that is all a subclass supplies — along with the calls it is
 * there to assert on its own.
 *
 * <p>The module's other integration tests drive the pipeline from Java. Neither of them
 * boots the server, so nothing here used to prove that the tool an agent actually calls
 * is reachable over a transport at all: that its definition survives the wire, that its
 * arguments arrive as the pipeline's options, and that a whole report — not a truncated
 * or protocol-level failure — comes back with the success flag the run earned.
 *
 * <p>The payload is a {@code verify_only} call rather than the {@code dry_run} an
 * operator rehearses with. A dry run reaches {@code claude init}, which needs a
 * logged-in {@code claude} the integration runner has not got — the same reason
 * {@code ForeignRepositoryAdoptionIT} leaves that step out. A verification shells out to
 * {@code git} alone: it clones and reads, and writes nothing at all, neither to GitHub
 * nor to the checkout. It is therefore the heaviest payload this server can be asked for
 * without credentials, and it exercises the argument mapping, the batch, and the report
 * exactly as an adoption would.
 *
 * <p>The repository is GitHub's own long-lived sample, a few hundred kilobytes, and it
 * has never been adopted — so the verification's verdict is a failure, and that verdict
 * is what proves the call was real: the report crosses the transport carrying the steps
 * that completed, the step that did not, and the checkout the run really made under this
 * test's own workspace.
 */
abstract class AbstractAdoptMcpIT {

	protected static final String ADOPT_REPO = "adopt_repo";

	/** A real repository, cloned over HTTPS so no key is needed, that nobody has adopted. */
	protected static final String HELLO_WORLD = "https://github.com/octocat/Hello-World.git";

	/** The directory {@code Hello-World} is cloned into, named after the repository. */
	private static final String CHECKOUT = "Hello-World";

	private static final String BRANCH = "claude/adopt-mcp-it";

	/**
	 * Well under the runner's ten-minute default, which is sized for a
	 * {@code claude init} rather than for cloning a sample repository, so a stalled
	 * network fails the test in minutes instead of hanging the build.
	 */
	private static final int TIMEOUT_MINUTES = 2;

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@LocalServerPort
	protected int port;

	/**
	 * The workspace the call is told to clone into, so the checkout the report names can
	 * be found and read — and so the clone is removed with the temporary directory
	 * rather than left under one nobody named. A verification keeps its checkout, that
	 * being the whole product of a run that pushes nothing.
	 */
	@TempDir
	protected Path workspace;

	protected McpSyncClient client;

	@BeforeEach
	void startClient() {
		client = McpClient.sync(transport())
				.clientInfo(McpSchema.Implementation.builder(clientName(), "1.0").build())
				.build();
		client.initialize();
	}

	@AfterEach
	void closeClient() {
		client.close();
	}

	/** The transport reaching the server this test started, built once {@link #port} is known. */
	protected abstract HttpClientStreamableHttpTransport transport();

	/** The name this test's client identifies itself to the server with. */
	protected abstract String clientName();

	@Test
	void listsTheAdoptionTool() {
		McpSchema.ListToolsResult tools = client.listTools();

		Set<String> names = tools.tools().stream().map(McpSchema.Tool::name).collect(Collectors.toSet());
		assertEquals(Set.of(ADOPT_REPO), names);
	}

	/**
	 * The call that proves the transport carries a real run: a repository is cloned from
	 * GitHub, read, and reported on, over the wire and back. The verdict is the failure
	 * the sample repository earns — it carries no {@code CLAUDE.md} and its build wires
	 * in no guard — which is asserted rather than avoided, because a report that names
	 * the step that stopped and the checkout it stopped in is the one an agent has to be
	 * able to read.
	 */
	@Test
	void verifiesARealRepositoryOverTheWire() {
		McpSchema.CallToolResult result = call(ADOPT_REPO, Map.of(
				"repository_url", HELLO_WORLD,
				"workspace", workspace.toString(),
				"branch", BRANCH,
				"verify_only", true,
				"timeout_minutes", TIMEOUT_MINUTES));

		assertTrue(result.isError(), "a repository that was never adopted must not verify");
		JsonNode report = parse(singleText(result));
		assertEquals(HELLO_WORLD, report.get("repositoryUrl").asText());
		assertEquals(BRANCH, report.get("branch").asText());
		assertFalse(report.get("succeeded").asBoolean());
		assertEquals(List.of("toolchain", "clone", "build-toolchain"), textValues(report.get("completedSteps")));
		assertVerdict(report.get("failure").asText());
		assertClonedIntoTheWorkspace(report);
	}

	/**
	 * The verdict names the step that reached it and both halves of what is missing, so
	 * an operator re-adopting the repository knows whether they are restoring a document,
	 * a guard, or both.
	 */
	private void assertVerdict(String failure) {
		assertTrue(failure.startsWith("check-adopted: "), failure);
		assertTrue(failure.contains("is not adopted"), failure);
		assertTrue(failure.contains("no CLAUDE.md at its root"), failure);
		assertTrue(failure.contains("wires in no CLAUDE.md guard"), failure);
	}

	/**
	 * The checkout is asserted on disk as well as in the report, since the report would
	 * name it either way: a {@code .git} directory under the workspace this call named is
	 * what says the clone really ran on the server rather than the arguments merely
	 * having been echoed back. A verification publishes nothing, so the pull request URL
	 * must be absent.
	 */
	private void assertClonedIntoTheWorkspace(JsonNode report) {
		Path checkout = Path.of(report.get("checkout").asText());
		assertEquals(CHECKOUT, checkout.getFileName().toString());
		assertTrue(Files.isDirectory(checkout.resolve(".git")), () -> checkout + " is not a git checkout");
		assertTrue(Files.isDirectory(workspace.resolve(CHECKOUT)), () -> "nothing was cloned into " + workspace);
		assertTrue(report.get("pullRequestUrl").isNull(), "a verification opens no pull request");
	}

	protected McpSchema.CallToolResult call(String tool, Map<String, Object> arguments) {
		return client.callTool(McpSchema.CallToolRequest.builder(tool).arguments(arguments).build());
	}

	/** @return the one text element of a result, whether it is a success or an error */
	protected String singleText(McpSchema.CallToolResult result) {
		assertEquals(1, result.content().size(), "expected exactly one content element");
		McpSchema.Content content = result.content().getFirst();
		assertInstanceOf(McpSchema.TextContent.class, content);
		return ((McpSchema.TextContent) content).text();
	}

	protected List<String> textValues(JsonNode array) {
		List<String> values = new ArrayList<>();
		array.forEach(node -> values.add(node.asText()));
		return values;
	}

	protected JsonNode parse(String json) {
		try {
			return MAPPER.readTree(json);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Invalid JSON: " + json, e);
		}
	}
}
