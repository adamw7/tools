package io.github.adamw7.context.okf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.adamw7.context.Language;
import io.github.adamw7.context.tree.ProjectTreeNode;

public class OkfBundlerTest {

	private static final Instant PRODUCED_AT = Instant.parse("2026-08-03T10:15:30Z");
	private static final String PRODUCER = "test-producer/1";

	private final OkfBundler bundler = new OkfBundler(Language.JAVA, PRODUCER,
			Clock.fixed(PRODUCED_AT, ZoneOffset.UTC));

	@Test
	void bundleRootIndexDeclaresTheTargetedSpecificationVersion() {
		OkfBundle bundle = bundler.bundle(directory("project"));

		assertTrue(document(bundle, "index.md").startsWith("---"));
		assertTrue(document(bundle, "index.md").contains("okf_version: \"0.2\""));
	}

	@Test
	void aNestedIndexCarriesNoFrontmatter() {
		ProjectTreeNode root = directory("project");
		root.addChild(directory("pkg"));

		OkfBundle bundle = bundler.bundle(root);

		assertFalse(document(bundle, "pkg/index.md").contains("---"));
	}

	@Test
	void everyConceptDocumentDeclaresANonEmptyType() {
		ProjectTreeNode root = directory("project");
		ProjectTreeNode pkg = directory("pkg");
		pkg.addChild(file("A.java"));
		root.addChild(pkg);
		root.addChild(file("README.md"));

		OkfBundle bundle = bundler.bundle(root);

		bundle.documents().stream()
				.filter(document -> !document.path().endsWith(OkfBundle.INDEX))
				.forEach(document -> {
					assertTrue(document.content().startsWith("---"), document.path() + " must open with frontmatter");
					assertTrue(document.content().contains("type: "), document.path() + " must declare a type");
				});
	}

	@Test
	void filesBecomeConceptDocumentsBesideTheirDirectoryIndex() {
		ProjectTreeNode root = directory("project");
		ProjectTreeNode pkg = directory("pkg");
		pkg.addChild(file("A.java"));
		root.addChild(pkg);

		OkfBundle bundle = bundler.bundle(root);

		assertEquals(List.of("index.md", "pkg/index.md", "pkg/A.java.md"),
				bundle.documents().stream().map(OkfDocument::path).toList());
	}

	@Test
	void aDependencyResolvedInTheProjectBecomesABundleRelativeLink() {
		ProjectTreeNode root = directory("project");
		ProjectTreeNode pkg = directory("pkg");
		pkg.addChild(file("A.java"));
		ProjectTreeNode dependent = file("B.java");
		dependent.addDependency("A.java");
		pkg.addChild(dependent);
		root.addChild(pkg);

		OkfBundle bundle = bundler.bundle(root);

		assertTrue(document(bundle, "pkg/B.java.md").contains("* [`A.java`](/pkg/A.java.md)"));
	}

	@Test
	void aDependencyOutsideTheProjectStaysPlainCode() {
		ProjectTreeNode root = directory("project");
		ProjectTreeNode dependent = file("B.java");
		dependent.addDependency("Library.java");
		root.addChild(dependent);

		OkfBundle bundle = bundler.bundle(root);

		assertTrue(document(bundle, "B.java.md").contains("* `Library.java`"));
		assertFalse(document(bundle, "B.java.md").contains("]("));
	}

	@Test
	void anAmbiguousDependencyNameIsNotLinkedToAGuess() {
		ProjectTreeNode root = directory("project");
		ProjectTreeNode first = directory("one");
		first.addChild(file("A.java"));
		ProjectTreeNode second = directory("two");
		second.addChild(file("A.java"));
		ProjectTreeNode dependent = file("B.java");
		dependent.addDependency("A.java");
		root.addChild(first);
		root.addChild(second);
		root.addChild(dependent);

		OkfBundle bundle = bundler.bundle(root);

		assertTrue(document(bundle, "B.java.md").contains("* `A.java`"));
		assertFalse(document(bundle, "B.java.md").contains("]("));
	}

	@Test
	void aConceptWithoutDependenciesSaysSo() {
		ProjectTreeNode root = directory("project");
		root.addChild(file("A.java"));

		OkfBundle bundle = bundler.bundle(root);

		assertTrue(document(bundle, "A.java.md").contains("# Dependencies"));
		assertTrue(document(bundle, "A.java.md").contains("This file depends on no other file in the project."));
	}

	@Test
	void anIndexEntryRepeatsTheDescriptionOfTheConceptItLinksTo() {
		ProjectTreeNode root = directory("project");
		ProjectTreeNode dependent = file("B.java");
		dependent.addDependency("Library.java");
		root.addChild(dependent);

		OkfBundle bundle = bundler.bundle(root);

		String description = "Java source file with 1 project dependency.";
		assertTrue(document(bundle, "index.md").contains("* [B.java](B.java.md) - " + description));
		assertTrue(document(bundle, "B.java.md").contains("description: " + description));
	}

	@Test
	void anIndexListsItsSubdirectoriesWithATrailingSeparator() {
		ProjectTreeNode root = directory("project");
		ProjectTreeNode pkg = directory("pkg");
		pkg.addChild(file("A.java"));
		root.addChild(pkg);

		OkfBundle bundle = bundler.bundle(root);

		assertTrue(document(bundle, "index.md").contains("# Directories"));
		assertTrue(document(bundle, "index.md").contains("* [pkg](pkg/) - Directory holding 1 concept."));
	}

