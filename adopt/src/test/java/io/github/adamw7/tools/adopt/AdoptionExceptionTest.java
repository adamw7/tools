package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class AdoptionExceptionTest {

	@Test
	void messageOnlyConstructorKeepsMessageAndHasNoCause() {
		AdoptionException exception = new AdoptionException("clone failed");
		assertEquals("clone failed", exception.getMessage());
		assertNull(exception.getCause());
	}

	@Test
	void messageAndCauseConstructorKeepsBoth() {
		Throwable cause = new IllegalStateException("boom");
		AdoptionException exception = new AdoptionException("push failed", cause);
		assertEquals("push failed", exception.getMessage());
		assertSame(cause, exception.getCause());
	}
}
