package io.github.adamw7.tools.enforcer.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class CommandTokensTest {

	@Test
	void splitsOnWhitespace() {
		assertEquals(List.of("bash", "run.sh", "--flag"), CommandTokens.of("bash run.sh --flag"));
	}

	@Test
	void collapsesRunsOfWhitespace() {
		assertEquals(List.of("bash", "run.sh"), CommandTokens.of("  bash \t\n run.sh  "));
	}

	@Test
	void keepsADoubleQuotedPathWithASpaceInOneToken() {
		assertEquals(List.of("bash", "\"my hook.sh\""), CommandTokens.of("bash \"my hook.sh\""));
	}

	@Test
	void keepsASingleQuotedPathWithASpaceInOneToken() {
		assertEquals(List.of("bash", "'my hook.sh'"), CommandTokens.of("bash 'my hook.sh'"));
	}

	@Test
	void treatsAQuoteInsideTheOtherQuoteAsOrdinaryText() {
		assertEquals(List.of("echo", "\"it's here\""), CommandTokens.of("echo \"it's here\""));
	}

	@Test
	void keepsAnUnclosedQuoteAsOneTrailingToken() {
		assertEquals(List.of("bash", "\"my hook.sh"), CommandTokens.of("bash \"my hook.sh"));
	}

	@Test
	void yieldsNoTokensForABlankCommand() {
		assertEquals(List.of(), CommandTokens.of("   "));
	}

	@Test
	void keepsAQuotedSegmentAttachedToItsSurroundingToken() {
		assertEquals(List.of("\"$CLAUDE_PROJECT_DIR\"/my", "hook.sh"),
				CommandTokens.of("\"$CLAUDE_PROJECT_DIR\"/my hook.sh"));
	}

	@Test
	void splitsOnASemicolonSoItDoesNotStayGluedToAPath() {
		assertEquals(List.of("a.sh", "echo", "done"), CommandTokens.of("a.sh; echo done"));
	}

	@Test
	void splitsOnPipesAndAmpersandsWithNoSurroundingSpace() {
		assertEquals(List.of("a.sh", "b.sh", "c.sh"), CommandTokens.of("a.sh&&b.sh|c.sh"));
	}

	@Test
	void keepsAQuotedSeparatorInsideItsToken() {
		assertEquals(List.of("echo", "\"a;b\""), CommandTokens.of("echo \"a;b\""));
	}
}
