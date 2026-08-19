package io.github.adamw7.context.okf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.adamw7.context.Language;
import io.github.adamw7.context.tree.ProjectTreeBuilder;
import io.github.adamw7.context.tree.ProjectTreeNode;

/**
 * Holds every bundle the emitter produces to the Open Knowledge Format's own
 * conformance conditions, restated here from the specification rather than read
 * back out of {@link OkfBundler}. A test that asked the bundler what it meant to
 * write would pass however far the output drifted from OKF; these assertions are
 * the specification's side of the contract, so this is what fails when the two
 * part company.
 * <p>
 * This runs in the ordinary {@code test} phase, so {@code mvn test}, {@code mvn
 * package} and {@code mvn install} all catch the drift. The
 * {@code okfBundleFormat} enforcer rule checks a bundle already written to disk;
 * this checks the producer that writes one.
 */
public class OkfBundleConformanceTest {

	private static final String DELIMITER = "---";
	private static final String INDEX = "index.md";
	private static final String LOG = "log.md";
	private static final Pattern TYPE = Pattern.compile("^type:\\s*(.+)$", Pattern.MULTILINE);
	private static final Pattern OKF_VERSION = Pattern.compile("^okf_version:\\s*\"(.+)\"$", Pattern.MULTILINE);
	private static final Pattern GENERATED_AT = Pattern.compile("at:\\s*'?([^'\\s]+)'?");
	private static final Pattern BUNDLE_LINK = Pattern.compile("]\\((/[^)]+)\\)");

	@TempDir
	Path projectRoot;

	private final OkfBundler bundler = new OkfBundler(Language.JAVA);

	@Test
	void everyNonReservedDocumentCarriesFrontmatterDeclaringANonEmptyType() {
		concepts(bundler.bundle(sampleTree())).forEach(document -> {
			assertTrue(hasFrontmatter(document.content()),
					document.path() + " must open with a closed --- frontmatter block");
			assertFalse(typeOf(document.content()).isBlank(),
					document.path() + " must declare a non-empty type");
		});
	}

	@Test
	void onlyTheBundleRootIndexCarriesFrontmatterAndOnlyTheVersion() {
		OkfBundle bundle = bundler.bundle(sampleTree());

		bundle.documents().stream()
				.filter(document -> document.path().endsWith(INDEX))
				.forEach(document -> assertIndexConforms(document));
	}

	@Test
	void theBundleRootDeclaresTheVersionTheDocumentsTarget() {
		OkfBundle bundle = bundler.bundle(sampleTree());

		Matcher declared = OKF_VERSION.matcher(documentAt(bundle, INDEX));
		assertTrue(declared.find(), "the bundle root index must declare okf_version");
		assertEquals(OkfBundle.VERSION, declared.group(1));
	}

	@Test
	void noDocumentUsesAReservedNameForAConcept() {
		concepts(bundler.bundle(sampleTree())).forEach(document -> {
			assertFalse(document.path().endsWith("/" + INDEX) || document.path().equals(INDEX),
					document.path() + " must not take the reserved index name");
			assertFalse(document.path().endsWith("/" + LOG) || document.path().equals(LOG),
					document.path() + " must not take the reserved log name");
		});
	}

	@Test
	void everyDocumentPathIsUnique() {
		List<String> paths = bundler.bundle(sampleTree()).documents().stream().map(OkfDocument::path).toList();

		assertEquals(paths.size(), Set.copyOf(paths).size(), "two documents claimed the same bundle path");
	}

	@Test
	void everyBundleRelativeLinkResolvesToADocumentOfTheBundle() {
		OkfBundle bundle = bundler.bundle(sampleTree());
		Set<String> paths = new HashSet<>(bundle.documents().stream().map(OkfDocument::path).toList());

		links(bundle).forEach(link -> assertTrue(paths.contains(link.substring(1)),
				"the link " + link + " points outside the bundle"));
	}

	@Test
	void everyProductionTimestampIsAnIsoInstant() {
		concepts(bundler.bundle(sampleTree())).forEach(document -> {
			Matcher at = GENERATED_AT.matcher(document.content());
			assertTrue(at.find(), document.path() + " must record when it was generated");
			assertEquals(at.group(1), Instant.parse(at.group(1)).toString());
		});
	}

