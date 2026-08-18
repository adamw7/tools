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

	@Test
	void readsTheProgramOfASingleCommand() {
		assertEquals(List.of(".claude/hooks/session-start.sh"),
				CommandTokens.scriptCandidatesOf(".claude/hooks/session-start.sh"));
	}

	@Test
	void readsTheProgramOfEveryChainedCommand() {
		assertEquals(List.of("a.sh", "b.sh", "c.sh"), CommandTokens.scriptCandidatesOf("a.sh && b.sh; c.sh --flag"));
	}

	@Test
	void doesNotReadAnArgumentAsAProgram() {
		assertEquals(List.of("build.sh"), CommandTokens.scriptCandidatesOf("build.sh --out target/log.txt"));
	}

	/** An interpreter names a script of its own, so both it and that script are candidates. */
	@Test
	void readsBothAnInterpreterAndTheScriptItRuns() {
		assertEquals(List.of("bash", ".claude/hooks/run.sh"),
				CommandTokens.scriptCandidatesOf("bash .claude/hooks/run.sh"));
	}

	@Test
	void skipsAnInterpretersOptionsToReachItsScript() {
		assertEquals(List.of("bash", "run.sh"), CommandTokens.scriptCandidatesOf("bash -eu run.sh"));
	}

	/** The argument of {@code -c} is the script's text rather than a file to look for. */
	@Test
	void readsNoScriptFromAnInlineShellScript() {
		assertEquals(List.of("sh"), CommandTokens.scriptCandidatesOf("sh -c \"run.sh --flag\""));
	}

	/** A shell reads {@code -ec} as {@code -e -c}, so the inline script is still text. */
	@Test
	void readsNoScriptFromAnInlineShellScriptInAFlagCluster() {
		assertEquals(List.of("bash"), CommandTokens.scriptCandidatesOf("bash -ec \"run.sh --flag\""));
	}

	/**
	 * The {@code pipefail} of {@code -euo pipefail} is the value the {@code o}
	 * ending the cluster takes, not the script. Reading the first non-option
	 * argument blindly took it for one and left the real script — the word behind
	 * it — unread.
	 */
	@Test
	void readsPastTheValueAShortOptionClusterTakes() {
		assertEquals(List.of("bash", ".claude/hooks/run.sh"),
				CommandTokens.scriptCandidatesOf("bash -euo pipefail .claude/hooks/run.sh"));
	}

	/** Only the last flag of a cluster can take a value, so {@code -oe} takes none. */
	@Test
	void readsAValueOnlyForTheFlagThatEndsACluster() {
		assertEquals(List.of("bash", "run.sh"), CommandTokens.scriptCandidatesOf("bash -oe run.sh"));
	}

	@Test
	void readsPastTheValueALongOptionTakes() {
		assertEquals(List.of("bash", ".claude/hooks/run.sh"),
				CommandTokens.scriptCandidatesOf("bash --rcfile config/bashrc .claude/hooks/run.sh"));
	}

	/** A python {@code -m} names a module, not a file, so the segment names no script. */
	@Test
	void readsNoScriptFromAModuleName() {
		assertEquals(List.of("python3"), CommandTokens.scriptCandidatesOf("python3 -m tools.runner"));
	}

	/**
	 * The {@code -e} of node, perl and ruby carries the script's text the way a
	 * shell's {@code -c} does. Reading only {@code -c} took that text for a path,
	 * and {@code require('./boot')} was reported as a script missing from disk.
	 */
	@Test
	void readsNoScriptFromAnInlineRuntimeScript() {
		assertEquals(List.of("node"), CommandTokens.scriptCandidatesOf("node -e \"require('./boot')\""));
		assertEquals(List.of("perl"), CommandTokens.scriptCandidatesOf("perl -e 'print \"a/b\"'"));
		assertEquals(List.of("ruby"), CommandTokens.scriptCandidatesOf("ruby -e 'puts 1/2'"));
	}

	/** A shell's {@code -e} stops on error; only a runtime's carries a script. */
	@Test
	void readsTheScriptOfAShellRunWithErrexit() {
		assertEquals(List.of("sh", ".claude/hooks/run.sh"),
				CommandTokens.scriptCandidatesOf("sh -e .claude/hooks/run.sh"));
	}

	/** Node's {@code -c} checks the syntax of a file it is handed, unlike a shell's. */
	@Test
	void readsTheScriptNodeIsAskedToCheck() {
		assertEquals(List.of("node", "boot.js"), CommandTokens.scriptCandidatesOf("node -c boot.js"));
	}

	@Test
	void recognisesAnInterpreterNamedByAnAbsolutePath() {
		assertEquals(List.of("/usr/bin/bash", "run.sh"), CommandTokens.scriptCandidatesOf("/usr/bin/bash run.sh"));
	}

	/** Only an interpreter hands its argument on; any other program's arguments are its own. */
	@Test
	void readsNoScriptFromAnOrdinaryProgramsArgument() {
		assertEquals(List.of("mkdir"), CommandTokens.scriptCandidatesOf("mkdir -p target/logs"));
	}

	@Test
	void keepsAQuotedProgramWithASpaceWhole() {
		assertEquals(List.of("\"my hook.sh\""), CommandTokens.scriptCandidatesOf("\"my hook.sh\" --flag"));
	}

	@Test
	void readsNoProgramFromAQuotedSeparator() {
		assertEquals(List.of("echo"), CommandTokens.scriptCandidatesOf("echo \"a;b\""));
	}

	@Test
	void yieldsNoProgramsForABlankCommand() {
		assertEquals(List.of(), CommandTokens.scriptCandidatesOf("   "));
	}

	@Test
	void yieldsNoProgramsForOperatorsAlone() {
		assertEquals(List.of(), CommandTokens.scriptCandidatesOf("&& ;| "));
	}

	/** A program is the first token of its own segment, however the operators are spaced. */
	@Test
	void readsTheProgramAfterAnOperatorWithNoSurroundingSpace() {
		assertEquals(List.of("a.sh", "b.sh"), CommandTokens.scriptCandidatesOf("a.sh --flag&&b.sh --flag"));
	}

	@Test
	void doesNotSplitASegmentOnAQuotedOperator() {
		assertEquals(List.of("run.sh", "b.sh"), CommandTokens.scriptCandidatesOf("run.sh \"a;b\"; b.sh"));
	}

	/** A hook written across several lines runs one program per line, not one in total. */
	@Test
	void readsAProgramFromEveryLineOfAMultiLineCommand() {
		assertEquals(List.of("a.sh", "b.sh"), CommandTokens.scriptCandidatesOf("a.sh --flag\nb.sh"));
	}

	@Test
	void readsAProgramFromEveryLineSeparatedByACarriageReturn() {
		assertEquals(List.of("a.sh", "b.sh"), CommandTokens.scriptCandidatesOf("a.sh\r\nb.sh"));
	}

	@Test
	void readsTheProgramOfASubshellWithoutItsParentheses() {
		assertEquals(List.of("a.sh"), CommandTokens.scriptCandidatesOf("(a.sh --flag)"));
	}

	@Test
	void splitsOnParenthesesSoTheyDoNotStayGluedToAPath() {
		assertEquals(List.of("a.sh"), CommandTokens.of("(a.sh)"));
	}

	@Test
	void skipsAVariableAssignmentToReachTheProgramItPrefixes() {
		assertEquals(List.of("a.sh"), CommandTokens.scriptCandidatesOf("FOO=bar a.sh"));
	}

	@Test
	void skipsEveryLeadingAssignmentOfACommand() {
		assertEquals(List.of("a.sh"), CommandTokens.scriptCandidatesOf("FOO=bar BAZ=1 a.sh --flag"));
	}

	@Test
	void yieldsNoProgramForAnAssignmentAlone() {
		assertEquals(List.of(), CommandTokens.scriptCandidatesOf("FOO=bar"));
	}

	/** A flag written as {@code --out=x} is an argument, and never the program of its segment. */
	@Test
	void doesNotMistakeAValuedFlagForAnAssignment() {
		assertEquals(List.of("--out=x"), CommandTokens.scriptCandidatesOf("--out=x"));
	}

	@Test
	void skipsAReservedWordToReachTheCommandItIntroduces() {
		assertEquals(List.of("[", "a.sh"),
				CommandTokens.scriptCandidatesOf("if [ -n \"$CI\" ]; then a.sh; fi"));
	}

	/**
	 * The loop variable takes the place of the program in the {@code for} segment,
	 * which costs nothing: a bare word names no path, so no rule resolves it.
	 */
	@Test
	void readsTheBodyOfALoopRatherThanItsKeyword() {
		assertEquals(List.of("f", "a.sh"), CommandTokens.scriptCandidatesOf("for f in x; do a.sh; done"));
	}

	@Test
	void readsBothBranchesOfAConditional() {
		assertEquals(List.of("a.sh", "b.sh"),
				CommandTokens.scriptCandidatesOf("if a.sh; then b.sh; fi"));
	}

	@Test
	void yieldsNoProgramForAReservedWordAlone() {
		assertEquals(List.of(), CommandTokens.scriptCandidatesOf("fi; done; esac"));
	}

	/** {@code then} names the command that follows it, but a file really called {@code then} is a program. */
	@Test
	void stillReadsAReservedWordSpelledAsAPath() {
		assertEquals(List.of("./then"), CommandTokens.scriptCandidatesOf("./then --flag"));
	}

	/**
	 * {@code exec} replaces the shell with what follows it, so the script it hands on
	 * is the one the hook runs. Reading {@code exec} as the program left that script
	 * unresolved: a rename of it passed the missing-script check, and
	 * {@code reportUnreferencedScripts} called it referenced by nothing.
	 */
	@Test
	void skipsExecToReachTheScriptItRuns() {
		assertEquals(List.of(".claude/hooks/session-start.sh"),
				CommandTokens.scriptCandidatesOf("exec .claude/hooks/session-start.sh"));
	}

	@Test
	void skipsEveryWrapperThatRunsTheCommandAfterIt() {
		assertEquals(List.of("a.sh"), CommandTokens.scriptCandidatesOf("command a.sh"));
		assertEquals(List.of("a.sh"), CommandTokens.scriptCandidatesOf("builtin a.sh"));
		assertEquals(List.of("a.sh"), CommandTokens.scriptCandidatesOf("nohup a.sh"));
		assertEquals(List.of("a.sh"), CommandTokens.scriptCandidatesOf("env a.sh"));
		assertEquals(List.of("a.sh"), CommandTokens.scriptCandidatesOf("time a.sh"));
	}

	/** A wrapper and an assignment are skipped over alike, in whichever order they are written. */
	@Test
	void skipsAnAssignmentPassedThroughAWrapper() {
		assertEquals(List.of("a.sh"), CommandTokens.scriptCandidatesOf("env LOG_LEVEL=debug a.sh"));
	}

	@Test
	void skipsAWrapperInEverySegmentOfAChainedCommand() {
		assertEquals(List.of("a.sh", "b.sh"), CommandTokens.scriptCandidatesOf("exec a.sh; nohup b.sh"));
	}

	/** {@code time} names what follows it, but a file really called {@code time} is a program. */
	@Test
	void stillReadsAWrapperSpelledAsAPath() {
		assertEquals(List.of("./time"), CommandTokens.scriptCandidatesOf("./time a.sh"));
	}

	@Test
	void yieldsNoProgramForAWrapperAlone() {
		assertEquals(List.of(), CommandTokens.scriptCandidatesOf("exec; nohup"));
	}

	@Test
	void endsATokenAtAGluedRedirection() {
		// Reading the > as part of the path invented a script named "build.sh>" that
		// no file can match, and the rules reported it as missing.
		assertEquals(List.of(".claude/hooks/build.sh", "build.log"),
				CommandTokens.of(".claude/hooks/build.sh> build.log"));
	}

	@Test
	void readsOnlyTheProgramAsAScriptAcrossARedirection() {
		// A redirection ends a token but not the command, so the file after it stays
		// an argument: making it an operator would have turned logs/build.log into a
		// program this rule requires to exist.
		assertEquals(List.of(".claude/hooks/build.sh"),
				CommandTokens.scriptCandidatesOf(".claude/hooks/build.sh > logs/build.log"));
		assertEquals(List.of(".claude/hooks/build.sh"),
				CommandTokens.scriptCandidatesOf(".claude/hooks/build.sh>logs/build.log"));
	}

	@Test
	void readsTheScriptAnInterpreterIsHandedAcrossARedirection() {
		assertEquals(List.of("bash", ".claude/hooks/build.sh"),
				CommandTokens.scriptCandidatesOf("bash .claude/hooks/build.sh >out.log"));
	}

	@Test
	void namesTheDescriptorOfACopiedRedirectionAsAProgramThatResolvesToNothing() {
		// The & of 2>&1 ends the command, so the 1 after it is read as a program of
		// its own. It names no path, so ClaudeProjectDir resolves it to nothing —
		// which is why the candidate list may carry a word no file can match.
		assertEquals(List.of(".claude/hooks/build.sh", "1"),
				CommandTokens.scriptCandidatesOf(".claude/hooks/build.sh >out.log 2>&1"));
	}
}
