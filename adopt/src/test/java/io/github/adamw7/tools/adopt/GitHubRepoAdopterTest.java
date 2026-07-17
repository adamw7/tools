package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.adamw7.tools.adopt.command.CommandRunner;
import io.github.adamw7.tools.adopt.command.RecordingCommandRunner;
import io.github.adamw7.tools.adopt.step.AdoptionStep;

class GitHubRepoAdopterTest {

	private final AdoptionContext context = new AdoptionContext("https://github.com/adamw7/tools.git",
			Path.of("/tmp/workspace"));

	private static final class NamingStep implements AdoptionStep {

		private final String name;
		private final List<String> log;

		NamingStep(String name, List<String> log) {
			this.name = name;
			this.log = log;
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public void execute(AdoptionContext context, CommandRunner runner) {
			log.add(name);
		}
	}

	private static final class ExplodingStep implements AdoptionStep {

		@Override
		public String name() {
			return "boom";
		}

		@Override
		public void execute(AdoptionContext context, CommandRunner runner) {
			throw new AdoptionException("boom");
		}
	}

	@Test
	void runsEveryStepInOrder() {
		List<String> order = new ArrayList<>();
		List<AdoptionStep> steps = List.of(new NamingStep("a", order), new NamingStep("b", order));
		new GitHubRepoAdopter(new RecordingCommandRunner(), steps).adopt(context);
		assertEquals(List.of("a", "b"), order);
	}

	@Test
	void stopsAtFirstFailingStep() {
		List<String> order = new ArrayList<>();
		List<AdoptionStep> steps = List.of(new NamingStep("a", order), new ExplodingStep(),
				new NamingStep("c", order));
		GitHubRepoAdopter adopter = new GitHubRepoAdopter(new RecordingCommandRunner(), steps);
		assertThrows(AdoptionException.class, () -> adopter.adopt(context));
		assertEquals(List.of("a"), order);
	}

	@Test
	void defaultPipelineIsCloneTrustInitCommitPushEnforceCommitPush() {
		List<String> names = GitHubRepoAdopter.defaultSteps().stream().map(AdoptionStep::name).toList();
		assertEquals(List.of("clone", "trust", "claude-init", "commit", "push", "enforcer", "commit", "push"), names);
	}
}
