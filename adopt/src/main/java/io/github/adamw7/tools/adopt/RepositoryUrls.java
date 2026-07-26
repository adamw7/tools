package io.github.adamw7.tools.adopt;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Reads the list of repository URLs an adoption run works through, from the two
 * forms the entry points offer: a file naming one repository per line, and a list
 * already collected in memory (repeated command-line flags, or an MCP argument).
 * Both end in {@link #distinct(List)}, so the two cannot drift apart on what
 * counts as a repository worth adopting.
 *
 * <p>A list file is written for people: blank lines are skipped and a line whose
 * first non-blank character is {@code #} is a comment, so a batch can be annotated
 * with why a repository is on it, and a repository can be commented out for a run
 * rather than deleted from the file.
 *
 * <p>Duplicates are dropped, keeping the order the URLs were given in. The same
 * URL twice would clone into one checkout directory and adopt it a second time,
 * which at best repeats the work and at worst pushes a branch built on a
 * half-adopted tree.
 */
public final class RepositoryUrls {

	private static final String COMMENT = "#";

	private RepositoryUrls() {
	}

	/**
	 * @param file a text file naming one repository URL per line
	 * @throws AdoptionException when the file cannot be read
	 */
	public static List<String> fromFile(Path file) {
		return fromLines(AdoptionFiles.read(file, "the repository URL list").lines().toList());
	}

	public static List<String> fromLines(List<String> lines) {
		return distinct(lines.stream().map(String::strip).filter(RepositoryUrls::isRepository).toList());
	}

	/** @return the URLs stripped of surrounding whitespace, without duplicates, in order */
	public static List<String> distinct(List<String> urls) {
		return List.copyOf(new LinkedHashSet<>(urls.stream().filter(Text::isPresent).map(String::strip).toList()));
	}

	private static boolean isRepository(String line) {
		return Text.isPresent(line) && !line.startsWith(COMMENT);
	}
}
