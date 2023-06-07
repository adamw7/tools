package io.github.adamw7.tools.code.gen;

import io.github.adamw7.tools.code.format.Formatter;

public record ClassContainer(String name, String code) {
	
	public ClassContainer(String name, Object code) {
		this(name, code.toString());
	}

	public ClassContainer format() {
		return new ClassContainer(name, new Formatter().format(code()));
	}

}
