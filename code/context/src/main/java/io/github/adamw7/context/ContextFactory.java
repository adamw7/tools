package io.github.adamw7.context;

import java.util.Set;

/**
 * Creates a {@link Context} over a set of containers. Decoupling the
 * {@link ProjectTreeBuilder} from a concrete {@link Context} keeps the builder
 * open for extension (e.g. a different dependency finder) without modification.
 */
@FunctionalInterface
public interface ContextFactory {
	Context create(Set<ClassContainer> allContainers);
}
