package io.github.adamw7.tools.adopt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * What becomes of a repository's checkout once its adoption is over.
 *
 * <p>A checkout is a full clone, and a run over a list of repositories makes one
 * per repository. Nothing removed them, so a fifty-repository batch left fifty
 * clones behind — under a temporary directory the operator never named and would
 * not think to look in. The adoption's own product is a pushed branch and a pull
 * request; the checkout is scaffolding.
 *
 * <p>It is scaffolding worth keeping in exactly two cases, which is why this is a
 * choice rather than an unconditional delete. A checkout whose adoption
 * <em>failed</em> is the only record of how far the run got, and reading it is the
 * first thing an operator does. A dry run's checkout is the whole point of the
 * dry run: it commits the adoption locally and pushes nothing, so deleting it
 * would leave the operator with nothing to inspect.
 */
public enum CheckoutRetention {

	/** Keep every checkout, whatever happened: a dry run, or {@code --keep-workspace}. */
	ALWAYS,

	/**
	 * Keep only the checkouts whose adoption failed, which are the ones with
	 * something left to say.
	 */
	ON_FAILURE;

	private static final Logger log = LogManager.getLogger(CheckoutRetention.class);

	/**
	 * @param keepWorkspace whether the operator asked for every checkout to be kept
	 * @param dryRun        whether the run pushes nothing, in which case the checkout
	 *                      is the only thing it produced
	 */
	public static CheckoutRetention of(boolean keepWorkspace, boolean dryRun) {
		return keepWorkspace || dryRun ? ALWAYS : ON_FAILURE;
	}

	/**
	 * Removes the checkout when this policy says to, and never fails the run for it:
	 * an adoption that succeeded has succeeded, and a directory that could not be
	 * removed is a warning rather than a reason to report the repository as failed.
	 *
	 * @param checkout  the directory to remove
	 * @param succeeded whether the adoption of that repository landed
	 */
	public void apply(Path checkout, boolean succeeded) {
		if (this == ALWAYS || !succeeded) {
			log.debug("Leaving the checkout {} in place", checkout);
			return;
		}
		remove(checkout);
	}

	private void remove(Path checkout) {
		try {
			deleteRecursively(checkout);
			log.info("Removed the checkout {}", checkout);
		} catch (IOException | RuntimeException e) {
			log.warn("Could not remove the checkout {}: {}", checkout, e.getMessage());
		}
	}

	/**
	 * Deepest entries first, because a directory is only removable once it is empty.
	 * An absent checkout is nothing to remove — a run that failed before its clone
	 * never made one.
	 */
	private static void deleteRecursively(Path checkout) throws IOException {
		if (!Files.exists(checkout)) {
			return;
		}
		try (Stream<Path> entries = Files.walk(checkout)) {
			for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(entry);
			}
		}
	}
}
