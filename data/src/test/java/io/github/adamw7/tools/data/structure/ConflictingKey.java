package io.github.adamw7.tools.data.structure;

/**
 * A key whose hash collides with every other one, so a map test can drive the
 * probe chain — insertion past a taken slot, and a live entry found past a
 * tombstone — rather than hope a real hash produces the collision.
 */
public record ConflictingKey(int number, String string) {

	@Override
	public int hashCode() {
		return 1; // conflicting hash
	}
}
