package io.github.adamw7.tools.enforcer.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.Test;

/**
 * What a configured {@code <severity>} is read as, and — the point of the type —
 * what it refuses.
 */
class SeverityTest {

	@Test
	void readsWarnWhateverItsCaseAndPadding() throws EnforcerRuleException {
		for (String configured : List.of("warn", "WARN", "Warn", " warn ", "warn\n\t")) {
			assertEquals(Optional.of(Severity.WARN), Severity.parse(configured, "aRule"), configured);
		}
	}

	@Test
	void readsErrorWhateverItsCaseAndPadding() throws EnforcerRuleException {
		for (String configured : List.of("error", "ERROR", " Error ")) {
			assertEquals(Optional.of(Severity.ERROR), Severity.parse(configured, "aRule"), configured);
		}
	}

	/** An unset element names no severity, which lets the caller fall back rather than guess. */
	@Test
	void namesNoSeverityWhenBlank() throws EnforcerRuleException {
		for (String configured : List.of("", "   ", "\n")) {
			assertEquals(Optional.empty(), Severity.parse(configured, "aRule"), configured);
		}
	}

	@Test
	void namesNoSeverityWhenNull() throws EnforcerRuleException {
		assertEquals(Optional.empty(), Severity.parse(null, "aRule"));
	}

	/**
	 * The regression this type exists for: 'warning' and 'info' read as the default
	 * and failed the build, which looks exactly like the rule doing its job.
	 */
	@Test
	void refusesAValueThatIsNotASeverity() {
		for (String configured : List.of("warning", "info", "ignore", "none", "off", "fail", "true")) {
			EnforcerRuleException thrown = assertThrows(EnforcerRuleException.class,
					() -> Severity.parse(configured, "aRule"), configured);

			assertTrue(thrown.getMessage().contains("'" + configured + "'"), thrown.getMessage());
		}
	}

	/** The refusal has to say which rule to correct and what it will accept. */
	@Test
	void refusalNamesTheRuleAndTheValuesItAccepts() {
		EnforcerRuleException thrown = assertThrows(EnforcerRuleException.class,
				() -> Severity.parse("warning", "ClaudeMdFormatRule[claudeMdFile=/tmp/CLAUDE.md]"));

		String message = thrown.getMessage();
		assertTrue(message.contains("ClaudeMdFormatRule[claudeMdFile=/tmp/CLAUDE.md]"), message);
		assertTrue(message.contains("error"), message);
		assertTrue(message.contains("warn"), message);
	}

	@Test
	void configuredNameIsTheLowerCaseSpellingAPomWrites() {
		assertEquals("warn", Severity.WARN.configuredName());
		assertEquals("error", Severity.ERROR.configuredName());
	}

	@Test
	void defaultsToError() {
		assertEquals(Severity.ERROR, Severity.DEFAULT);
	}
}
