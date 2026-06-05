package io.github.adamw7.tools.code.gen;

import io.github.adamw7.tools.code.format.Formatter;
import io.github.adamw7.tools.code.format.UnusedImportsRemover;

public record ClassContainer(String name, CharSequence code) {

	public ClassContainer format() {
		String withoutUnusedImports = new UnusedImportsRemover().removeUnused(codeAsString());
		return new ClassContainer(name, new Formatter().format(withoutUnusedImports));
	}

	String codeAsString() {
		return code.toString();
	}
}
