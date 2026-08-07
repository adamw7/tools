package io.github.adamw7.tools.test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.function.Executable;

/**
 * Asserts that a call fails, and that its message says why. Nearly every test of
 * a rule or of an adoption step asserts exactly that pair, which written out is
 * the {@code assertThrows} that names the exception type twice, a local to hold
 * it, and one {@code assertTrue(...contains(...), ...getMessage())} per fragment
 * the message must carry — with the message repeated as the failure description
 * so a test that breaks says what was actually thrown.
 * <p>
 * Collecting it here leaves each test naming only what it expects, and makes the
 * message the assertion description by construction rather than something every
 * call has to remember to pass.
 */
public final class ExpectedFailures {

	private ExpectedFailures() {
	}

	/**
	 * @param type              the exception the call must fail with
	 * @param failing           the call under test
	 * @param expectedInMessage fragments the failure's message must contain, in no
	 *                          particular order; none asserts only the type
	 * @return the failure, for a test that has more to say about it than its message
	 */
	public static <T extends Throwable> T assertFailure(Class<T> type, Executable failing,
			String... expectedInMessage) {
		T failure = assertThrows(type, failing);
		String message = String.valueOf(failure.getMessage());
		for (String expected : expectedInMessage) {
			assertTrue(message.contains(expected), message);
		}
		return failure;
	}
}
