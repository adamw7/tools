package io.github.adamw7.tools.adopt.step;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
	void builderSetsEveryField() {
		PullRequestOptions options = PullRequestOptions.builder()
				.title("Title")
				.body("Body")
				.reviewers(List.of("octocat"))
				.labels(List.of("automation"))
				.assignees(List.of("adamw7"))
				.draft(true)
				.build();
		assertEquals("Title", options.title());
		assertEquals("Body", options.body());
		assertEquals(List.of("octocat"), options.reviewers());
		assertEquals(List.of("automation"), options.labels());
		assertEquals(List.of("adamw7"), options.assignees());
		assertTrue(options.draft());
	}

	@Test
	void trimsTitleAndBody() {
		PullRequestOptions options = PullRequestOptions.builder().title("  Title  ").body("  Body  ").build();
		assertEquals("Title", options.title());
		assertEquals("Body", options.body());
	}

	@Test
	void rejectsBlankTitle() {
		PullRequestOptions.Builder builder = PullRequestOptions.builder().title(" ");
		assertThrows(IllegalArgumentException.class, builder::build);
	}

	@Test
	void rejectsBlankBody() {
		PullRequestOptions.Builder builder = PullRequestOptions.builder().body("");
		assertThrows(IllegalArgumentException.class, builder::build);
	}

	/** A caller clearing a field rather than leaving the default must not slip a null into gh's arguments. */
	@Test
	void rejectsANullTitleOrBody() {
		PullRequestOptions.Builder withoutTitle = PullRequestOptions.builder().title(null);
		assertThrows(IllegalArgumentException.class, withoutTitle::build);
		PullRequestOptions.Builder withoutBody = PullRequestOptions.builder().body(null);
		assertThrows(IllegalArgumentException.class, withoutBody::build);
	}

	/**
	 * The command line and the MCP tool both map an optional input straight into the
	 * builder, so an omitted title or body has to leave the adoption default in place
	 * rather than blank out a field the record refuses.
	 */
	@Test
	void keepsTheDefaultsWhenNoTitleOrBodyWasSupplied() {
		PullRequestOptions options = PullRequestOptions.builder()
				.titleIfPresent(null)
				.bodyIfPresent("   ")
				.build();
		assertEquals(PullRequestOptions.DEFAULT_TITLE, options.title());
		assertEquals(PullRequestOptions.DEFAULT_BODY, options.body());
	}

	@Test
	void suppliedTitleAndBodyOverrideTheDefaults() {
		PullRequestOptions options = PullRequestOptions.builder()
				.titleIfPresent("Title")
				.bodyIfPresent("Body")
				.build();
		assertEquals("Title", options.title());
		assertEquals("Body", options.body());
	}

	@Test
	void defensivelyCopiesReviewers() {
		List<String> reviewers = new ArrayList<>(List.of("octocat"));
		PullRequestOptions options = PullRequestOptions.builder().reviewers(reviewers).build();
		reviewers.add("hubot");
		assertEquals(List.of("octocat"), options.reviewers());
	}
}