	@Test
	void aBundleScannedFromRealSourcesIsConformantToo() throws IOException {
		Files.writeString(projectRoot.resolve("A.java"), "public class A {}");
		Path pkg = Files.createDirectory(projectRoot.resolve("pkg"));
		Files.writeString(pkg.resolve("B.java"), "public class B { A a; }");
		Files.writeString(projectRoot.resolve("notes.md"), "plain markdown, not a source file");

		OkfBundle bundle = bundler.bundle(new ProjectTreeBuilder(Language.JAVA, 1).build(projectRoot));

		concepts(bundle).forEach(document -> assertTrue(hasFrontmatter(document.content())
				&& !typeOf(document.content()).isBlank(), document.path() + " is not conformant"));
		Set<String> paths = new HashSet<>(bundle.documents().stream().map(OkfDocument::path).toList());
		links(bundle).forEach(link -> assertTrue(paths.contains(link.substring(1)), link + " is dangling"));
	}

	private void assertIndexConforms(OkfDocument document) {
		if (!document.path().equals(INDEX)) {
			assertFalse(hasFrontmatter(document.content()),
					document.path() + " is not the bundle root, so it must carry no frontmatter");
			return;
		}
		assertEquals(List.of("okf_version"), frontmatterKeys(document.content()),
				"a bundle-root index may declare only okf_version");
	}

	private List<OkfDocument> concepts(OkfBundle bundle) {
		return bundle.documents().stream().filter(document -> !document.path().endsWith(INDEX)).toList();
	}

	private List<String> links(OkfBundle bundle) {
		List<String> found = new ArrayList<>();
		bundle.documents().forEach(document -> {
			Matcher matcher = BUNDLE_LINK.matcher(document.content());
			while (matcher.find()) {
				found.add(matcher.group(1));
			}
		});
		return found;
	}

	private String documentAt(OkfBundle bundle, String path) {
		return bundle.documents().stream()
				.filter(document -> document.path().equals(path))
				.map(OkfDocument::content)
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("the bundle holds no document at " + path));
	}

	/** True when the content opens with a {@code ---} line that a later {@code ---} closes. */
	private boolean hasFrontmatter(String content) {
		List<String> lines = content.lines().toList();
		return !lines.isEmpty() && lines.get(0).strip().equals(DELIMITER)
				&& lines.subList(1, lines.size()).stream().anyMatch(line -> line.strip().equals(DELIMITER));
	}

	private List<String> frontmatterKeys(String content) {
		return frontmatterLines(content).stream()
				.map(line -> line.substring(0, line.indexOf(':')))
				.toList();
	}

	private List<String> frontmatterLines(String content) {
		List<String> lines = content.lines().toList();
		return lines.subList(1, lines.size()).stream()
				.takeWhile(line -> !line.strip().equals(DELIMITER))
				.filter(line -> line.contains(":"))
				.toList();
	}

	private String typeOf(String content) {
		Matcher matcher = TYPE.matcher(content);
		return matcher.find() ? matcher.group(1).replace("\"", "").strip() : "";
	}

	private ProjectTreeNode sampleTree() {
		ProjectTreeNode root = new ProjectTreeNode("project", ProjectTreeNode.Type.DIRECTORY);
		ProjectTreeNode pkg = new ProjectTreeNode("pkg", ProjectTreeNode.Type.DIRECTORY);
		ProjectTreeNode dependency = new ProjectTreeNode("A.java", ProjectTreeNode.Type.FILE);
		ProjectTreeNode dependent = new ProjectTreeNode("B.java", ProjectTreeNode.Type.FILE);
		dependent.addDependency("A.java");
		dependent.addDependency("Library.java");
		pkg.addChild(dependency);
		pkg.addChild(dependent);
		root.addChild(pkg);
		root.addChild(new ProjectTreeNode("pom.xml", ProjectTreeNode.Type.FILE));
		root.addChild(new ProjectTreeNode(INDEX, ProjectTreeNode.Type.FILE));
		// Files whose own names are what the reserved document names are built from,
		// which is where a concept last took a path a directory listing already had.
		root.addChild(new ProjectTreeNode("index", ProjectTreeNode.Type.FILE));
		root.addChild(new ProjectTreeNode("log", ProjectTreeNode.Type.FILE));
		return root;
	}
}
