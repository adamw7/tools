package io.github.adamw7.tools.data.uniqueness;

import java.util.Arrays;

public record Key(String[] values) {

	@Override
	public String toString() {
		return Arrays.toString(values);
	}

	/**
	 * A record compares and hashes its array component by identity, which would make
	 * two keys holding equal values distinct; both are answered by content instead, so
	 * a key works as a map key and a set element.
	 */
	@Override
	public int hashCode() {
		return Arrays.hashCode(values);
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof Key other && Arrays.equals(values, other.values);
	}
}
