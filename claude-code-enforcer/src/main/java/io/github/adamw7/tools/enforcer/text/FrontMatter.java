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
 * The YAML front matter block at the top of a Markdown document: the lines
 * between a leading {@code ---} delimiter and the next {@code ---} delimiter.
 * <p>
 * The block is read by SnakeYAML, the same kind of loader Claude Code itself
 * reads front matter with, so what a rule validates is what the tool acts on.
 * Parsing stops at {@link Yaml#compose}, which answers the document's node tree
 * rather than constructing Java objects from it: a rule needs the text an author
 * declared, not a typed value. That distinction is not cosmetic — composing keeps
 * {@code okf_version: 0.20} the string {@code 0.20} instead of rounding it to a
 * double, and it keeps a duplicated key visible, which every loader that builds a
 * {@code Map} collapses.
 * <p>
 * Each entry is answered as a single line of text, which is all the rules need:
 * they check a value's length, blankness, uniqueness, or shape, never its
 * structure. A value written across several lines — a block scalar, a wrapped
 * plain scalar, a nested mapping or sequence — is folded into one line joined by
 * single spaces, because the alternative is to answer the empty string for a value
 * written out in full on the lines below its key. Folding a mapping is lossy on
 * purpose: {@code by: agent/1} reads back as text rather than as a nested entry. A
 * rule that needs the structure itself should ask SnakeYAML for it directly.
 * <p>
 * A block no loader can read — an unterminated quoted scalar, or a value such as
 * {@code "a" and "b"} whose quotes do not wrap it — is no front matter at all, and
 * {@link #parse} answers empty for it. Reading it as text instead and handing the
 * rules each key's characters verbatim validated something Claude Code never sees:
 * its loader fails on that block exactly as this one does. The rules already say so
 * far better than a guess could — "has no parseable YAML frontmatter block" — so
 * there is nothing here to invent. A block that reads but declares no entries, such
 * as the {@code name:value} that lacks the space a YAML entry needs, is present and
 * simply declares nothing.
 * <p>
 * A key declared twice yields its <em>last</em> declaration, which is the one a
 * YAML loader keeps and so the one Claude Code acts on. Reading the first instead
 * validated a value the tool never sees — a second {@code description:} could
 * lengthen a capped one, break a name convention, or collide with another
 * definition, and every check would still be looking at the line above it.
 * {@link #duplicateKeys()} reports the duplication itself, since a key written
 * twice is a mistake whichever value wins.
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
	 * Parses the front matter at the start of {@code content}, or returns empty
	 * when the content does not begin with a closed {@code ---} delimited block.
	 * Claude Code only recognises a block whose opening delimiter is the very
	 * first line, so content that reaches its {@code ---} after blank lines has
	 * no front matter here either. A byte-order mark, if any, must already be
	 * stripped by the caller.
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
	 * The keys declared more than once, each named once, in the order they first
	 * appear. Only the last of them takes effect, so the earlier lines say something
	 * the tool never reads — the front matter equivalent of the duplicated JSON key
	 * the configuration rules already reject.
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
	 * {@value #MAX_DEPTH} levels. Composing is linear in the size of the document, but
	 * the node graph it answers is a graph rather than a tree — an alias is the node it
	 * names, not a copy of it — so walking it is not. Nine aliases nested nine deep is a
	 * few lines of YAML that expands to hundreds of millions of characters, the shape
	 * known as a billion laughs, and a rule that reads a repository's own files should
	 * not be the thing that expands it.
	 * <p>
	 * The two caps answer two different shapes, and the length alone answered only one.
	 * An alias may name a node that <em>contains</em> it — {@code description: &loop}
	 * over a {@code - *loop} composes to a graph with a cycle in it — and the walk of
	 * such a graph never reaches a leaf to append at, so it recurses until the stack
	 * runs out. A {@link StackOverflowError} is not a {@link YAMLException}: it escaped
	 * {@link #entriesOf} and failed the build as an internal error rather than as the
	 * unreadable front matter it is. Both caps are far above any value a rule
	 * meaningfully checks: a description this long, or this deep, has already failed
	 * whatever it was held to.
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
	 * collections rather than only on entry to {@link #render}, so a walk that has
	 * filled the budget stops iterating instead of merely stopping appending — the
	 * iteration is the part an alias graph multiplies.
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
