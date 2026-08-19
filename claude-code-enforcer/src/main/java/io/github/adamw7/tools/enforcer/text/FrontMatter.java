package io.github.adamw7.tools.enforcer.text;

import java.io.StringReader;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

/**
 * The YAML front matter block at the top of a Markdown document: the lines between
 * a leading {@code ---} delimiter and the next.
 * <p>
 * The block is read by SnakeYAML, the same kind of loader Claude Code reads front
 * matter with, so what a rule validates is what the tool acts on. Parsing stops at
 * {@link Yaml#compose}, which answers the node tree rather than constructing Java
 * objects: a rule needs the text an author declared, not a typed value. Composing
 * keeps {@code okf_version: 0.20} the string {@code 0.20} instead of rounding it,
 * and keeps a duplicated key visible, which every loader building a {@code Map}
 * collapses.
 * <p>
 * Each entry is answered as a single line of text, which is all the rules need:
 * they check a value's length, blankness, uniqueness or shape, never its structure.
 * A value written across several lines is folded into one joined by single spaces,
 * the alternative being to answer the empty string for a value written out below
 * its key. Folding a mapping is lossy on purpose — {@code by: agent/1} reads back
 * as text; a rule needing the structure should ask SnakeYAML directly.
 * <p>
 * A block no loader can read is no front matter at all, and {@link #parse} answers
 * empty for it: reading it as text validated something Claude Code never sees,
 * since its loader fails on that block too. A block that reads but declares no
 * entries — the {@code name:value} lacking the space a YAML entry needs — is
 * present and simply declares nothing.
 * <p>
 * A key declared twice yields its <em>last</em> declaration, the one a YAML loader
 * keeps and so the one Claude Code acts on; reading the first validated a value the
 * tool never sees. {@link #duplicateKeys()} reports the duplication itself, a key
 * written twice being a mistake whichever value wins.
 */
public final class FrontMatter {

	private static final String DELIMITER = "---";
	private static final char KEY_VALUE_SEPARATOR = ':';

	/** How much of one entry is rendered before the walk gives up. See {@link #folded}. */
	private static final int MAX_VALUE_LENGTH = 8192;

	/** How deep the walk follows a node before it gives up. See {@link #folded}. */
	private static final int MAX_DEPTH = 64;

	private final List<NodeTuple> entries;

	private FrontMatter(List<NodeTuple> entries) {
		this.entries = entries;
	}

	/**
	 * Parses the front matter at the start of {@code content}, or empty when it does
	 * not begin with a closed {@code ---} block. Claude Code only recognises a block
	 * whose opening delimiter is the very first line, so content reaching its
	 * {@code ---} after blank lines has none here either. Any byte-order mark must
	 * already be stripped by the caller.
	 */
	public static Optional<FrontMatter> parse(String content) {
		List<String> allLines = content.lines().toList();
		if (allLines.isEmpty() || !allLines.get(0).strip().equals(DELIMITER)) {
			return Optional.empty();
		}
		int end = indexOfDelimiter(allLines, 1);
		if (end < 0) {
			return Optional.empty();
		}
		return entriesOf(allLines.subList(1, end)).map(FrontMatter::new);
	}

	/** True when a {@code key:} entry is present, regardless of its value. */
	public boolean hasKey(String key) {
		return keys().contains(key);
	}

	/**
	 * The trimmed value declared for {@code key}, or empty when the key is absent.
	 * A present key whose value is neither on its own line nor below it yields an
	 * empty string, not an empty optional; a value continued on the lines below —
	 * a block scalar, a wrapped plain scalar, or a nested mapping — yields those
	 * lines folded into one. A key declared more than once yields its last
	 * declaration, the one a YAML loader keeps.
	 */
	public Optional<String> value(String key) {
		return entries.stream()
				.filter(entry -> folded(entry.getKeyNode()).equals(key))
				.reduce((first, last) -> last)
				.map(entry -> folded(entry.getValueNode()));
	}

	/** The declared keys, in document order, without their trailing colon. */
	public List<String> keys() {
		return entries.stream().map(entry -> folded(entry.getKeyNode())).toList();
	}

	/**
	 * The keys declared more than once, each named once, in first-appearance order.
	 * Only the last takes effect, so the earlier lines say something the tool never
	 * reads — the front matter equivalent of the duplicated JSON key the
	 * configuration rules already reject.
	 */
	public List<String> duplicateKeys() {
		Set<String> seen = new LinkedHashSet<>();
		return keys().stream().filter(key -> !seen.add(key)).distinct().toList();
	}

