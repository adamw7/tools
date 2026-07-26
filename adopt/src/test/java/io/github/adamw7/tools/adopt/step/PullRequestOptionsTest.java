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

	private PullRequestOptions options(String title, String body) {
		return new PullRequestOptions(title, body, List.of(), List.of(), List.of(), false);
	}
}
