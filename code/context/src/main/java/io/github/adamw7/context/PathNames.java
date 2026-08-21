package io.github.adamw7.context;

import java.nio.file.Path;

/**
 * The name a path is recognised, loaded and ordered by. {@link Path#getFileName}
 * answers {@code null} for a path with no name element — a filesystem root such as
 * {@code /} or {@code C:\} — so a project walked from one, or a listing that reaches
 * one, would otherwise fail on it with a {@link NullPointerException} rather than
 * simply finding no sources there.
 */
public final class PathNames {

	private PathNames() {
	}

	/** The path's last element, or the whole path when it has no name of its own. */
	public static String of(Path path) {
		Path name = path.getFileName();
		return name != null ? name.toString() : path.toString();
	}
}
