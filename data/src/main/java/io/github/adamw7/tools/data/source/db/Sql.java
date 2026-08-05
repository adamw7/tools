package io.github.adamw7.tools.data.source.db;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.SQLException;

/**
 * Runs JDBC work that reports failure as a checked {@link SQLException} and
 * re-raises it the way the data sources report every other read failure: an
 * {@link UncheckedIOException}, so a caller iterating a source handles one kind of
 * failure whether it is backed by a file or by a database.
 *
 * <p>Every JDBC call in this package needs that same wrapping, so it lives here
 * rather than in a {@code try}/{@code catch} around each of them — which is what
 * the sources carried before, once per call.
 */
final class Sql {

	/** JDBC work that answers with a value. */
	@FunctionalInterface
	interface Query<T> {
		T run() throws SQLException;
	}

	/** JDBC work that answers with nothing. */
	@FunctionalInterface
	interface Call {
		void run() throws SQLException;
	}

	private Sql() {
	}

	static <T> T answering(Query<T> query) {
		try {
			return query.run();
		} catch (SQLException e) {
			throw new UncheckedIOException(new IOException(e));
		}
	}

	static void running(Call call) {
		answering(() -> {
			call.run();
			return null;
		});
	}
}
