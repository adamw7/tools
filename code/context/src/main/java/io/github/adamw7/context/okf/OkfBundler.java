package io.github.adamw7.context.okf;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import io.github.adamw7.context.Language;
import io.github.adamw7.context.tree.ProjectTreeNode;

/**
 * Turns a scanned {@link ProjectTreeNode} tree into an {@link OkfBundle}: every
 * directory becomes a reserved {@code index.md} listing what it holds, and every
 * file becomes a concept document whose frontmatter names it and whose body links
 * to the concepts it depends on. The result is the project's structure and its
 * class dependencies expressed in Google's Open Knowledge Format, so an agent
 * that already speaks OKF can consume this repository's context without knowing
 * anything about the tree the finder builds.
 * <p>
 * A dependency is linked only when its file name identifies exactly one concept
 * in the bundle; an ambiguous or unresolved name is rendered as plain code rather
 * than as a link to a guess. Links use the bundle-relative form the specification
 * recommends, since it survives a document being moved.
 */
public class OkfBundler {

	/** The {@code <producer>/<version>} actor recorded in every concept's {@code generated} field. */
	public static final String DEFAULT_PRODUCER = "tools.code.context/1";

	private static final String SEPARATOR = "/";
	private static final String DOCUMENT_SUFFIX = ".md";
	private static final String CONCEPT_SUFFIX = ".concept";
	private static final Set<String> RESERVED_NAMES = Set.of(OkfBundle.INDEX, OkfBundle.LOG);
	private static final String DELIMITER = "---";
	private static final String BULLET = "* ";
	private static final String DIRECTORIES_HEADING = "# Directories";
	private static final String FILES_HEADING = "# Files";
	private static final String EMPTY_HEADING = "# Contents";
	private static final String EMPTY_BODY = "This directory holds no concepts.";

	private final Language language;
	private final String producer;
	private final Clock clock;

	public OkfBundler(Language language) {
		this(language, DEFAULT_PRODUCER, Clock.systemUTC());
	}

	public OkfBundler(Language language, String producer, Clock clock) {
		this.language = language;
		this.producer = producer;
		this.clock = clock;
	}

	public OkfBundle bundle(ProjectTreeNode root) {
		Map<String, String> conceptPaths = conceptPaths(root);
		List<OkfDocument> documents = new ArrayList<>();
		documents.add(index(root, ""));
		entries(root).forEach(child -> collect(child, "", documents, conceptPaths));
		return new OkfBundle(documents);
	}

	private void collect(ProjectTreeNode node, String prefix, List<OkfDocument> documents,
			Map<String, String> conceptPaths) {
		if (node.isDirectory()) {
			collectDirectory(node, prefix + node.name() + SEPARATOR, documents, conceptPaths);
			return;
		}
		documents.add(concept(node, prefix, conceptPaths).render(producer, clock.instant()));
	}

	private void collectDirectory(ProjectTreeNode directory, String prefix, List<OkfDocument> documents,
			Map<String, String> conceptPaths) {
		documents.add(index(directory, prefix));
		directory.children().forEach(child -> collect(child, prefix, documents, conceptPaths));
	}

	/**
	 * Maps each file name to the one concept it identifies. A name carried by more
	 * than one file is dropped, so a dependency on it stays unlinked instead of
	 * pointing at whichever file happened to be scanned last.
	 */
	private Map<String, String> conceptPaths(ProjectTreeNode root) {
		Map<String, String> paths = new HashMap<>();
		Set<String> ambiguous = new HashSet<>();
		entries(root).forEach(child -> mapConcept(child, "", paths, ambiguous));
		ambiguous.forEach(paths::remove);
		return paths;
	}

	private void mapConcept(ProjectTreeNode node, String prefix, Map<String, String> paths, Set<String> ambiguous) {
		if (node.isDirectory()) {
			node.children().forEach(child -> mapConcept(child, prefix + node.name() + SEPARATOR, paths, ambiguous));
			return;
		}
		String previous = paths.put(node.name(), SEPARATOR + prefix + conceptName(node));
		if (previous != null) {
			ambiguous.add(node.name());
		}
	}

	private OkfConcept concept(ProjectTreeNode node, String prefix, Map<String, String> conceptPaths) {
		return new OkfConcept(prefix + conceptName(node), type(node), node.name(), describe(node),
				prefix + node.name(), tags(node), dependencyLinks(node, conceptPaths));
	}

	private List<String> dependencyLinks(ProjectTreeNode node, Map<String, String> conceptPaths) {
		return node.dependencies().stream().map(dependency -> link(dependency, conceptPaths)).toList();
	}

	private String link(String dependency, Map<String, String> conceptPaths) {
		String target = conceptPaths.get(dependency);
		if (target == null) {
			return "`" + dependency + "`";
		}
		return "[`" + dependency + "`](" + target + ")";
	}

