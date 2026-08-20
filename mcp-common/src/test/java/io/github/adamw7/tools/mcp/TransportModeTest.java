package io.github.adamw7.tools.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class TransportModeTest {

	@Test
	public void readsEveryKnownMode() {
		assertSame(TransportMode.STDIO, TransportMode.of("stdio"));
		assertSame(TransportMode.STREAMABLE_HTTP, TransportMode.of("streamable-http"));
		assertSame(TransportMode.STATELESS_HTTP, TransportMode.of("stateless-http"));
	}

	/**
	 * The spellings the {@code @ConditionalOnProperty} conditions in
	 * {@link AbstractMcpConfiguration} are written with are the same constants, so a
	 * mode this enum accepts always matches the transport it names.
	 */
	@Test
	public void namesTheSpellingsTheTransportsAreGatedOn() {
		assertEquals(TransportMode.Value.STDIO, TransportMode.STDIO.value());
		assertEquals(TransportMode.Value.STREAMABLE_HTTP, TransportMode.STREAMABLE_HTTP.value());
		assertEquals(TransportMode.Value.STATELESS_HTTP, TransportMode.STATELESS_HTTP.value());
	}

	@Test
	public void everyModeIsReadBackFromItsOwnValue() {
		for (TransportMode mode : TransportMode.values()) {
			assertSame(mode, TransportMode.of(mode.value()));
		}
	}

	/**
	 * The enum's own constant names are not what a launch argument is written with, so
	 * accepting them would let a mode through that no transport condition matches.
	 */
	@Test
	public void refusesTheEnumConstantName() {
		assertThrows(IllegalArgumentException.class, () -> TransportMode.of("STREAMABLE_HTTP"));
	}

	@Test
	public void refusesAnUnknownModeNamingTheKnownOnes() {
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
				() -> TransportMode.of("streamablehttp"));

		assertTrue(thrown.getMessage().contains("stdio, streamable-http, stateless-http"), thrown.getMessage());
	}

	@Test
	public void refusesNull() {
		assertThrows(IllegalArgumentException.class, () -> TransportMode.of(null));
	}
}
