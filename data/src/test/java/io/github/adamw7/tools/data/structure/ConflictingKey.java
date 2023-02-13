package io.github.adamw7.tools.data.structure;

import java.util.Objects;

public class ConflictingKey {

	private final int number;
	private final String string;
	
	public ConflictingKey(int number, String string) {
		this.number = number;
		this.string = string;
	}
	
	@Override
	public int hashCode() {
		return 1; // conflicting hash
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ConflictingKey other = (ConflictingKey) obj;
		return number == other.number && Objects.equals(string, other.string);
	}
}
