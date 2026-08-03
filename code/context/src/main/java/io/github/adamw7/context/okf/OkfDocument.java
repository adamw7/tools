package io.github.adamw7.context.okf;

/**
 * One markdown file of an {@link OkfBundle}: where it sits in the bundle and what
 * it says. The Open Knowledge Format gives a concept its identity through its
 * path, so carrying the path beside the content lets a bundle be written to disk,
 * archived or handed to a client without the producer having to remember where
 * each file belongs.
 *
 * @param path    bundle-relative path, always {@code /}-separated and never
 *                starting with a separator, e.g. {@code tree/ProjectTreeNode.java.md}
 * @param content the markdown content, YAML frontmatter included
 */
public record OkfDocument(String path, String content) {
}
