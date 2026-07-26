package io.github.adamw7.tools.adopt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

class FailuresTest {

	@Test
	void runsTheCleanUp() {
		StringBuilder cleanedUp = new StringBuilder();
		Failures.alsoRun(new AdoptionException("boom"), () -> cleanedUp.append("done"));
		assertEquals("done", cleanedUp.toString());
	}

	/**
	 * The adoption failure carries the diagnostic the operator needs, so a clean-up
	 * that fails on its way out must be attached to it rather than thrown over it.
	 */
	@Test
	void attachesACleanUpFailureToTheOneBeingReported() {
		AdoptionException failure = new AdoptionException("boom");
		Failures.alsoRun(failure, () -> {
			throw new IllegalStateException("could not write the report");
		});
		assertEquals(1, failure.getSuppressed().length);
		assertEquals("could not write the report", failure.getSuppressed()[0].getMessage());
	}

	@Test
	void describesAFailureByItsMessage() {
		assertEquals("boom", Failures.describe(new AdoptionException("boom")));
	}

	/**
	 * A report that recorded the empty string as the reason a run stopped would say
	 * the run failed without saying anything about why.
	 */
	@Test
	void describesAFailureWithABlankMessageByItsType() {
		assertEquals("AdoptionException", Failures.describe(new AdoptionException("   ")));
	}

	@Test
	void describesAFailureWithNoMessageByItsType() {
		assertEquals("IllegalStateException", Failures.describe(new IllegalStateException()));
	}

	@Test
	void isAUtilityClassNobodyInstantiates() throws NoSuchMethodException {
		Constructor<Failures> constructor = Failures.class.getDeclaredConstructor();
		assertTrue(Modifier.isPrivate(constructor.getModifiers()),
				"a class of static helpers must not be instantiable");
	}
}