	@Test
	void anEmptyDirectoryStillGetsAConformantIndex() {
		OkfBundle bundle = bundler.bundle(directory("project"));

		assertEquals(1, bundle.documents().size());
		assertTrue(document(bundle, "index.md").contains("# Contents"));
		assertTrue(document(bundle, "index.md").contains("This directory holds no concepts."));
	}

	@Test
	void aFileNamedAfterAReservedDocumentDoesNotClaimIt() {
		ProjectTreeNode root = directory("project");
		root.addChild(file("index"));
		root.addChild(file("log"));

		OkfBundle bundle = bundler.bundle(root);

		assertEquals(List.of("index.md", "index.concept.md", "log.concept.md"),
				bundle.documents().stream().map(OkfDocument::path).toList());
		assertTrue(document(bundle, "index.md").contains("* [index](index.concept.md)"));
		assertTrue(document(bundle, "index.md").contains("* [log](log.concept.md)"));
	}

	@Test
	void aFileNamedAfterAnEscapedReservedDocumentDoesNotClaimItEither() {
		ProjectTreeNode root = directory("project");
		root.addChild(file("index"));
		root.addChild(file("index.concept"));

		OkfBundle bundle = bundler.bundle(root);

		assertEquals(List.of("index.md", "index.concept.md", "index.concept.concept.md"),
				bundle.documents().stream().map(OkfDocument::path).toList());
	}

	@Test
	void aDependencyOnAFileNamedAfterAReservedDocumentLinksToItsEscapedConcept() {
		ProjectTreeNode root = directory("project");
		root.addChild(file("index"));
		ProjectTreeNode dependent = file("B.java");
		dependent.addDependency("index");
		root.addChild(dependent);

		OkfBundle bundle = bundler.bundle(root);

		assertTrue(document(bundle, "B.java.md").contains("* [`index`](/index.concept.md)"));
	}

	@Test
	void aFileThatIsNotASourceFileIsTypedAsAProjectFile() {
		ProjectTreeNode root = directory("project");
		root.addChild(file("pom.xml"));

		OkfBundle bundle = bundler.bundle(root);

		assertTrue(document(bundle, "pom.xml.md").contains("type: Project File"));
		assertTrue(document(bundle, "pom.xml.md").contains("tags:" + System.lineSeparator() + "- file"));
	}

	@Test
	void theConfiguredLanguageNamesAndTagsTheConcept() {
		ProjectTreeNode root = directory("project");
		root.addChild(file("A.kt"));
		OkfBundler kotlinBundler = new OkfBundler(Language.KOTLIN, PRODUCER, Clock.fixed(PRODUCED_AT, ZoneOffset.UTC));

		OkfBundle bundle = kotlinBundler.bundle(root);

		assertTrue(document(bundle, "A.kt.md").contains("type: Kotlin Source File"));
		assertTrue(document(bundle, "A.kt.md").contains("- source" + System.lineSeparator() + "- kotlin"));
	}

	@Test
	void aConceptPointsAtTheProjectFileItDescribes() {
		ProjectTreeNode root = directory("project");
		ProjectTreeNode pkg = directory("pkg");
		pkg.addChild(file("A.java"));
		root.addChild(pkg);

		OkfBundle bundle = bundler.bundle(root);

		assertTrue(document(bundle, "pkg/A.java.md").contains("resource: pkg/A.java"));
	}

	@Test
	void aScannedFileRootStillYieldsAConformantBundle() {
		OkfBundle bundle = bundler.bundle(file("A.java"));

		assertEquals(List.of("index.md", "A.java.md"), bundle.documents().stream().map(OkfDocument::path).toList());
		assertTrue(document(bundle, "index.md").contains("* [A.java](A.java.md)"));
	}

	@Test
	void everyConceptRecordsTheProducerAndTheMomentOfProduction() {
		ProjectTreeNode root = directory("project");
		root.addChild(file("A.java"));

		OkfBundle bundle = bundler.bundle(root);

		assertTrue(document(bundle, "A.java.md")
				.contains("generated:" + System.lineSeparator() + "  by: " + PRODUCER + System.lineSeparator() + "  at: '2026-08-03T10:15:30Z'"));
	}

	@Test
	void aFileNamedLikeAReservedDocumentDoesNotOverwriteTheIndex() {
		ProjectTreeNode root = directory("project");
		root.addChild(file(OkfBundle.INDEX));

		OkfBundle bundle = bundler.bundle(root);

		assertTrue(document(bundle, "index.md").contains("# Files"));
		assertTrue(document(bundle, "index.md.md").contains("type: Project File"));
	}

	private String document(OkfBundle bundle, String path) {
		OkfDocument document = bundle.documents().stream()
				.filter(candidate -> candidate.path().equals(path))
				.findFirst()
				.orElse(null);
		assertNotNull(document, "the bundle must hold a document at " + path);
		return document.content();
	}

	private ProjectTreeNode directory(String name) {
		return new ProjectTreeNode(name, ProjectTreeNode.Type.DIRECTORY);
	}

	private ProjectTreeNode file(String name) {
		return new ProjectTreeNode(name, ProjectTreeNode.Type.FILE);
	}
}
