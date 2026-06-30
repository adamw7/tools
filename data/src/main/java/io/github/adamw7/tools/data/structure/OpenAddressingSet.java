package io.github.adamw7.tools.data.structure;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;

/**
 * A {@link Set} backed by an {@link OpenAddressingMap}, in the same way that
 * {@link java.util.HashSet} is backed by a {@link java.util.HashMap}. Elements
 * are stored as keys of the underlying map against a shared sentinel value, so
 * all of the open-addressing behaviour (double hashing, tombstone removal and
 * automatic resizing) is reused rather than re-implemented.
 *
 * <p>Like the underlying map this set does not support {@code null} elements
 * (they are rejected with {@link IllegalArgumentException}) and is not
 * thread-safe.
 */
public class OpenAddressingSet<E> extends AbstractSet<E> implements Set<E> {

	private static final Object PRESENT = new Object();

	private final OpenAddressingMap<E, Object> map;

	public OpenAddressingSet() {
		map = new OpenAddressingMap<>();
	}

	public OpenAddressingSet(int size) {
		map = new OpenAddressingMap<>(size);
	}

	@Override
	public int size() {
		return map.size();
	}

	@Override
	public boolean isEmpty() {
		return map.isEmpty();
	}

	@Override
	public boolean contains(Object element) {
		return map.containsKey(element);
	}

	@Override
	public boolean add(E element) {
		return map.put(element, PRESENT) == null;
	}

	@Override
	public boolean remove(Object element) {
		return map.remove(element) != null;
	}

	@Override
	public void clear() {
		map.clear();
	}

	@Override
	public Iterator<E> iterator() {
		return map.keySet().iterator();
	}
}
