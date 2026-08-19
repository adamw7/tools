package io.github.adamw7.tools.adopt.step;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import io.github.adamw7.tools.adopt.AdoptionException;

/**
 * The versions this build pins, read from the metadata Maven filters into
 * {@value #BUILD_PROPERTIES} at build time.
 *
 * <p>Both versions the adoption writes into somebody else's {@code pom.xml} come
 * from here rather than from literals in the code: the {@code claude-code-enforcer}
 * rule, pinned to the exact {@code tools} release running the adoption so it
 * resolves from the repository that published it, and the
 * {@code maven-enforcer-plugin}, pinned to the version this project builds
 * against. A literal would drift, and the half that drifts is the one nobody
 * builds — a stranger's repository, adopted with a version this project stopped
 * using.
 */
final class BuildMetadata {

	static final String BUILD_PROPERTIES = "/adopt-build.properties";

	private BuildMetadata() {
	}

	/**
	 * @param key         the property to read
	 * @param description what the value is for, so a failure says what is missing
	 *                    rather than only which key
	 */
	static String value(String key, String description) {
		try (InputStream stream = BuildMetadata.class.getResourceAsStream(BUILD_PROPERTIES)) {
			return read(stream, key, description);
		} catch (IOException e) {
			throw new AdoptionException("Could not read build metadata: " + BUILD_PROPERTIES, e);
		}
	}

	/**
	 * Refuses metadata that reached the classpath unfiltered as firmly as metadata
	 * that is missing: an unsubstituted token would otherwise be wired into the
	 * adopted POM as if it were a version. Both delimiters are rejected — the
	 * resource is written with {@code @...@}, which is what this build filters, and
	 * {@code ${...}} is what it would carry had that configuration changed.
	 *
	 * <p>Package-visible because the stream is the only seam these refusals have.
	 * {@link #value} reads one resource off this class's own classpath, and that
	 * resource is correct in every build that runs these tests — so a test driving a
	 * version through it can only ever take the path that succeeds, leaving the
	 * failures this method words for nobody to reach. They are the failures worth
	 * reaching: each one is what stands between a broken build of {@code tools} and a
	 * literal {@code @enforcer.rule.version@} wired into a stranger's {@code pom.xml}
	 * by a pull request the adoption opened.
	 */
	static String read(InputStream stream, String key, String description) throws IOException {
		if (stream == null) {
			throw new AdoptionException("Build metadata not on the classpath: " + BUILD_PROPERTIES
					+ " (build the module so its resources are filtered)");
		}
		Properties properties = new Properties();
		properties.load(stream);
		String value = properties.getProperty(key, "").strip();
		if (value.isEmpty() || value.startsWith("@") || value.startsWith("${")) {
			throw new AdoptionException("Build metadata " + BUILD_PROPERTIES + " carries no usable " + key
					+ " (" + description + ") but '" + value + "'; the module's resources were not filtered.");
		}
		return value;
	}
}
