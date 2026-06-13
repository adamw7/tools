package io.github.adamw7.tools.data;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// transport.mode=none keeps the stdio MCP server out of the test context. A live
// stdio server spawns a non-daemon thread blocked on System.in that cannot be
// interrupted, which would prevent the forked test JVM from shutting down cleanly.
@SpringBootTest(classes = Application.class, properties = "transport.mode=none")
public class ApplicationTest {

	@Test
	void contextLoads() {
	}
}
