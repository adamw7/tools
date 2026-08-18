package io.github.adamw7.tools.enforcer.rule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.adamw7.tools.markdown.MarkdownDocument;

/**
 * Reads and parses each input file once per build, however many rules ask for it.
 *
 * <p>The rules are deliberately independent — each names its own inputs and
 * reaches a verdict on them alone — and the cost of that is that they overlap. A
 * project wiring the catalogue has {@code CLAUDE.md} read and parsed by
 * {@code claudeMdFormat}, {@code contextBudget}, {@code memoryImports},
 * {@code crossDocConsistency} and {@code moduleMapConsistency}, and
 * {@code settings.json} read and parsed by {@code settingsJsonValid},
 * {@code hookCommandsValid}, {@code hooksFormat} and {@code permissionsFormat}.
 * Sharing the result costs the rules none of their independence, because what is
 * shared is the file's content and not any rule's reading of it.
 *
 * <p>An entry is keyed by what the file <em>is</em> rather than by where it is:
 * the path together with its modification time and size. A file rewritten during
 * the build — which {@code autoFix} does — therefore misses rather than serving
 * the content it had before the fix, without anything having to remember to
 * invalidate it. Only successful reads and parses are kept; a file that could not
 * be read or did not parse is re-read, since the rule that asked is about to fail
 * the build anyway and a cached failure would have to carry its diagnosis too.
 *
 * <p>The cache is static because a Maven session builds each rule afresh, so
 * anything held per instance is held for exactly one rule. It is bounded and
 * dropped wholesale when it fills: entries are worth keeping for one build, and a
 * build reads a handful of files.
 */
final class DocumentCache {

	/**
	 * Roughly an order of magnitude more entries than a build touches, so the clear
	 * below is a safety net against a long-lived JVM (an IDE, a daemon, the
	 * integration tests' repeated runs) rather than something a build reaches.
	 */
	private static final int MAX_ENTRIES = 512;

	private static final Map<Key, String> TEXT = new ConcurrentHashMap<>();
	private static final Map<Key, MarkdownDocument> MARKDOWN = new ConcurrentHashMap<>();
	private static final Map<Key, JsonNode> JSON = new ConcurrentHashMap<>();

	private DocumentCache() {
	}

	/**
	 * The file's text, read through {@code reader} on a miss.
	 *
	 * @param reader reads the file, answering empty when it is not readable as text
	 */
	static Optional<String> text(File file, Supplier<Optional<String>> reader) {
		return cached(TEXT, file, reader);
	}

	/** The file's parsed Markdown, parsed from {@code content} on a miss. */
	static MarkdownDocument markdown(File file, String content) {
		return cached(MARKDOWN, file, () -> Optional.of(MarkdownDocument.parse(content)))
				.orElseGet(() -> MarkdownDocument.parse(content));
	}

	/**
	 * The file's parsed JSON, parsed through {@code parser} on a miss. A parse that
	 * failed answers empty and is not kept, so the violation it collected is
	 * collected again for the next rule that asks.
	 */
	static Optional<JsonNode> json(File file, Supplier<Optional<JsonNode>> parser) {
		return cached(JSON, file, parser);
	}

	private static <T> Optional<T> cached(Map<Key, T> cache, File file, Supplier<Optional<T>> load) {
		Optional<Key> key = Key.of(file);
		if (key.isEmpty()) {
			return load.get();
		}
		T hit = cache.get(key.get());
		if (hit != null) {
			return Optional.of(hit);
		}
		Optional<T> loaded = load.get();
		loaded.ifPresent(value -> store(cache, key.get(), value));
		return loaded;
	}

	private static <T> void store(Map<Key, T> cache, Key key, T value) {
		if (cache.size() >= MAX_ENTRIES) {
			cache.clear();
		}
		cache.put(key, value);
	}

	/** Drops everything held, so a test can prove a read happened rather than a hit. */
	static void clear() {
		TEXT.clear();
		MARKDOWN.clear();
		JSON.clear();
	}

	/**
	 * What identifies a file's content for the length of a build: where it is, when
	 * it was last written, and how big it is. A file whose stamp cannot be read —
	 * because it is not there, or the filesystem refused — yields no key at all, and
	 * the caller reads it directly: the rule's own missing-file diagnosis is a better
	 * answer than anything this class could invent.
	 */
	private record Key(Path path, long lastModified, long size) {

		static Optional<Key> of(File file) {
			try {
				Path path = file.toPath().toAbsolutePath().normalize();
				BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
				return Optional.of(new Key(path, nanoseconds(attributes), attributes.size()));
			} catch (IOException | RuntimeException e) {
				return Optional.empty();
			}
		}

		/**
		 * The finest modification time the filesystem records. Truncating to
		 * milliseconds would leave a window in which two writes stamp identically, and
		 * an equal-length rewrite inside it would be served from the cache — exactly the
		 * stale read this key exists to prevent.
		 */
		private static long nanoseconds(BasicFileAttributes attributes) {
			return attributes.lastModifiedTime().to(TimeUnit.NANOSECONDS);
		}
	}
}
