package io.github.adamw7.tools.code.format;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.TextEdit;

/**
 * Applies an Eclipse {@link TextEdit} to source text. Both the formatter and the
 * unused-import remover end the same way — wrap the code in a {@link Document},
 * apply the edits the tool computed, and hand back the result — so the wrapping,
 * and the {@link BadLocationException} that becomes a {@link FormatException},
 * live here once rather than in each of them.
 */
final class Documents {

	/**
	 * Computes the edits to apply. The document is passed in because a rewrite
	 * derives its edits from the very document they are applied to.
	 */
	@FunctionalInterface
	interface Edits {
		TextEdit of(IDocument document) throws BadLocationException;
	}

	private Documents() {
	}

	/** @return {@code code} with {@code edits} applied to it */
	static String edited(String code, Edits edits) {
		IDocument document = new Document(code);
		try {
			edits.of(document).apply(document);
		} catch (BadLocationException e) {
			throw new FormatException(e);
		}
		return document.get();
	}
}
