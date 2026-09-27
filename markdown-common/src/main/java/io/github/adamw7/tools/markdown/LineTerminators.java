package io.github.adamw7.tools.markdown;

/**
 * Keeps text written into an existing file on the line terminator that file
 * already uses.
 *
 * <p>Text assembled in code is LF, so writing it into a CRLF file leaves the new
 * region with terminators mixed into an otherwise CRLF file — or, where the
 * reading normalised the file first, flips every line of it to LF and reformats
 * the whole thing. Both show up as a diff nobody wrote.
 *
 * <p>It lives here rather than beside any one caller because the callers that need
 * it are on either side of this module: {@link MarkdownConformer} reshapes a
 * document line by line and has to put the terminators back, the adoption
 * pipeline edits build files the same way, and the enforcer's front-matter fix
 * rewrites a skill or agent file in place.
 */
public final class LineTerminators {

	private static final String CRLF = "\r\n";
	private static final String LF = "\n";
	private static final String CR = "\r";

	private LineTerminators() {
	}

	/**
	 * Rewrites {@code text}'s line terminators to the one {@code sample} already
	 * uses: CRLF when the sample carries one, a lone CR when that is the only
	 * terminator it has, LF otherwise. A stray CR inside an LF file therefore keeps
	 * it on LF. The text is normalised to LF first, so one that already mixes
	 * terminators — assembled from a converted body and a part carried over
	 * verbatim — is not double-converted.
	 */
	public static String matching(String text, String sample) {
		String normalized = normalized(text);
		String terminator = terminatorOf(sample);
		return terminator.equals(LF) ? normalized : normalized.replace(LF, terminator);
	}

	private static String terminatorOf(String sample) {
		if (sample.contains(CRLF)) {
			return CRLF;
		}
		return sample.contains(CR) && !sample.contains(LF) ? CR : LF;
	}

	/**
	 * Rewrites every terminator to LF, so text assembled from parts that disagree —
	 * or read from a CRLF file and then reshaped line by line — is worked on in one
	 * shape and converted back only when it is written.
	 */
	public static String normalized(String text) {
		return text.replace(CRLF, LF).replace(CR, LF);
	}
}
