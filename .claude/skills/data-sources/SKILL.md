---
name: data-sources
description: Choose and use the data module's sources (CSV, JDBC, Parquet via DuckDB, JSON/YAML/TOON), the ColumnarDataSource vs forward-only contract, and the uniqueness/key checker. Use when reading tabular data, adding a new data source, running a uniqueness check, or when the user says "data source", "uniqueness check", or "find a key".
---

# Data Sources Skill

Pick the right data source in the `data` module, respect the schema contract
that keeps forward-only sources away from schema-dependent callers, and run the
uniqueness checker to find whether a set of columns can serve as a key.

## When to Use
- Reading tabular data from CSV, a JDBC query, Parquet, JSON, YAML, or TOON
- Adding a brand-new data source
- Checking whether columns are unique / finding a smaller key
- The user says "data source" / "uniqueness check" / "find a key"

## In-memory vs iterative — pick first
Every format ships in two variants:
- **`InMemory…`** — loads all rows once (`readAll()`), then runs multiple
  recursive checks cheaply. Use when the data fits in heap.
- **`Iterable…`** — holds one row at a time (tiny heap), but re-reads the source
  for each recursive pass. Use for large data or streaming.

## The schema contract (don't fight it)
- **`IterableDataSource`** — the base forward-only contract: `open()`,
  `nextRow()`, `hasMoreData()`, `reset()`, `nextRows(int batchSize)`.
- **`ColumnarDataSource extends IterableDataSource`** — adds `getColumnNames()`
  for sources whose columns are known up front.
- Forward-only sources that discover keys as they stream — **iterable JSON, YAML,
  TOON** — deliberately do **not** implement `ColumnarDataSource`. Callers that
  need the schema (e.g. the uniqueness check) depend on the narrower
  `ColumnarDataSource`, so a schema-less source can never be handed in and answer
  with `null`. Don't widen `IterableDataSource` to "fix" a compile error — reach
  for a columnar source instead.

## Source picker
| Format | In-memory | Iterable | Notes |
|---|---|---|---|
| CSV | `InMemoryCSVDataSource` | `CSVDataSource` | file path or `InputStream` |
| JDBC | `InMemorySQLDataSource` | `IterableSQLDataSource` | `batchSize` sets JDBC fetch size |
| Parquet | `InMemoryParquetDataSource` | `IterableParquetDataSource` | read via in-process DuckDB, JDBC-like |
| JSON | `InMemoryJSONDataSource` | `IterableJSONDataSource` | nested flattened to dotted paths |
| YAML | `InMemoryYAMLDataSource` | `IterableYAMLDataSource` | same flattening; no size limit |
| TOON | `InMemoryTOONDataSource` | `IterableTOONDataSource` | compact, LLM-friendly |

- All file-based sources accept a **file path or an `InputStream`** and
  transparently **decompress `.gz`** with no extra config.
- JSON/YAML flatten nested objects to dotted keys, e.g.
  `people[0].address.city`.

## Uniqueness check
```java
AbstractUniqueness check = new InMemoryUniquenessCheck();   // or NoMemoryUniquenessCheck
check.setDataSource(new InMemorySQLDataSource(connection, query));
Result result = check.exec("COLUMN1", "COLUMN2", "COLUMN3");
if (result.isUnique()) {
    for (Result better : result.getBetterOptions()) {   // smaller candidate keys
        log.info(better);
    }
}
```
The checker consumes a `ColumnarDataSource`, asks whether the given columns form
a key, and searches for a smaller one. `InMemory…` runs recursive passes over a
single load; `NoMemory…` re-reads the source per pass for a tiny heap.

## Adding a new source
Implement `IterableDataSource` (five methods; `nextRows` is a default). If it can
report its columns, also implement `ColumnarDataSource` so schema-dependent
callers can use it. Add `readAll()` via `InMemoryDataSource` for an in-memory
variant. Keep JDBC specifics in `source.db` (ArchUnit enforces this).

## References
- `README.md` — *Data* (worked uniqueness example, source list, interfaces)
- `AGENTS.md` — *Data* summary (source of truth)
- `data/.../uniqueness/mcp/MCP_USAGE.md` — running the uniqueness MCP server
