package io.github.adamw7.tools.data.uniqueness.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

public class MainTest {

	private String resolveTransportMode(String[] args) throws Exception {
		Method method = Main.class.getDeclaredMethod("resolveTransportMode", String[].class);
		method.setAccessible(true);
		return (String) method.invoke(null, (Object) args);
	}

	@Test
	public void defaultsToStdioWhenNoArguments() throws Exception {
		assertEquals("stdio", resolveTransportMode(new String[] {}));
	}

	@Test
	public void defaultsToStdioWhenPrefixAbsent() throws Exception {
		assertEquals("stdio", resolveTransportMode(new String[] { "--server.port=8080", "--other" }));
	}

	@Test
	public void readsExplicitTransportMode() throws Exception {
		assertEquals("sse", resolveTransportMode(new String[] { "--transport.mode=sse" }));
	}

	@Test
	public void findsTransportModeAmongOtherArguments() throws Exception {
		String[] args = { "--server.port=8080", "--transport.mode=streamable-http" };
		assertEquals("streamable-http", resolveTransportMode(args));
	}

	@Test
	public void supportsEmptyTransportModeValue() throws Exception {
		assertEquals("", resolveTransportMode(new String[] { "--transport.mode=" }));
	}
}
