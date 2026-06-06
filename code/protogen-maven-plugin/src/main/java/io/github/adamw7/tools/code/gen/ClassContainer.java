package io.github.adamw7.tools.code.gen;

import io.github.adamw7.tools.code.format.Formatter;
import io.github.adamw7.tools.code.format.UnusedImportsRemover;

public record ClassContainer(String name, CharSequence code) {

	private static final UnusedImportsRemover IMPORTS_REMOVER = new UnusedImportsRemover();
	private static final Formatter FORMATTER = new Formatter();

	public ClassContainer format() {
		String withoutUnusedImports = IMPORTS_REMOVER.removeUnused(codeAsString());
		return new ClassContainer(name, FORMATTER.format(withoutUnusedImports));
	}

	String codeAsString() {
		return code.toString();
	}
}
