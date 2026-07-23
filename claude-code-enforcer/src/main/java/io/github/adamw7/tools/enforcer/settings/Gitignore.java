package io.github.adamw7.tools.enforcer.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A parsed {@code .gitignore}, able to answer whether a repository-relative
 * file path is ignored. It implements the subset of gitignore semantics the
 * {@link LocalSettingsIgnoredRule} needs: comments and blank lines are skipped,
 * {@code !} negates, a trailing {@code /} restricts a pattern to directories, a
 * pattern containing a {@code /} is anchored to the gitignore's directory while
 * one without matches at any depth, and {@code *}, {@code ?}, and {@code **}
 * glob within and across path segments respectively. Later lines override
 * earlier ones, and a file is also ignored when any of its ancestor directories
 * is.
 * <p>
 * Paths are matched with {@code /} separators and no leading slash, exactly as
 * they appear in {@code git status} output.
 */
final class Gitignore {

	private final List<Line> lines;

	private Gitignore(List<Line> lines) {
		this.lines = lines;
	}

	static Gitignore parse(String content) {
		List<Line> lines = new ArrayList<>();
		for (String raw : content.lines().toList()) {
			parseLine(raw.strip(), lines);
		}
		return new Gitignore(lines);
	}

	private static void parseLine(String line, List<Line> lines) {
		if (line.isEmpty() || line.startsWith("#")) {
			return;
		}
		boolean negated = line.startsWith("!");
		String pattern = negated ? line.substring(1) : line;
		boolean directoryOnly = pattern.endsWith("/");
		String body = directoryOnly ? pattern.substring(0, pattern.length() - 1) : pattern;
		lines.add(new Line(negated, directoryOnly, compile(body)));
	}

	private static Pattern compile(String body) {
		boolean anchored = body.startsWith("/") || body.indexOf('/') >= 0;
		String stripped = body.startsWith("/") ? body.substring(1) : body;
		String prefix = anchored ? "^" : "^(?:.*/)?";
		return Pattern.compile(prefix + translate(stripped) + "$");
	}

	/** Translates one gitignore glob into a regular expression body. */
	private static String translate(String glob) {
		StringBuilder regex = new StringBuilder();
		int i = 0;
		while (i < glob.length()) {
			i += appendToken(glob, i, regex);
		}
		return regex.toString();
	}

	private static int appendToken(String glob, int i, StringBuilder regex) {
		if (glob.startsWith("**/", i)) {
			regex.append("(?:.*/)?");
			return 3;
		}
		if (glob.startsWith("**", i)) {
			regex.append(".*");
			return 2;
		}
		return appendCharacter(glob.charAt(i), regex);
	}

	private static int appendCharacter(char c, StringBuilder regex) {
		if (c == '*') {
			regex.append("[^/]*");
		} else if (c == '?') {
			regex.append("[^/]");
		} else {
			regex.append(Pattern.quote(String.valueOf(c)));
		}
		return 1;
	}

	/**
	 * True when the file at {@code path} is ignored, either directly or because
	 * one of its ancestor directories is.
	 */
	boolean covers(String path) {
		if (isIgnored(path, false)) {
			return true;
		}
		return ancestorsOf(path).stream().anyMatch(ancestor -> isIgnored(ancestor, true));
	}

	/** The verdict of the last matching line, honouring negations; directory-only lines match directories alone. */
	private boolean isIgnored(String path, boolean isDirectory) {
		boolean ignored = false;
		for (Line line : lines) {
			ignored = line.verdict(path, isDirectory, ignored);
		}
		return ignored;
	}

	private List<String> ancestorsOf(String path) {
		List<String> ancestors = new ArrayList<>();
		int slash = path.indexOf('/');
		while (slash >= 0) {
			ancestors.add(path.substring(0, slash));
			slash = path.indexOf('/', slash + 1);
		}
		return ancestors;
	}

	private record Line(boolean negated, boolean directoryOnly, Pattern pattern) {

		boolean verdict(String path, boolean isDirectory, boolean current) {
			if (matches(path, isDirectory)) {
				return !negated;
			}
			return current;
		}

		private boolean matches(String path, boolean isDirectory) {
			if (directoryOnly && !isDirectory) {
				return false;
			}
			return pattern.matcher(path).matches();
		}
	}
}
