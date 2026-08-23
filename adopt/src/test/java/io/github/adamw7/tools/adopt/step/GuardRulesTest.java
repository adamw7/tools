package io.github.adamw7.tools.adopt.step;

import static io.github.adamw7.tools.test.ExpectedFailures.assertFailure;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The rule set decides what somebody else's build comes to enforce, so a name that
 * is not one of these is refused rather than read as the default: quietly widening
 * or narrowing the guard is the failure worth spending a test on.
 */
class GuardRulesTest {

	@Test
	void namesTheRuleTheAdoptedPomConfigures() {
		assertEquals("claudeMdFormat", GuardRules.MINIMAL.ruleName());
		assertEquals("claudeCodeProject", GuardRules.PROJECT.ruleName());
	}

	/** The value is read as an operator wrote it: in any case, and with room around it. */
	@Test
	void readsARuleSetNamedInAnyCase() {
		assertEquals(GuardRules.MINIMAL, GuardRules.of("minimal"));
		assertEquals(GuardRules.PROJECT, GuardRules.of("  Project  "));
	}

	/** The refusal names what it accepts, so the operator can correct the command line from it. */
	@Test
	void refusesAnUnknownRuleSetNamingTheOnesItAccepts() {
		assertFailure(IllegalArgumentException.class, () -> GuardRules.of("everything"),
				"everything", "minimal", "project");
	}
}
