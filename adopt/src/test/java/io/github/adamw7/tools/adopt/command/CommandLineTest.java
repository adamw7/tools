package io.github.adamw7.tools.adopt.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class CommandLineTest {

	@Test
	void ofKeepsTheProgramAndItsArgumentsInOrder() {
		assertEquals(List.of("git", "checkout", "-B"), CommandLine.of("git", "checkout", "-B").toList());
	}

	@Test
	void ofWithoutArgumentsStartsAnEmptyCommand() {
		assertEquals(List.of(), CommandLine.of().toList());
	}

	@Test
	void addAppendsInOrder() {
		assertEquals(List.of("git", "commit", "-m", "message"),
				CommandLine.of("git").add("commit").add("-m", "message").toList());
	}

	@Test
	void addAllAppendsAnAssembledRun() {
		assertEquals(List.of("mvnw", "-B", "verify"),
				CommandLine.of("mvnw").addAll(List.of("-B", "verify")).toList());
	}

	@Test
	void addIfAppendsOnlyWhenTheConditionHolds() {
		assertEquals(List.of("gh", "--draft"), CommandLine.of("gh").addIf(true, "--draft").toList());
		assertEquals(List.of("gh"), CommandLine.of("gh").addIf(false, "--draft").toList());
	}

	@Test
	void addEachRepeatsTheFlagOncePerValue() {
		assertEquals(List.of("gh", "--reviewer", "ann", "--reviewer", "bob"),
				CommandLine.of("gh").addEach("--reviewer", List.of("ann", "bob")).toList());
	}

	@Test
	void addEachLeavesTheCommandUntouchedForNoValues() {
		assertEquals(List.of("gh"), CommandLine.of("gh").addEach("--label", List.of()).toList());
	}

	@Test
	void toListIsImmutable() {
		List<String> command = CommandLine.of("git").toList();
		assertThrows(UnsupportedOperationException.class, () -> command.add("status"));
	}

	@Test
	void toListDoesNotSeeLaterAdditions() {
		CommandLine command = CommandLine.of("git");
		List<String> assembled = command.toList();
		command.add("status");
		assertEquals(List.of("git"), assembled);
	}

	@Test
	void addAllCopiesTheSourceList() {
		List<String> arguments = new ArrayList<>(List.of("verify"));
		CommandLine command = CommandLine.of("mvn").addAll(arguments);
		arguments.add("deploy");
		assertEquals(List.of("mvn", "verify"), command.toList());
	}
}
