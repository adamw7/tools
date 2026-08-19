package io.github.adamw7.tools.adopt;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Reads the list of repository URLs an adoption run works through, in the two
 * forms the entry points offer: a file naming one repository per line, and a list
 * already collected in memory. Both end in {@link #distinct(List)}, so the two
 * cannot drift apart on what counts as a repository worth adopting.
 *
 * <p>A list file is written for people: blank lines are skipped and a {@code #}
 * first non-blank character is a comment, so a batch can be annotated and a
 * repository commented out rather than deleted. Duplicates are dropped, keeping the
 * order given — the same URL twice would adopt one checkout a second time.
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
		return distinct(AdoptionFiles.read(file, "the repository URL list").lines()
				.map(String::strip)
				.filter(RepositoryUrls::isRepository)
				.toList());
	}

	/** @return the URLs stripped of surrounding whitespace, without duplicates, in order */
	public static List<String> distinct(List<String> urls) {
		return List.copyOf(new LinkedHashSet<>(urls.stream().filter(Text::isPresent).map(String::strip).toList()));
	}

	private static boolean isRepository(String line) {
		return Text.isPresent(line) && !line.startsWith(COMMENT);
	}
}
