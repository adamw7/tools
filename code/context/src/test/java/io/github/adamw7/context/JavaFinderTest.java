package io.github.adamw7.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JavaFinderTest {

	@TempDir
	Path projectRoot;

	@Test
	void rejectsNonPositiveDepth() throws IOException {
		ClassContainer a = writeJava("A", "public class A {}");
		Finder finder = new Finder(containers(a));

		assertThrows(IllegalArgumentException.class, () -> finder.find(a, 0));
		assertThrows(IllegalArgumentException.class, () -> finder.find(a, -1));
	}

	@Test
	void leafHasNoDependencies() throws IOException {
		ClassContainer a = writeJava("A", "public class A {}");

		assertTrue(new Finder(containers(a)).find(a, 1).isEmpty());
	}

	@Test
	void rootIsNeverItsOwnDependency() throws IOException {
		ClassContainer a = writeJava("A", "public class A { A self; }");

		assertTrue(new Finder(containers(a)).find(a, 1).isEmpty());
	}

	@Test
	void findsDirectDependencyAtDepthOne() throws IOException {
		ClassContainer a = writeJava("A", "public class A {}");
		ClassContainer b = writeJava("B", "public class B { A a; }");

		Set<ClassContainer> dependencies = new Finder(containers(a, b)).find(b, 1);

		assertEquals(1, dependencies.size());
		assertEquals(names("A.java"), classNames(dependencies));
	}

	@Test
	void findsAllDistinctDirectDependencies() throws IOException {
		ClassContainer a = writeJava("A", "public class A {}");
		ClassContainer c = writeJava("C", "public class C {}");
		ClassContainer b = writeJava("B", "public class B { A a; C c; A again; }");

		Set<ClassContainer> dependencies = new Finder(containers(a, b, c)).find(b, 1);

		assertEquals(2, dependencies.size());
		assertEquals(names("A.java", "C.java"), classNames(dependencies));
	}

	@Test
	void reportsSharedDependencyOnlyOnceInDiamondGraph() throws IOException {
		ClassContainer a = writeJava("A", "public class A {}");
		ClassContainer b = writeJava("B", "public class B { A a; }");
		ClassContainer c = writeJava("C", "public class C { A a; }");
		ClassContainer d = writeJava("D", "public class D { B b; C c; }");

		Set<ClassContainer> dependencies = new Finder(containers(a, b, c, d)).find(d, 3);

		assertEquals(3, dependencies.size());
		assertEquals(names("A.java", "B.java", "C.java"), classNames(dependencies));
	}

	@Test
	void ignoresReferencesWithoutAMatchingSourceFile() throws IOException {
		ClassContainer b = writeJava("B", "public class B { Unknown u; }");

		assertTrue(new Finder(containers(b)).find(b, 1).isEmpty());
	}

	@Test
	void stopsAtTheRequestedDepth() throws IOException {
		ClassContainer a = writeJava("A", "public class A {}");
		ClassContainer b = writeJava("B", "public class B { A a; }");
		ClassContainer c = writeJava("C", "public class C { B b; }");
		Set<ClassContainer> all = containers(a, b, c);

		assertEquals(names("B.java"), classNames(new Finder(all).find(c, 1)));
		assertEquals(names("A.java", "B.java"), classNames(new Finder(all).find(c, 2)));
	}

	@Test
	void doesNotWalkPastDepthInLongerChain() throws IOException {
		ClassContainer a = writeJava("A", "public class A {}");
		ClassContainer b = writeJava("B", "public class B { A a; }");
		ClassContainer c = writeJava("C", "public class C { B b; }");
		ClassContainer d = writeJava("D", "public class D { C c; }");

		Set<ClassContainer> dependencies = new Finder(containers(a, b, c, d)).find(d, 2);

		assertEquals(names("B.java", "C.java"), classNames(dependencies));
	}

	@Test
	void terminatesOnCycles() throws IOException {
		ClassContainer a = writeJava("A", "public class A { B b; }");
		ClassContainer b = writeJava("B", "public class B { A a; }");

		Set<ClassContainer> dependencies = new Finder(containers(a, b)).find(a, 10);

		assertEquals(1, dependencies.size());
		assertEquals(names("B.java"), classNames(dependencies));
	}

	@Test
	void ignoresClassNamesInComments() throws IOException {
		ClassContainer a = writeJava("A", "public class A {}");
		ClassContainer b = writeJava("B", "public class B { /* uses A */ // also A\n }");

		assertTrue(new Finder(containers(a, b)).find(b, 1).isEmpty());
	}

	@Test
	void ignoresClassNamesInStringLiterals() throws IOException {
		ClassContainer a = writeJava("A", "public class A {}");
		ClassContainer b = writeJava("B", "public class B { String s = \"A\"; }");

		assertTrue(new Finder(containers(a, b)).find(b, 1).isEmpty());
	}

	@Test
	void ignoresClassNamesInCharacterLiterals() throws IOException {
		ClassContainer a = writeJava("A", "public class A {}");
		ClassContainer b = writeJava("B", "public class B { char c = 'A'; }");

		assertTrue(new Finder(containers(a, b)).find(b, 1).isEmpty());
	}

	@Test
	void ignoresClassNamesInMultiLineBlockComments() throws IOException {
		ClassContainer a = writeJava("A", "public class A {}");
		ClassContainer b = writeJava("B", "public class B {\n/*\n A spanning\n several A lines\n*/\n}");

		assertTrue(new Finder(containers(a, b)).find(b, 1).isEmpty());
	}

	@Test
	void findsDependencyMentionedOnlyOutsideCommentsAndLiterals() throws IOException {
		ClassContainer a = writeJava("A", "public class A {}");
		ClassContainer b = writeJava("B", "public class B { /* not A */ String s = \"A\"; A real; }");

		Set<ClassContainer> dependencies = new Finder(containers(a, b)).find(b, 1);

		assertEquals(names("A.java"), classNames(dependencies));
	}

	private ClassContainer writeJava(String className, String body) throws IOException {
		Path path = projectRoot.resolve(className + ".java");
		Files.writeString(path, body);
		return ClassContainer.load(path, path.getFileName().toString());
	}

	private Set<ClassContainer> containers(ClassContainer... containers) {
		return new HashSet<>(Set.of(containers));
	}

	private Set<String> classNames(Set<ClassContainer> containers) {
		Set<String> names = new HashSet<>();
		for (ClassContainer container : containers) {
			names.add(container.className());
		}
		return names;
	}

	private Set<String> names(String... names) {
		return new HashSet<>(Set.of(names));
	}
}
