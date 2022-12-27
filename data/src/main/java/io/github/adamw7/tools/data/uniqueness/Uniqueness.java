package io.github.adamw7.tools.data.uniqueness;

public interface Uniqueness {

	Result exec(String... keyCandidates) throws Exception;

}