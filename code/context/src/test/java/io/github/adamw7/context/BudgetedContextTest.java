package io.github.adamw7.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

public class BudgetedContextTest {

	private final TokenEstimator oneTokenPerCharacter = text -> text.length();

	@Test
	void keepsContainersWhileTheyFitTheBudget() {
		Context delegate = orderedDelegate(container("A", "xx"), container("B", "xx"), container("C", "xx"));
		BudgetedContext budgeted = new BudgetedContext(delegate, oneTokenPerCharacter, 4);

		Set<ClassContainer> result = budgeted.find(any(), 1);

		assertEquals(names("A", "B"), classNames(result));
	}

	@Test
	void stopsAtTheFirstContainerThatDoesNotFit() {
		Context delegate = orderedDelegate(container("A", "x"), container("B", "xxxxx"), container("C", "x"));
		BudgetedContext budgeted = new BudgetedContext(delegate, oneTokenPerCharacter, 3);

		Set<ClassContainer> result = budgeted.find(any(), 1);

		assertEquals(names("A"), classNames(result),
				"a later small container must not jump ahead of an earlier oversized one");
	}

	@Test
	void preservesTheDelegateOrder() {
		Context delegate = orderedDelegate(container("A", "x"), container("B", "x"), container("C", "x"));
		BudgetedContext budgeted = new BudgetedContext(delegate, oneTokenPerCharacter, 100);

		List<String> ordered = new ArrayList<>();
		budgeted.find(any(), 1).forEach(container -> ordered.add(container.className()));

		assertEquals(List.of("A", "B", "C"), ordered);
	}

	@Test
	void returnsEmptyWhenEvenTheFirstContainerExceedsTheBudget() {
		Context delegate = orderedDelegate(container("A", "xxxxx"));
		BudgetedContext budgeted = new BudgetedContext(delegate, oneTokenPerCharacter, 2);

		assertTrue(budgeted.find(any(), 1).isEmpty());
	}

	@Test
	void rejectsNullDelegateNullEstimatorAndNonPositiveBudget() {
		Context delegate = orderedDelegate();

		assertThrows(IllegalArgumentException.class,
				() -> new BudgetedContext(null, oneTokenPerCharacter, 1));
		assertThrows(IllegalArgumentException.class,
				() -> new BudgetedContext(delegate, null, 1));
		assertThrows(IllegalArgumentException.class,
				() -> new BudgetedContext(delegate, oneTokenPerCharacter, 0));
	}

	private Context orderedDelegate(ClassContainer... containers) {
		Set<ClassContainer> ordered = new LinkedHashSet<>(List.of(containers));
		return (root, depth) -> ordered;
	}

	private ClassContainer container(String name, String code) {
		return new ClassContainer(name, code);
	}

	private ClassContainer any() {
		return new ClassContainer("Root", "");
	}

	private Set<String> classNames(Set<ClassContainer> containers) {
		Set<String> names = new LinkedHashSet<>();
		containers.forEach(container -> names.add(container.className()));
		return names;
	}

	private Set<String> names(String... names) {
		return new LinkedHashSet<>(List.of(names));
	}
}