	/**
	 * The block's entries as YAML reads them, or empty when no loader can read the
	 * block. A document that reads but is not a mapping declares no entries rather
	 * than being unreadable: that is how a loader sees a {@code name:value} written
	 * without the space a YAML entry needs.
	 */
	private static Optional<List<NodeTuple>> entriesOf(List<String> lines) {
		try {
			Node composed = new Yaml().compose(new StringReader(String.join("\n", lines)));
			return Optional.of(composed instanceof MappingNode mapping ? mapping.getValue() : List.of());
		} catch (YAMLException e) {
			return Optional.empty();
		}
	}

	/**
	 * One node as a single line of text: a scalar as YAML resolved it, and a mapping
	 * or sequence rendered into the {@code key: value} and {@code - item} shape a
	 * YAML block writes them in. Every result is folded onto one line, so a literal
	 * block scalar's newlines read the same way a folded one's do.
	 * <p>
	 * The rendering stops at {@value #MAX_VALUE_LENGTH} characters and
	 * {@value #MAX_DEPTH} levels, answering two different shapes. The node graph is a
	 * graph rather than a tree — an alias is the node it names, not a copy — so nine
	 * aliases nested nine deep expand to hundreds of millions of characters (a
	 * billion laughs), which the length cap stops. An alias may also name a node that
	 * <em>contains</em> it, and the walk of such a cycle never reaches a leaf, so it
	 * recursed until the stack ran out — and a {@link StackOverflowError} is not a
	 * {@link YAMLException}, so it escaped {@link #entriesOf} and failed the build as
	 * an internal error rather than as the unreadable front matter it is. Both caps
	 * sit far above any value a rule meaningfully checks.
	 */
	private static String folded(Node node) {
		StringBuilder text = new StringBuilder();
		render(node, text, 0);
		return onOneLine(text.length() > MAX_VALUE_LENGTH ? text.substring(0, MAX_VALUE_LENGTH) : text.toString());
	}

	/**
	 * A scalar is rendered at any depth, since it ends the walk by itself. Only a
	 * collection is held to {@value #MAX_DEPTH}, because only a collection can name a
	 * node that leads back to it.
	 */
	private static void render(Node node, StringBuilder text, int depth) {
		if (node instanceof ScalarNode scalar) {
			text.append(scalar.getValue());
		} else if (depth < MAX_DEPTH) {
			renderCollection(node, text, depth);
		}
	}

	private static void renderCollection(Node node, StringBuilder text, int depth) {
		if (node instanceof MappingNode entries) {
			entries.getValue().stream().takeWhile(entry -> hasRoom(text))
					.forEach(entry -> renderEntry(entry, text, depth + 1));
		} else if (node instanceof SequenceNode items) {
			items.getValue().stream().takeWhile(item -> hasRoom(text))
					.forEach(item -> renderItem(item, text, depth + 1));
		}
	}

	private static void renderEntry(NodeTuple entry, StringBuilder text, int depth) {
		separate(text);
		render(entry.getKeyNode(), text, depth);
		text.append(KEY_VALUE_SEPARATOR).append(' ');
		render(entry.getValueNode(), text, depth);
	}

	private static void renderItem(Node item, StringBuilder text, int depth) {
		separate(text);
		text.append("- ");
		render(item, text, depth);
	}

	/** Separates one entry or item from the one before it, never doubling a space already there. */
	private static void separate(StringBuilder text) {
		if (!text.isEmpty() && text.charAt(text.length() - 1) != ' ') {
			text.append(' ');
		}
	}

	/**
	 * Whether there is still room to render into {@code text}. Tested by the
	 * collections rather than only on entry to {@link #render}, so a filled budget
	 * stops the iteration — the part an alias graph multiplies — and not merely the
	 * appending.
	 */
	private static boolean hasRoom(StringBuilder text) {
		return text.length() < MAX_VALUE_LENGTH;
	}

	/**
	 * The text folded onto one line: each line stripped, the blank ones dropped, and
	 * the rest joined by single spaces. A value already on one line is only
	 * stripped, so its own spacing survives.
	 */
	private static String onOneLine(String value) {
		return value.lines().map(String::strip).filter(line -> !line.isEmpty()).collect(Collectors.joining(" "));
	}

	private static int indexOfDelimiter(List<String> lines, int from) {
		for (int i = from; i < lines.size(); i++) {
			if (lines.get(i).strip().equals(DELIMITER)) {
				return i;
			}
		}
		return -1;
	}
}
