package io.github.adamw7.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

public class PackageAwareFinderTest {

	private final ClassContainer fooInA = new ClassContainer("Foo.java", "package a;\npublic class Foo {}");
	private final ClassContainer fooInB = new ClassContainer("Foo.java", "package b;\npublic class Foo { int x; }");

	@Test
	void rejectsNonPositiveDepth() {
		ClassContainer user = new ClassContainer("User.java", "package x;\npublic class User {}");
		PackageAwareFinder finder = new PackageAwareFinder(containers(user));

		assertThrows(IllegalArgumentException.class, () -> finder.find(user, 0));
		assertThrows(IllegalArgumentException.class, () -> finder.find(user, -1));
	}

	@Test
	void resolvesByExplicitImportWhenSimpleNameIsAmbiguous() {
		ClassContainer user = new ClassContainer("User.java",
				"package x;\nimport a.Foo;\npublic class User { Foo f; }");

		Set<ClassContainer> dependencies = new PackageAwareFinder(containers(fooInA, fooInB, user)).find(user, 1);

		assertTrue(dependencies.contains(fooInA));
		assertFalse(dependencies.contains(fooInB));
		assertEquals(1, dependencies.size());
	}

	@Test
	void prefersTheClassInTheReferencingFilesOwnPackage() {
		ClassContainer user = new ClassContainer("User.java", "package a;\npublic class User { Foo f; }");

		Set<ClassContainer> dependencies = new PackageAwareFinder(containers(fooInA, fooInB, user)).find(user, 1);

		assertTrue(dependencies.contains(fooInA));
		assertFalse(dependencies.contains(fooInB));
	}

	@Test
	void resolvesThroughAWildcardImport() {
		ClassContainer user = new ClassContainer("User.java",
				"package x;\nimport b.*;\npublic class User { Foo f; }");

		Set<ClassContainer> dependencies = new PackageAwareFinder(containers(fooInA, fooInB, user)).find(user, 1);

		assertTrue(dependencies.contains(fooInB));
		assertFalse(dependencies.contains(fooInA));
	}

	@Test
	void leavesAnAmbiguousReferenceUnresolved() {
		ClassContainer user = new ClassContainer("User.java", "package x;\npublic class User { Foo f; }");

		Set<ClassContainer> dependencies = new PackageAwareFinder(containers(fooInA, fooInB, user)).find(user, 1);

		assertTrue(dependencies.isEmpty());
	}

	@Test
	void fallsBackToTheSoleCandidateWhenTheSimpleNameIsUnique() {
		ClassContainer user = new ClassContainer("User.java", "package x;\npublic class User { Foo f; }");

		Set<ClassContainer> dependencies = new PackageAwareFinder(containers(fooInA, user)).find(user, 1);

		assertEquals(Set.of(fooInA), dependencies);
	}

	@Test
	void resolvesTransitiveDependenciesAcrossPackages() {
		ClassContainer leaf = new ClassContainer("Leaf.java", "package a;\npublic class Leaf {}");
		ClassContainer mid = new ClassContainer("Mid.java", "package a;\npublic class Mid { Leaf leaf; }");
		ClassContainer top = new ClassContainer("Top.java",
				"package x;\nimport a.Mid;\npublic class Top { Mid mid; }");

		Set<ClassContainer> dependencies = new PackageAwareFinder(containers(leaf, mid, top)).find(top, 2);

		assertEquals(Set.of(mid, leaf), dependencies);
	}

	@Test
	void stopsAtTheRequestedDepth() {
		ClassContainer leaf = new ClassContainer("Leaf.java", "package a;\npublic class Leaf {}");
		ClassContainer mid = new ClassContainer("Mid.java", "package a;\npublic class Mid { Leaf leaf; }");
		ClassContainer top = new ClassContainer("Top.java",
				"package x;\nimport a.Mid;\npublic class Top { Mid mid; }");

		Set<ClassContainer> dependencies = new PackageAwareFinder(containers(leaf, mid, top)).find(top, 1);

		assertEquals(Set.of(mid), dependencies);
	}

	@Test
	void terminatesOnCycles() {
		ClassContainer a = new ClassContainer("A.java", "package p;\nimport p.B;\npublic class A { B b; }");
		ClassContainer b = new ClassContainer("B.java", "package p;\nimport p.A;\npublic class B { A a; }");

		Set<ClassContainer> dependencies = new PackageAwareFinder(containers(a, b)).find(a, 10);

		assertEquals(Set.of(b), dependencies);
	}

	@Test
	void rootIsNeverItsOwnDependency() {
		ClassContainer a = new ClassContainer("A.java", "package p;\npublic class A { A self; }");

		assertTrue(new PackageAwareFinder(containers(a)).find(a, 1).isEmpty());
	}

	@Test
	void ignoresClassNamesInCommentsAndLiterals() {
		ClassContainer foo = new ClassContainer("Foo.java", "package a;\npublic class Foo {}");
		ClassContainer user = new ClassContainer("User.java",
				"package a;\npublic class User { /* Foo */ String s = \"Foo\"; }");

		assertTrue(new PackageAwareFinder(containers(foo, user)).find(user, 1).isEmpty());
	}

