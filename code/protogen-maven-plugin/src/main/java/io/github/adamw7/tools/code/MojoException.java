package io.github.adamw7.tools.code;

/**
 * The generator's own failure, thrown by the packages that do the work so they
 * stay free of the Maven API. {@link CodeMojo} is the only place it is meant to
 * reach: it translates every failure into a
 * {@link org.apache.maven.plugin.MojoExecutionException}, so a mistake in a
 * consumer's pom is reported as an attributed build failure rather than as an
 * escaping runtime exception Maven blames on the plugin.
 */
public class MojoException extends RuntimeException {

	public MojoException(String message, Exception cause) {
		super(message, cause);
	}

}
