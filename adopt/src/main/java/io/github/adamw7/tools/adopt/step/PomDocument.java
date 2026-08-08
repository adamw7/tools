package io.github.adamw7.tools.adopt.step;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;

import io.github.adamw7.tools.adopt.AdoptionException;
import io.github.adamw7.tools.adopt.AdoptionFiles;

/**
 * A {@code pom.xml} read for editing and written back without reformatting.
 * Callers find the element they want to extend with the query methods and add
 * markup under it with {@link #insertUnder}; every whitespace character, the XML
 * declaration, the trailing newline, and the line terminator survive
 * {@link #write()} untouched, so the adoption commit shows only the block that was
 * added.
 *
 * <p>The parse is only ever read — it answers which elements the POM declares, how
 * they nest, and where each of them sits in the source text — while the edit itself
 * is text spliced into the bytes the file already held. Writing an edited tree out
 * whole cannot keep that promise however carefully the serialiser is configured,
 * because the details it reformats are not in the tree to begin with: a start tag
 * spread over several lines and an empty element written {@code <rule />} both come
 * back normalised. Keeping the edit textual also means added elements inherit the
 * POM's default namespace rather than having to be qualified.
 *
 * <p>The reading is jsoup's, whose XML parser reports the source range of every
 * start and end tag it reads — the one thing a plain DOM parse cannot answer, and
 * the reason this class used to pair a JAXP parse with a lexical scan of its own.
 * jsoup resolves no DTDs and no external entities at all, so the secure-processing
 * configuration that parse needed has nothing left to switch off.
 *
 * <p>Added markup arrives one element per line, indented by
 * {@link #FRAGMENT_INDENT} per nesting level, and is re-indented to the document's
 * own unit (detected from the file, defaulting to two spaces) at the depth it
 * lands at. It is inserted after the parent's last child, so the parent's own
 * closing indentation stays put.
 */
final class PomDocument {

	/** The indentation one nesting level of a caller's fragment is written with. */
	static final String FRAGMENT_INDENT = "  ";

	private static final String DEFAULT_INDENT_UNIT = "  ";
	private static final String DESCRIPTION = "POM";
	private static final String SELF_CLOSING_END = "/>";

	/** A replacement of {@code [start, end)} in the original text; a pure insertion when they are equal. */
	private record Edit(int start, int end, String replacement) {
	}

	private final Path file;
	private final String original;
	private final Element root;
	private final String indentUnit;

	/** The markup to add inside each element, in fragment indentation, awaiting {@link #write()}. */
	private final Map<Element, String> additions = new IdentityHashMap<>();

	private PomDocument(Path file, String original, Element root) {
		this.file = file;
		this.original = original;
		this.root = root;
		this.indentUnit = detectIndentUnit(root);
	}

	/**
	 * The file is read once and parsed from that same text, so the elements and the
	 * source offsets they report can never come from two different reads of a file
	 * that changed in between.
	 */
	static PomDocument read(Path file) {
		String original = AdoptionFiles.read(file, DESCRIPTION);
		return new PomDocument(file, original, parse(file, original));
	}

	Element root() {
		return root;
	}

	/**
	 * Every {@code plugin} the POM binds to a build, wherever that build sits: the
	 * project's own or a profile's. A project can wire a plugin somewhere other than
	 * its build — behind an opt-in profile, most often — and a check that only looked
	 * there would conclude the plugin is absent and add a second declaration of it.
	 *
	 * <p>A {@code pluginManagement} entry is deliberately not one of them. It says
	 * which version to use if the plugin is ever bound and nothing more, so a rule
	 * declared only there is a rule no build ever runs.
	 */
	List<Element> boundPlugins() {
		return selfAndDescendants(root).stream()
				.filter(element -> "plugin".equals(localName(element)))
				.filter(element -> !isManaged(element))
				.toList();
	}

	/** Whether the element sits under a {@code pluginManagement}, at any depth. */
	private static boolean isManaged(Element element) {
		return element.parents().stream().anyMatch(parent -> "pluginManagement".equals(localName(parent)));
	}

	/**
	 * The element the nested {@code path} names below the root, or empty when the POM
	 * does not carry every level of it. The counterpart of {@link #insertUnder}: a
	 * caller asks about the very place it would add to, so what it inspects and what
	 * it edits cannot drift apart.
	 */
	Optional<Element> at(List<String> path) {
		Optional<Element> element = Optional.of(root);
		for (String name : path) {
			element = element.flatMap(parent -> child(parent, name));
		}
		return element;
	}