	private OkfDocument index(ProjectTreeNode node, String prefix) {
		StringBuilder builder = new StringBuilder();
		appendVersion(builder, prefix);
		appendSection(builder, DIRECTORIES_HEADING, directoryEntries(node));
		appendSection(builder, FILES_HEADING, fileEntries(node));
		appendEmptyContents(builder, node);
		return new OkfDocument(prefix + OkfBundle.INDEX, builder.toString());
	}

	/**
	 * A bundle-root index is the one index allowed a frontmatter block, and the one
	 * place the targeted specification version is declared.
	 */
	private void appendVersion(StringBuilder builder, String prefix) {
		if (!prefix.isEmpty()) {
			return;
		}
		builder.append(DELIMITER).append(System.lineSeparator())
				.append("okf_version: \"").append(OkfBundle.VERSION).append('"').append(System.lineSeparator())
				.append(DELIMITER).append(System.lineSeparator())
				.append(System.lineSeparator());
	}

	private void appendSection(StringBuilder builder, String heading, List<String> sectionEntries) {
		if (sectionEntries.isEmpty()) {
			return;
		}
		builder.append(heading).append(System.lineSeparator()).append(System.lineSeparator());
		sectionEntries.forEach(entry -> builder.append(BULLET).append(entry).append(System.lineSeparator()));
		builder.append(System.lineSeparator());
	}

	private void appendEmptyContents(StringBuilder builder, ProjectTreeNode node) {
		if (!entries(node).isEmpty()) {
			return;
		}
		builder.append(EMPTY_HEADING).append(System.lineSeparator()).append(System.lineSeparator())
				.append(EMPTY_BODY).append(System.lineSeparator());
	}

	private List<String> directoryEntries(ProjectTreeNode node) {
		return indexEntries(node, ProjectTreeNode::isDirectory, child -> child.name() + SEPARATOR);
	}

	private List<String> fileEntries(ProjectTreeNode node) {
		return indexEntries(node, child -> !child.isDirectory(), this::conceptName);
	}

	/**
	 * One index line per accepted child: a markdown link to it, then what it holds.
	 * A directory and a file differ only in which of them is accepted and where the
	 * link points, so the line itself is written once.
	 */
	private List<String> indexEntries(ProjectTreeNode node, Predicate<ProjectTreeNode> accepted,
			Function<ProjectTreeNode, String> target) {
		return entries(node).stream()
				.filter(accepted)
				.map(child -> "[" + child.name() + "](" + target.apply(child) + ") - " + describe(child))
				.toList();
	}

	/**
	 * The children a node contributes to its own index. A file handed in as the
	 * scanned root is its own single entry, so a one-file scan still yields a
	 * conformant bundle rather than an empty one.
	 */
	private List<ProjectTreeNode> entries(ProjectTreeNode node) {
		return node.isDirectory() ? node.children() : List.of(node);
	}

	private String describe(ProjectTreeNode node) {
		if (node.isDirectory()) {
			return "Directory holding " + count(node.children().size(), "concept") + ".";
		}
		if (isSource(node)) {
			return displayLanguage() + " source file with "
					+ count(node.dependencies().size(), "project dependency", "project dependencies") + ".";
		}
		return "Project file " + node.name() + ".";
	}

	private String count(int amount, String singular) {
		return count(amount, singular, singular + "s");
	}

	private String count(int amount, String singular, String plural) {
		if (amount == 0) {
			return "no " + plural;
		}
		return amount + " " + (amount == 1 ? singular : plural);
	}

	private String type(ProjectTreeNode node) {
		return isSource(node) ? displayLanguage() + " Source File" : "Project File";
	}

	private List<String> tags(ProjectTreeNode node) {
		if (isSource(node)) {
			return List.of("source", language.name().toLowerCase(Locale.ROOT));
		}
		return List.of("file");
	}

	private boolean isSource(ProjectTreeNode node) {
		return node.name().endsWith(language.extension());
	}

	private String displayLanguage() {
		String name = language.name();
		return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
	}

	/**
	 * The bundle document a file's concept is written to. A file's own name plus
	 * {@value #DOCUMENT_SUFFIX} would let a file called {@code index} or {@code log}
	 * claim one of the names the specification reserves for a directory's listing and
	 * a bundle's log — two documents at one path, of which only the last written
	 * survives. Such a name is escaped instead, and so is a name that already carries
	 * the escape, which keeps the mapping one-to-one: no two files can be given the
	 * same concept document.
	 */
	private String conceptName(ProjectTreeNode node) {
		return escapeReserved(node.name()) + DOCUMENT_SUFFIX;
	}

	private String escapeReserved(String fileName) {
		return claimsAReservedName(fileName) ? fileName + CONCEPT_SUFFIX : fileName;
	}

	/** True for a reserved name and for anything the escaping above maps onto one. */
	private boolean claimsAReservedName(String fileName) {
		if (fileName.endsWith(CONCEPT_SUFFIX)) {
			return claimsAReservedName(fileName.substring(0, fileName.length() - CONCEPT_SUFFIX.length()));
		}
		return RESERVED_NAMES.contains(fileName + DOCUMENT_SUFFIX);
	}
}
