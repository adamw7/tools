package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What becomes of a checkout once its adoption is over. A batch makes one full
 * clone per repository, so the default matters: fifty repositories used to leave
 * fifty clones behind, under a temporary directory nobody named.
 */
class CheckoutRetentionTest {

	@TempDir
	private Path workspace;

	@Test
	void removesTheCheckoutOfAnAdoptionThatLanded() throws IOException {
		Path checkout = checkoutWithContent();

		CheckoutRetention.ON_FAILURE.apply(checkout, true);

		assertFalse(Files.exists(checkout), "a successful adoption's checkout is scaffolding");
	}

	/** The only record of how far a failed run got, and the first thing an operator reads. */
	@Test
	void keepsTheCheckoutOfAnAdoptionThatFailed() throws IOException {
		Path checkout = checkoutWithContent();

		CheckoutRetention.ON_FAILURE.apply(checkout, false);

		assertTrue(Files.exists(checkout));
	}

	@Test
	void keepsEveryCheckoutWhenAskedTo() throws IOException {
		Path checkout = checkoutWithContent();

		CheckoutRetention.ALWAYS.apply(checkout, true);

		assertTrue(Files.exists(checkout));
	}

	/** A directory is only removable once it is empty, so the walk goes deepest first. */
	@Test
	void removesANestedCheckoutWhole() throws IOException {
		Path checkout = checkoutWithContent();
		Files.createDirectories(checkout.resolve(".git/refs/heads"));
		Files.writeString(checkout.resolve(".git/refs/heads/main"), "ref");
		Files.writeString(checkout.resolve(".git/config"), "[core]");

		CheckoutRetention.ON_FAILURE.apply(checkout, true);

		assertFalse(Files.exists(checkout));
	}

	/** A run that failed before its clone never made a checkout; that is nothing to remove. */
	@Test
	void toleratesACheckoutThatWasNeverMade() {
		assertDoesNotThrow(() -> CheckoutRetention.ON_FAILURE.apply(workspace.resolve("absent"), true));
	}

	/**
	 * An adoption that succeeded has succeeded. A checkout that could not be removed
	 * is a warning, not a reason to report the repository as failed.
	 */
	@Test
	void neverFailsTheRunOverACheckoutItCouldNotRemove() throws IOException {
		Path checkout = checkoutWithContent();
		Path unreadable = Files.createDirectory(checkout.resolve("locked"));
		Files.writeString(unreadable.resolve("file"), "x");
		unreadable.toFile().setReadable(false);

		assertDoesNotThrow(() -> CheckoutRetention.ON_FAILURE.apply(checkout, true));

		unreadable.toFile().setReadable(true);
	}

	@Test
	void aDryRunKeepsItsCheckoutBecauseItIsAllTheRunProduced() {
		assertEquals(CheckoutRetention.ALWAYS, CheckoutRetention.of(false, true));
	}

	@Test
	void keepWorkspaceKeepsEveryCheckout() {
		assertEquals(CheckoutRetention.ALWAYS, CheckoutRetention.of(true, false));
	}

	@Test
	void anOrdinaryRunKeepsOnlyWhatFailed() {
		assertEquals(CheckoutRetention.ON_FAILURE, CheckoutRetention.of(false, false));
	}

	private Path checkoutWithContent() throws IOException {
		Path checkout = Files.createDirectories(workspace.resolve("repo"));
		Files.writeString(checkout.resolve("CLAUDE.md"), "# CLAUDE.md");
		return checkout;
	}
}
