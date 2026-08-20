package io.github.adamw7.tools.mcp;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The transports an MCP server can be started on, and the only values
 * {@code --transport.mode} accepts. Naming them once, as a type, is what lets an
 * unrecognised mode be refused at the command line instead of reaching Spring: every
 * transport's beans in {@link AbstractMcpConfiguration} are gated on the property
 * matching one of these spellings exactly, so {@code streamablehttp} matched no
 * transport at all — and, not being stdio, left the web server enabled. The server
 * then started, bound its port, logged nothing unusual, and served no {@code /mcp}
 * endpoint, with a single character in a launch argument the only thing to point at.
 */
public enum TransportMode {

	STDIO(Value.STDIO),
	STREAMABLE_HTTP(Value.STREAMABLE_HTTP),
	STATELESS_HTTP(Value.STATELESS_HTTP);

	/**
	 * The spellings as compile-time constants, so the {@code @ConditionalOnProperty}
	 * conditions and this enum cannot drift apart: an annotation takes only a constant
	 * expression, which an enum constant is not, and an enum's own constants must be
	 * declared before any field of it, so the list above cannot refer to fields
	 * declared below it either. A nested class is what both can name.
	 */
	public static final class Value {

		public static final String STDIO = "stdio";

		public static final String STREAMABLE_HTTP = "streamable-http";

		public static final String STATELESS_HTTP = "stateless-http";

		private Value() {
		}
	}

	private final String value;

	TransportMode(String value) {
		this.value = value;
	}

	/**
	 * @return the spelling this mode is named by on the command line and in the
	 *         {@code transport.mode} property the transports are gated on
	 */
	public String value() {
		return value;
	}

	/**
	 * @param value the spelling given on the command line
	 * @return the mode it names
	 * @throws IllegalArgumentException naming every known mode when it names none of
	 *         them, so a typo is reported where it was made rather than becoming a
	 *         server that answers nothing
	 */
	public static TransportMode of(String value) {
		return Arrays.stream(values())
				.filter(mode -> mode.value.equals(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown transport mode: '" + value
						+ "'. Known modes are: " + known()));
	}

	private static String known() {
		return Arrays.stream(values()).map(TransportMode::value).collect(Collectors.joining(", "));
	}
}
