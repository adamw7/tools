package io.github.adamw7.tools.adopt.step;

/**
 * Keeps text the adoption writes into an existing file on the line terminator
 * that file already uses. Both installers that edit a project file — the POM
 * rewrite in {@link PomEnforcerInstaller} and the block
 * {@link GradleGuardInstaller} appends — produce LF text, so writing it into a
 * CRLF file would leave the new region with terminators mixed into an otherwise
 * CRLF file, or (for the POM, whose terminators XML parsing has already
 * normalised) flip every line of it to LF and reformat the whole file.
 */
final class LineTerminators {

	private static final String CRLF = "\r\n";
	private static final String LF = "\n";
	private static final String CR = "\r";

	private LineTerminators() {
	}

	/**
	 * Rewrites {@code text}'s line terminators to the one {@code sample} already
	 * uses: CRLF when the sample carries one, LF otherwise. The text is normalised
	 * to LF first, so one that already mixes terminators — assembled from a
	 * converted body and a part carried over verbatim — is not double-converted.
	 */
	static String matching(String text, String sample) {
		String normalized = normalized(text);
		return sample.contains(CRLF) ? normalized.replace(LF, CRLF) : normalized;
	}

	/**
	 * Rewrites every terminator to LF, so text assembled from parts that disagree —
	 * or read from a CRLF file and then reshaped line by line — is worked on in one
	 * shape and converted back only when it is written.
	 */
	static String normalized(String text) {
		return text.replace(CRLF, LF).replace(CR, LF);
	}
}
