package io.github.adamw7.tools.adopt;

import java.util.Locale;

/**
 * The host operating system, as the adoption needs to read it. Windows is the one
 * platform the adoption treats differently — a build wrapper is {@code mvnw.cmd}
 * rather than {@code mvnw} there, and a bare program name has to be resolved
 * through {@code PATHEXT} before {@link ProcessBuilder} will start it — so the
 * detection lives here rather than being spelled out by each place that branches
 * on it.
 */
public final class Platform {

	private Platform() {
	}

	/** @return whether the JVM is running on Windows */
	public static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
	}
}
