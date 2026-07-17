package io.github.adamw7.tools.adopt.command;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Platform-appropriate command lines for the {@link ProcessCommandRunner}
 * tests. {@link ProcessBuilder} starts a program directly rather than through a
 * shell, so the POSIX commands these tests need are not startable on Windows:
 * {@code echo} is a shell builtin there rather than an executable, and
 * {@code sh} and {@code sleep} exist only if a POSIX toolchain happens to be on
 * the {@code PATH}. Each factory below yields a command with the same
 * observable behaviour — output, exit code, duration — on either platform, so
 * the tests assert on {@link ProcessCommandRunner} instead of on what the
 * developer's {@code PATH} contains.
 */
final class PlatformCommands {

	private static final boolean WINDOWS = System.getProperty("os.name", "")
			.toLowerCase(Locale.ROOT).startsWith("windows");

	private PlatformCommands() {
	}

	/** A command that prints {@code text} to standard output and succeeds. */
	static List<String> printing(String text) {
		return WINDOWS ? cmd("echo", text) : List.of("echo", text);
	}

	/** A command that terminates with {@code exitCode}. */
	static List<String> exitingWith(int exitCode) {
		return WINDOWS ? cmd("exit", String.valueOf(exitCode)) : sh("exit " + exitCode);
	}

	/** A command that prints {@code text} to standard error. */
	static List<String> printingToStandardError(String text) {
		return WINDOWS ? cmd("echo", text, "1>&2") : sh("echo " + text + " 1>&2");
	}

	/** A command that stays alive for at least {@code seconds}. */
	static List<String> sleepingFor(int seconds) {
		return WINDOWS ? ping(seconds) : List.of("sleep", String.valueOf(seconds));
	}

	/**
	 * Windows has no {@code sleep} executable, and {@code timeout} refuses to run
	 * with redirected input, which is exactly how a child of
	 * {@link ProcessBuilder} is started. Pinging the loopback address once a
	 * second is the portable stand-in.
	 */
	private static List<String> ping(int seconds) {
		return List.of("ping", "-n", String.valueOf(seconds + 1), "127.0.0.1");
	}

	/**
	 * Each token is passed separately so no argument contains a space: Java would
	 * otherwise quote it, and {@code cmd.exe} applies its own quoting rules to
	 * whatever follows {@code /c}.
	 */
	private static List<String> cmd(String... arguments) {
		return Stream.concat(Stream.of("cmd.exe", "/c"), Stream.of(arguments)).toList();
	}

	private static List<String> sh(String script) {
		return List.of("sh", "-c", script);
	}
}
