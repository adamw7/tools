package io.github.adamw7.context;

import java.util.Set;

public interface Context {
    Set<ClassContainer> find(ClassContainer root, int depth);
}
