package io.github.adamw7.tools.adopt;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.adamw7.tools.adopt.command.CommandRunner;
import io.github.adamw7.tools.adopt.command.ProcessCommandRunner;

/**
 * Command-line entry point: given a GitHub repository URL (and an optional
 * workspace directory to clone into and an optional feature-branch name), runs
 * the default adoption pipeline against a real {@code git}/{@code claude}/
 * {@code gh} toolchain. A supplied workspace directory is created when it does
 * not yet exist, so the clone step always has a directory to run in; when
 * omitted, a temporary one is created instead. When no branch name is supplied,
 * {@link AdoptionContext#DEFAULT_BRANCH} is used.
 */
public class Main {

	public static void main(String[] args) {
		String repositoryUrl = repositoryUrl(args);
		Path workspace = workspace(args);
		String branchName = branchName(args);
		CommandRunner runner = new ProcessCommandRunner();
		GitHubRepoAdopter.withDefaultPipeline(runner)
				.adopt(new AdoptionContext(repositoryUrl, workspace, branchName));
	}

	private static String repositoryUrl(String[] args) {
		if (args == null || args.length < 1 || args[0].isBlank()) {
			throw new IllegalArgumentException("Usage: <github-repo-url> [workspace-directory] [branch-name]");
		}
		return args[0];
	}

	static String branchName(String[] args) {
		if (args.length >= 3 && !args[2].isBlank()) {
			return args[2];
		}
		return AdoptionContext.DEFAULT_BRANCH;
	}

	static Path workspace(String[] args) {
		if (args.length >= 2 && !args[1].isBlank()) {
			return createIfMissing(Path.of(args[1]));
		}
		return createTemporaryWorkspace();
	}

	private static Path createIfMissing(Path workspace) {
		try {
			return Files.createDirectories(workspace);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static Path createTemporaryWorkspace() {
		try {
			return Files.createTempDirectory("claude-adopt-");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