	@Test
	void resolvesScalaSourcesByTheirPackage() {
		ClassContainer foo = new ClassContainer("Foo.scala", "package a\nclass Foo");
		ClassContainer user = new ClassContainer("User.scala",
				"package x\nimport a.Foo\nclass User { val f: Foo = null }");

		Set<ClassContainer> dependencies =
				new PackageAwareFinder(containers(foo, user), Language.SCALA).find(user, 1);

		assertEquals(Set.of(foo), dependencies);
	}

	@Test
	void defaultConstructorResolvesJavaSources() {
		ClassContainer a = new ClassContainer("A.java", "package p;\npublic class A {}");
		ClassContainer b = new ClassContainer("B.java", "package p;\npublic class B { A a; }");

		assertEquals(Set.of(a), new PackageAwareFinder(containers(a, b)).find(b, 1));
	}

	@Test
	void resolvesWithinTheDefaultPackageWhenNoPackageIsDeclared() {
		ClassContainer a = new ClassContainer("A.java", "public class A {}");
		ClassContainer b = new ClassContainer("B.java", "public class B { A a; }");

		assertEquals(Set.of(a), new PackageAwareFinder(containers(a, b)).find(b, 1));
	}

	@Test
	void explicitImportTakesPrecedenceOverASamePackageClassOfTheSameName() {
		ClassContainer user = new ClassContainer("User.java",
				"package a;\nimport b.Foo;\npublic class User { Foo f; }");

		Set<ClassContainer> dependencies = new PackageAwareFinder(containers(fooInA, fooInB, user)).find(user, 1);

		assertEquals(Set.of(fooInB), dependencies);
		assertFalse(dependencies.contains(fooInA));
	}

	@Test
	void anImportPointingAtAMissingClassFallsBackToTheSamePackage() {
		ClassContainer user = new ClassContainer("User.java",
				"package a;\nimport x.Foo;\npublic class User { Foo f; }");

		Set<ClassContainer> dependencies = new PackageAwareFinder(containers(fooInA, fooInB, user)).find(user, 1);

		assertEquals(Set.of(fooInA), dependencies);
	}

	@Test
	void resolvesTheOuterTypeOfAQualifiedReference() {
		ClassContainer map = new ClassContainer("Map.java", "package p;\npublic class Map {}");
		ClassContainer user = new ClassContainer("User.java", "package p;\npublic class User { Map.Entry e; }");

		assertEquals(Set.of(map), new PackageAwareFinder(containers(map, user)).find(user, 1));
	}

	@Test
	void resolvesAcrossSeveralWildcardImports() {
		ClassContainer foo = new ClassContainer("Foo.java", "package a;\npublic class Foo {}");
		ClassContainer bar = new ClassContainer("Bar.java", "package b;\npublic class Bar {}");
		ClassContainer user = new ClassContainer("User.java",
				"package x;\nimport a.*;\nimport b.*;\npublic class User { Foo f; Bar b; }");

		Set<ClassContainer> dependencies = new PackageAwareFinder(containers(foo, bar, user)).find(user, 1);

		assertEquals(2, dependencies.size());
		assertTrue(dependencies.contains(foo));
		assertTrue(dependencies.contains(bar));
	}

	@Test
	void resolvesKotlinSourcesByTheirPackage() {
		ClassContainer foo = new ClassContainer("Foo.kt", "package a\nclass Foo");
		ClassContainer user = new ClassContainer("User.kt",
				"package x\nimport a.Foo\nclass User { val f: Foo? = null }");

		Set<ClassContainer> dependencies =
				new PackageAwareFinder(containers(foo, user), Language.KOTLIN).find(user, 1);

		assertEquals(Set.of(foo), dependencies);
	}

	@Test
	void aLeafClassHasNoDependencies() {
		ClassContainer a = new ClassContainer("A.java", "package p;\npublic class A {}");

		assertTrue(new PackageAwareFinder(containers(a)).find(a, 1).isEmpty());
	}

	@Test
	void preservesBreadthFirstOrderOfDependencies() {
		ClassContainer leaf = new ClassContainer("Leaf.java", "package a;\npublic class Leaf {}");
		ClassContainer mid = new ClassContainer("Mid.java", "package a;\npublic class Mid { Leaf leaf; }");
		ClassContainer top = new ClassContainer("Top.java",
				"package x;\nimport a.Mid;\npublic class Top { Mid mid; }");

		List<String> order = new PackageAwareFinder(containers(leaf, mid, top)).find(top, 2).stream()
				.map(ClassContainer::className)
				.toList();

		assertEquals(List.of("Mid.java", "Leaf.java"), order);
	}

	private Set<ClassContainer> containers(ClassContainer... containers) {
		return new HashSet<>(Set.of(containers));
	}
}
