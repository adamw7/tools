package io.github.adamw7.tools.code.gen;

import io.github.adamw7.tools.code.format.Formatter;

public record ClassContainer(String name, CharSequence code) {
	
	public ClassContainer format() {
		return new ClassContainer(name, new Formatter().format(codeAsString()));
	}

	public String codeAsString() {
		return code.toString();
	}
}
