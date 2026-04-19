package io.github.adamw7.tools.data.uniqueness;

import java.util.Arrays;

public record Key(String[] values) {

	public Key {
		values = values == null ? new String[0] : values.clone();
	}

	@Override
	public String[] values() {
		return values.clone();
	}

	@Override
	public String toString() {
		return Arrays.toString(values);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(values);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof Key other)) {
			return false;
		}
		return Arrays.equals(values, other.values);
	}
}
