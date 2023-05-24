package io.github.adamw7.tools.code.format;

import org.eclipse.jface.text.BadLocationException;

public class FormatException extends RuntimeException {

	public FormatException(BadLocationException e) {
		super(e);
	}

}
