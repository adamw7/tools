package io.github.adamw7.tools.code.format;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class UnusedImportsRemoverTest {

	private final ImportRemoverIfc remover = new UnusedImportsRemover();

	@Test
	public void removesSingleUnusedImport() {
		String code = """
				package com.example;

				import java.util.List;
				import java.util.Map;

				public class Sample {
					private List<String> values;
				}
				""";

		String result = remover.removeUnused(code);

		assertTrue(result.contains("import java.util.List;"));
		assertFalse(result.contains("import java.util.Map;"));
	}

	@Test
	public void keepsImportUsedOnlyAsNestedTypeQualifier() {
		String code = """
				package com.example;

				import com.example.protos.Person;

				public class Sample {
					private Person.Builder builder;
				}
				""";

		String result = remover.removeUnused(code);

		assertTrue(result.contains("import com.example.protos.Person;"));
	}

	@Test
	public void keepsWildcardImports() {
		String code = """
				package com.example;

				import com.example.protos.*;

				public class Sample {
				}
				""";

		String result = remover.removeUnused(code);

		assertTrue(result.contains("import com.example.protos.*;"));
	}

	@Test
	public void removesAllImportsWhenNoneReferenced() {
		String code = """
				package com.example;

				import java.util.List;
				import java.util.Map;

				public class Sample {
				}
				""";

		String result = remover.removeUnused(code);

		assertFalse(result.contains("import java.util.List;"));
		assertFalse(result.contains("import java.util.Map;"));
	}

	@Test
	public void keepsAllImportsWhenAllReferenced() {
		String code = """
				package com.example;

				import java.util.List;
				import java.util.Map;

				public class Sample {
					private List<String> values;
					private Map<String, String> mappings;
				}
				""";

		String result = remover.removeUnused(code);

		assertTrue(result.contains("import java.util.List;"));
		assertTrue(result.contains("import java.util.Map;"));
	}

	@Test
	public void returnsCodeUnchangedWhenNoImports() {
		String code = """
				package com.example;

				public class Sample {
				}
				""";

		String result = remover.removeUnused(code);

		assertTrue(result.contains("public class Sample"));
		assertFalse(result.contains("import"));
	}
}
