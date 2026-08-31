package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.tools.adopt.AdoptionContext;
import io.github.adamw7.tools.adopt.AdoptionContexts;
import io.github.adamw7.tools.adopt.command.RecordingCommandRunner;

class SkillsStepTest {

	private final SkillsStep step = new SkillsStep();

	@Test
	void installsEveryStarterSkill(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		step.execute(context, new RecordingCommandRunner());
		StarterSkills.WRITTEN_PATHS.forEach(
				path -> assertTrue(Files.isRegularFile(context.repositoryDirectory().resolve(path)), path));
	}

	@Test
	void runsNoExternalCommands(@TempDir Path workspace) throws IOException {
		RecordingCommandRunner runner = new RecordingCommandRunner();
		step.execute(AdoptionContexts.checkedOutIn(workspace), runner);
		assertEquals(0, runner.count());
	}

	@Test
	void isNamedSkills() {
		assertEquals("skills", step.name());
	}

	/**
	 * The step is separate from {@link AssetsStep} precisely because the skills
	 * describe the guard the checkout's own build system got, so a Maven checkout and
	 * a Gradle one must not be handed the same file.
	 */
	@Test
	void describesTheGuardTheCheckoutsBuildSystemWired(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		AdoptionContexts.write(context, "build.gradle", "plugins { id 'java' }\n");
		step.execute(context, new RecordingCommandRunner());
		assertTrue(buildSkill(context).contains("enforceClaudeMd"), buildSkill(context));
	}

	@Test
	void aMavenCheckoutIsToldToRunTheMavenGuard(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		AdoptionContexts.write(context, "pom.xml", "<project/>");
		step.execute(context, new RecordingCommandRunner());
		assertTrue(buildSkill(context).contains("mvn -q -N validate"), buildSkill(context));
	}

	@Test
	void isIdempotentAcrossReRuns(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		step.execute(context, new RecordingCommandRunner());
		Path skill = context.repositoryDirectory()
				.resolve(StarterSkills.skillFile(StarterSkills.CLAUDE_MD_SKILL));
		Files.writeString(skill, "customised\n");
		step.execute(context, new RecordingCommandRunner());
		assertEquals("customised\n", Files.readString(skill));
	}

	/**
	 * A step configured with a build-system list that matches nothing is skipped with a
	 * warning rather than failing the run, the same answer
	 * {@link AbstractBuildSystemStep} gives every step that acts on a build system.
	 * {@link BuildSystems#DEFAULTS} always matches, so this is reachable only from a
	 * caller assembling its own list.
	 */
	@Test
	void writesNothingWhenNoBuildSystemMatches(@TempDir Path workspace) throws IOException {
		AdoptionContext context = AdoptionContexts.checkedOutIn(workspace);
		new SkillsStep(List.of(new MavenBuildSystem())).execute(context, new RecordingCommandRunner());
		assertTrue(Files.notExists(context.repositoryDirectory().resolve(".claude/skills")));
	}

	private String buildSkill(AdoptionContext context) throws IOException {
		return Files.readString(context.repositoryDirectory()
				.resolve(StarterSkills.skillFile(StarterSkills.BUILD_SKILL)));
	}
}