	static Optional<Element> child(Element parent, String name) {
		return children(parent, name).stream().findFirst();
	}

	static List<Element> children(Element parent, String name) {
		return parent.children().stream().filter(child -> name.equals(localName(child))).toList();
	}

	/** The element's name without the namespace prefix it may have been written with. */
	static String localName(Element element) {
		return element.tag().localName();
	}

	/** @return whether the element declares exactly this {@code artifactId} */
	static boolean hasArtifactId(Element element, String artifactId) {
		return child(element, "artifactId")
				.map(Element::wholeText)
				.map(String::strip)
				.filter(artifactId::equals)
				.isPresent();
	}

	/** @return {@code body} wrapped in a {@code name} element, its lines indented one level deeper */
	static String wrapped(String name, String body) {
		return "<" + name + ">\n" + indented(body) + "\n</" + name + ">";
	}

	/**
	 * Adds {@code fragment} inside {@code parent}'s nested {@code path}, creating
	 * whichever of those levels the POM does not carry yet — so a POM with no
	 * {@code build} at all and one that already has {@code build/plugins} are the same
	 * call. Nothing is written until {@link #write()}.
	 */
	void insertUnder(Element parent, List<String> path, String fragment) {
		if (path.isEmpty()) {
			additions.merge(parent, fragment, (existing, added) -> existing + "\n" + added);
			return;
		}
		List<String> rest = path.subList(1, path.size());
		child(parent, path.get(0)).ifPresentOrElse(
				element -> insertUnder(element, rest, fragment),
				() -> insertUnder(parent, List.of(), wrap(path, fragment)));
	}

	/**
	 * Writes the original text back with the added markup spliced into it, so every
	 * byte the file already held — its XML declaration, its attribute layout, its
	 * empty-element style, its trailing newline — is preserved by construction rather
	 * than by a serialiser that has to be talked out of reformatting. A POM nothing was
	 * added to is written back unchanged.
	 *
	 * <p>The edits are applied last-first so that each one's offsets, taken from the
	 * unmodified text, are still correct when it is applied.
	 */
	void write() {
		StringBuilder content = new StringBuilder(original);
		additions.entrySet().stream()
				.map(addition -> edit(addition.getKey(), addition.getValue()))
				.sorted(Comparator.comparingInt(Edit::start).reversed())
				.forEach(edit -> content.replace(edit.start(), edit.end(), edit.replacement()));
		AdoptionFiles.write(file, content.toString(), DESCRIPTION);
	}

	/** @return {@code fragment} wrapped in the named elements, outermost first */
	private static String wrap(List<String> names, String fragment) {
		String wrapped = fragment;
		for (String name : names.reversed()) {
			wrapped = wrapped(name, wrapped);
		}
		return wrapped;
	}

	private static String indented(String fragment) {
		return fragment.lines().map(line -> FRAGMENT_INDENT + line).collect(Collectors.joining("\n"));
	}

	/**
	 * Where the markup goes. An element that already had children takes it after the
	 * last of them, leaving the whitespace before the end tag — and so that tag's
	 * indentation — exactly as it was. An element that was empty or held only
	 * whitespace has that content replaced instead, because there is no last child to
	 * follow and its end tag needs indenting onto a line of its own; an element written
	 * {@code <plugins/>} additionally has to grow an end tag to hold the markup at all.
	 */
	private Edit edit(Element parent, String addition) {
		int contentStart = contentStart(parent);
		int depth = parent.parents().size();
		String fragment = fragment(addition, depth + 1);
		String closingIndent = newlineIndent(depth);
		if (isSelfClosing(contentStart)) {
			return edit(parent.sourceRange().startPos(), contentStart,
					reopened(parent) + fragment + closingIndent + endTag(parent));
		}
		int contentEnd = parent.endSourceRange().startPos();
		int lastChildEnd = beforeTrailingWhitespace(contentStart, contentEnd);
		if (lastChildEnd == contentStart) {
			return edit(contentStart, contentEnd, fragment + closingIndent);
		}
		return edit(lastChildEnd, lastChildEnd, fragment);
	}

	/** The offset just past the element's start tag, where its content begins. */
	private static int contentStart(Element element) {
		return element.sourceRange().endPos();
	}

