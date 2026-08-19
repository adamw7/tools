package io.github.adamw7.context.okf;

import java.util.List;

/**
 * A knowledge bundle in Google's Open Knowledge Format: a directory tree of
 * markdown documents that a gen-AI agent can consume without a proprietary
 * runtime, held in memory as an ordered list of {@link OkfDocument}s so a
 * producer can hand it to a writer, an archive or an MCP client alike.
 * <p>
 * A bundle is conformant when every non-reserved document carries parseable YAML
 * frontmatter with a non-empty {@code type}, and the reserved {@code index.md}
 * and {@code log.md} names keep their defined meaning. Bundles built here target
 * {@link #VERSION}.
 */
public class OkfBundle {

	/** The Open Knowledge Format version the documents of this bundle target. */
	public static final String VERSION = "0.2";

	/** The reserved name of a directory listing, which carries no concept of its own. */
	public static final String INDEX = "index.md";

	/** The reserved name of a bundle's change log, which is likewise no concept of its own. */
	public static final String LOG = "log.md";

	private final List<OkfDocument> documents;

	public OkfBundle(List<OkfDocument> documents) {
		this.documents = List.copyOf(documents);
	}

	public List<OkfDocument> documents() {
		return documents;
	}
}
