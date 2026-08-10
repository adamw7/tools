package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class PullRequestOptionsTest {

	@Test
	void defaultsCarryTheAdoptionTitleAndRequestNobody() {
		PullRequestOptions options = PullRequestOptions.defaults();
		assertEquals(PullRequestOptions.DEFAULT_TITLE, options.title());
		assertEquals(PullRequestOptions.DEFAULT_BODY, options.body());
		assertTrue(options.reviewers().isEmpty());
		assertTrue(options.labels().isEmpty());
		assertTrue(options.assignees().isEmpty());
		assertFalse(options.draft());
	}

	@Test
	void carriesEverySuppliedField() {
		PullRequestOptions options = new PullRequestOptions("Title", "Body", List.of("octocat"),
				List.of("automation"), List.of("adamw7"), true);
		assertEquals("Title", options.title());
		assertEquals("Body", options.body());
		assertEquals(List.of("octocat"), options.reviewers());
		assertEquals(List.of("automation"), options.labels());
		assertEquals(List.of("adamw7"), options.assignees());
		assertTrue(options.draft());
	}

	/**
	 * The record documents its lists as defensively copied and never {@code null}, so
	 * an absent one is read as naming nobody — the answer a blank title already gets.
	 * A {@code null} used to come back out as a message-less
	 * {@link NullPointerException} from the very constructor making that promise.
	 */
	@Test
	void readsAnAbsentListAsNamingNobody() {
		PullRequestOptions options = new PullRequestOptions("Title", "Body", null, null, null, false);
		assertTrue(options.reviewers().isEmpty());
		assertTrue(options.labels().isEmpty());
		assertTrue(options.assignees().isEmpty());
	}

	@Test
	void trimsTitleAndBody() {
		PullRequestOptions options = options("  Title  ", "  Body  ");
		assertEquals("Title", options.title());
		assertEquals("Body", options.body());
	}

	/**
	 * The command line and the MCP tool both map an optional input straight in, so an
	 * omitted — or explicitly blanked — title or body has to leave the adoption
	 * default in place rather than reach {@code gh} as nothing at all.
	 */
	@Test
	void keepsTheDefaultsWhenNoTitleOrBodyWasSupplied() {
		assertEquals(PullRequestOptions.DEFAULT_TITLE, options(null, "   ").title());
		assertEquals(PullRequestOptions.DEFAULT_BODY, options(null, "   ").body());
		assertEquals(PullRequestOptions.DEFAULT_TITLE, options(" ", null).title());
		assertEquals(PullRequestOptions.DEFAULT_BODY, options(" ", null).body());
	}

	@Test
	void defensivelyCopiesReviewers() {
		List<String> reviewers = new ArrayList<>(List.of("octocat"));
		PullRequestOptions options = new PullRequestOptions(null, null, reviewers, List.of(), List.of(), false);
		reviewers.add("hubot");
		assertEquals(List.of("octocat"), options.reviewers());
	}

	/**
	 * Every list entry becomes an argument of its own — {@code gh pr create
	 * --reviewer <entry>} — so a blank one reached {@code gh} as an empty argument
	 * and failed the adoption at its last step, with the branch already pushed. The
	 * command line adds what it was given without reading it, so the rule belongs
	 * here, where the MCP tool's own stripping already agreed with it.
	 */
	@Test
	void dropsBlankListEntriesSoNoneReachesGhAsAnEmptyArgument() {
		PullRequestOptions options = new PullRequestOptions(null, null, List.of("", "octocat"),
				List.of("   ", "automation"), List.of("\t", "adamw7"), false);
		assertEquals(List.of("octocat"), options.reviewers());
		assertEquals(List.of("automation"), options.labels());
		assertEquals(List.of("adamw7"), options.assignees());
	}

	@Test
	void stripsListEntries() {
		PullRequestOptions options = new PullRequestOptions(null, null, List.of("  octocat  "),
				List.of("  automation  "), List.of("  adamw7  "), false);
		assertEquals(List.of("octocat"), options.reviewers());
		assertEquals(List.of("automation"), options.labels());
		assertEquals(List.of("adamw7"), options.assignees());
	}

	/** A list of nothing but blanks requests nobody, exactly as an omitted one does. */
	@Test
	void requestsNobodyWhenEveryEntryIsBlank() {
		PullRequestOptions options = new PullRequestOptions(null, null, List.of(" "), List.of(""),
				List.of("  "), false);
		assertTrue(options.reviewers().isEmpty());
		assertTrue(options.labels().isEmpty());
		assertTrue(options.assignees().isEmpty());
	}

	/**
	 * {@code gh} treats naming a reviewer twice as naming them once, so a repeated
	 * {@code --reviewer} is left alone rather than being second-guessed here.
	 */
	@Test
	void keepsRepeatedEntriesAsTheyWereGiven() {
		PullRequestOptions options = new PullRequestOptions(null, null, List.of("octocat", "octocat"),
				List.of(), List.of(), false);
		assertEquals(List.of("octocat", "octocat"), options.reviewers());
	}

	private PullRequestOptions options(String title, String body) {
		return new PullRequestOptions(title, body, List.of(), List.of(), List.of(), false);
	}
}