	/** Whether the start tag ending at {@code contentStart} was written {@code <name/>}. */
	private boolean isSelfClosing(int contentStart) {
		return original.startsWith(SELF_CLOSING_END, contentStart - SELF_CLOSING_END.length());
	}

	/**
	 * The addition's lines, each on a line of its own indented to {@code depth} plus
	 * its own nesting within the fragment, in the document's indentation unit rather
	 * than the fragment's.
	 */
	private String fragment(String addition, int depth) {
		return addition.lines()
				.map(line -> newlineIndent(depth + levelOf(line)) + line.stripLeading())
				.collect(Collectors.joining());
	}

	private int levelOf(String line) {
		return (line.length() - line.stripLeading().length()) / FRAGMENT_INDENT.length();
	}

	/**
	 * A fragment is always LF — the parse reads {@code \r\n} as a line break and the
	 * templates are written with LF — so {@link LineTerminators} puts the file's own
	 * terminator back rather than leaving LF lines in an otherwise CRLF POM.
	 */
	private Edit edit(int start, int end, String replacement) {
		return new Edit(start, end, LineTerminators.matching(replacement, original));
	}

	/** The start tag of a {@code <name/>} element, reopened as {@code <name>} to take children. */
	private String reopened(Element element) {
		String startTag = original.substring(element.sourceRange().startPos(), contentStart(element));
		return startTag.substring(0, startTag.length() - SELF_CLOSING_END.length()).stripTrailing() + ">";
	}

	private String endTag(Element element) {
		return "</" + element.tagName() + ">";
	}

	private int beforeTrailingWhitespace(int contentStart, int contentEnd) {
		int end = contentEnd;
		while (end > contentStart && Character.isWhitespace(original.charAt(end - 1))) {
			end--;
		}
		return end;
	}

	/**
	 * The element and every element below it, in document order — what a caller asks
	 * for when the thing it is looking for may sit at any depth, such as the rule
	 * inside a plugin's {@code configuration/rules} that
	 * {@link PomEnforcerInstaller} looks for.
	 */
	static List<Element> selfAndDescendants(Element root) {
		return List.copyOf(root.getAllElements());
	}

	private String newlineIndent(int depth) {
		return "\n" + indentUnit.repeat(depth);
	}

	/**
	 * The indentation of a single nesting level, read from the first top-level
	 * element's leading whitespace, or two spaces when the POM carries none.
	 */
	private static String detectIndentUnit(Element root) {
		return root.children().stream()
				.map(PomDocument::leadingIndentOf)
				.filter(unit -> !unit.isEmpty())
				.findFirst()
				.orElse(DEFAULT_INDENT_UNIT);
	}

	private static String leadingIndentOf(Element element) {
		Node previous = element.previousSibling();
		if (!(previous instanceof TextNode text) || !text.isBlank()) {
			return "";
		}
		String whitespace = text.getWholeText();
		int newline = whitespace.lastIndexOf('\n');
		return newline < 0 ? "" : whitespace.substring(newline + 1);
	}

	private static Element parse(Path file, String original) {
		Document document = Jsoup.parse(original, "", Parser.xmlParser().setTrackPosition(true));
		Element root = document.children().first();
		if (root == null) {
			throw new AdoptionException("Could not read POM: " + file + " declares no root element");
		}
		requireClosedElements(file, original, root);
		return root;
	}

	/**
	 * Refuses a POM that left an element open. jsoup repairs what it reads rather than
	 * rejecting it, so an unclosed element comes back closed at the point its parent
	 * ends — an end tag that is not in the file, reported as an implicit range — and an
	 * edit spliced at that offset would land inside an element it was never meant to
	 * touch. An element written {@code <name/>} reports the same implicit range for the
	 * end tag it legitimately has none of, so the source has the last word on which of
	 * the two it is.
	 */
	private static void requireClosedElements(Path file, String original, Element root) {
		Optional<Element> unclosed = selfAndDescendants(root).stream()
				.filter(element -> element.endSourceRange().isImplicit())
				.filter(element -> !original.startsWith(SELF_CLOSING_END,
						contentStart(element) - SELF_CLOSING_END.length()))
				.findFirst();
		if (unclosed.isPresent()) {
			throw new AdoptionException(
					"Could not read POM: " + file + " never closes <" + unclosed.get().tagName() + ">");
		}
	}
}
