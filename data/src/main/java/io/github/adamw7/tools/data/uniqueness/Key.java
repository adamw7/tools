package io.github.adamw7.tools.data.uniqueness;

import java.util.Arrays;

public record Key(String[] values) {

	@Override
	public String toString() {
		return Arrays.toString(values);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(values);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Key other = (Key) obj;
		return Arrays.equals(values, other.values);
	}
	
	
}
