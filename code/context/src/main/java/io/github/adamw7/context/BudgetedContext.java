package io.github.adamw7.context;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A {@link Context} decorator that trims a delegate's result to fit a token
 * budget. The delegate (for example a {@link Finder}) yields its dependencies in
 * breadth-first order — closest dependencies first — so this decorator keeps that
 * order and accepts containers until the next one would push the estimated token
 * total past the {@code budget}. The first container that does not fit stops the
 * assembly, preserving the highest-priority prefix of the dependency graph.
 */
public class BudgetedContext implements Context {

	private final Context delegate;
	private final TokenEstimator estimator;
	private final int budget;

	public BudgetedContext(Context delegate, TokenEstimator estimator, int budget) {
		requireNonNull(delegate, "delegate");
		requireNonNull(estimator, "estimator");
		requirePositiveBudget(budget);
		this.delegate = delegate;
		this.estimator = estimator;
		this.budget = budget;
	}

	private void requireNonNull(Object value, String name) {
		if (value == null) {
			throw new IllegalArgumentException(name + " must not be null");
		}
	}

	private void requirePositiveBudget(int budget) {
		if (budget <= 0) {
			throw new IllegalArgumentException("budget must be positive and received: " + budget);
		}
	}

	@Override
	public Set<ClassContainer> find(ClassContainer root, int depth) {
		Set<ClassContainer> all = delegate.find(root, depth);
		Set<ClassContainer> withinBudget = new LinkedHashSet<>();
		fill(all.iterator(), budget, withinBudget);
		return withinBudget;
	}

	private void fill(Iterator<ClassContainer> candidates, int remaining, Set<ClassContainer> accepted) {
		if (!candidates.hasNext()) {
			return;
		}
		ClassContainer candidate = candidates.next();
		int cost = estimator.estimate(candidate.originalCode());
		if (cost > remaining) {
			return;
		}
		accepted.add(candidate);
		fill(candidates, remaining - cost, accepted);
	}
}
