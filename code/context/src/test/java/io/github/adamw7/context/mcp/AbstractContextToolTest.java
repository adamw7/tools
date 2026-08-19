package io.github.adamw7.context.mcp;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What every context tool answers the same way, asserted once. {@link
 * AbstractContextTool#apply} is final: it confines the path, defaults the
 * language and bounds the depth before handing a {@code Scan} to the tool, so
 * rejecting a missing path, a path outside the allowed roots and an excessive
 * depth is one behaviour shared by all four tools rather than four behaviours
 * that happen to agree. Asserting it in each tool's test repeated the same three
 * cases — and the fixture they need — once per tool, and let a tool be added
 * with none of them.
 * <p>
 * A subclass names its tool through {@link #tool()} and is left with only what
 * is its own: the tool's name, the arguments beyond the shared three, and what
 * it answers with.
 */
abstract class AbstractContextToolTest {

	protected static final ObjectMapper MAPPER = new ObjectMapper();

	@TempDir
	Path projectRoot;

	@TempDir
	Path outsideRoot;

	/** The tool under test, confined to {@link #projectRoot}. */
	protected abstract ContextTool tool();

	@Test
	@SuppressWarnings("unchecked")
	void toolDefinitionRequiresPath() {
		List<String> required = (List<String>) tool().getToolDefinition().inputSchema().get("required");
		assertTrue(required.contains("path"));
	}

	@Test
	void missingPathIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> tool().apply(new HashMap<>()));
	}

	@Test
	void pathOutsideTheAllowedRootIsRejected() {
		assertThrows(SecurityException.class, () -> tool().apply(pathArgument(outsideRoot)));
	}

	@Test
	void excessiveDepthIsRejected() {
		// The depth is bounded while the shared arguments are read, before the tool is
		// asked anything, so this needs no source file to scan.
		Map<String, Object> arguments = pathArgument(projectRoot);
		arguments.put("depth", 99);

		assertThrows(IllegalArgumentException.class, () -> tool().apply(arguments));
	}

	@Test
	void zeroDepthIsRejectedWithTheAcceptedRange() {
		// A depth of zero resolves nothing and every finder refuses it, so it is
		// refused here, where the message can name the range the tools do take.
		Map<String, Object> arguments = pathArgument(projectRoot);
		arguments.put("depth", 0);

		IllegalArgumentException rejected =
				assertThrows(IllegalArgumentException.class, () -> tool().apply(arguments));

		assertTrue(rejected.getMessage().contains("between 1 and 10"), rejected.getMessage());
	}

	/** The arguments naming a root, as the map a test then adds its own entries to. */
	protected Map<String, Object> pathArgument(Path root) {
		Map<String, Object> arguments = new HashMap<>();
		arguments.put("path", root.toString());
		return arguments;
	}

	protected void writeJava(String className, String body) throws IOException {
		Files.writeString(projectRoot.resolve(className + ".java"), body);
	}
}
