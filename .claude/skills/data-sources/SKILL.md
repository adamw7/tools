---
name: data-sources
description: Choose and use the data module's sources (CSV, JDBC, Parquet via DuckDB, JSON/YAML/TOON), the ColumnarDataSource vs forward-only contract, and the uniqueness/key checker. Use when reading tabular data, adding a new data source, running a uniqueness check, or when the user says "data source", "uniqueness check", or "find a key".
---

# Data Sources Skill

Pick the right data source in the `data` module, respect the schema contract
that keeps forward-only sources away from schema-dependent callers, and run the
uniqueness checker to find whether a set of columns can serve as a key.

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
- `getColumnNames()` **never returns `null`**: a columnar source that cannot name
  its columns throws `IllegalStateException` saying why. `IterableSQLDataSource`
  does it for a source read before `open()`; `CSVDataSource` does it for one built
  without a `columnsRow` (`new CSVDataSource(fileName)` defaults it to `-1`), which
  otherwise surfaced two frames away as `execForAllColumns()` failing with
  `Wrong input: null`. A CSV header survives `close()` and is only dropped —
  and reloaded — by `reset()`.

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
- The **JDBC sources run the query verbatim** through a plain `Statement`: they
  bind nothing, so a query built from untrusted input is an injection at the
  caller's keyboard. Their javadoc says so, and `data/spotbugs-exclude.xml`
  accepts `SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE` for those two methods only.
- A **read that breaks part-way through fails**, rather than reading as a short
  file. The `Scanner`-backed sources (`CSVDataSource` and the map-backed
  in-memory JSON/YAML/TOON ones) go through `AbstractFileSource.hasNextLine()`,
  which raises `UncheckedIOException` when `Scanner.ioException()` is set —
  `Scanner` swallows the failure by design and just stops producing tokens, so a
  truncated transfer, a corrupt GZip member or a disk error would otherwise look
  like the end of the data and let the uniqueness check call a column unique on
  half a file. The forward-only sources on `AbstractIterableFileSource` already
  propagate. A new source must do one or the other.

### Confining a source to a directory
Every path a source is given is canonicalised and refused if it climbs out with
`..`. To also **hold one source inside one directory** — always do this when the
path comes from outside the process, as an MCP tool's does — pass it an
`AllowedPaths`:

```java
AllowedPaths uploads = AllowedPaths.under(Path.of("/data/uploads"));   // throws if absent
new InMemoryCSVDataSource(fileName, 1, uploads);                       // SecurityException outside it
new InMemoryCSVDataSource(fileName, 1, AllowedPaths.anywhere());       // unrestricted
```

The boundary belongs to the source it was handed to, so two sources can hold two
different roots, and symlinks are followed before the check so one inside the
root cannot point out of it. Every file source has a constructor taking one; the
constructors without it fall back to the process-wide base directory of
`PathValidator`, deprecated for removal because it is one boundary for the whole
JVM that any caller can move or clear.

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

A **row with fewer columns than the key reads** — a CSV line with a delimiter
missing — fails with `RaggedRowException`, naming the data row's 1-based position
(the header and any skipped comment line excluded), the arity the key needs and
the one the row has. It replaces the `ArrayIndexOutOfBoundsException` the
projection used to throw, which named neither the row nor the column. Columns the
key does not read are never touched, so a short row only fails a key that reaches
past its end.

## Data structures (`data.structure`)
Open-addressing collections with double hashing, for when `HashMap`'s per-entry
node allocation is what hurts:
- `OpenAddressingMap<K, V>` / `OpenAddressingSet<E>` — extend `AbstractMap` /
  `AbstractSet`, so `equals`/`hashCode`/`toString` are the contract's. A `null`
  key or element is refused by `put`/`add` with a `NullPointerException` and
  reported absent by the lookups; the view iterators are fail-fast.
- `IntKeyOpenAddressingMap<V>` — primitive `int[]` keys, so lookups and inserts
  never box. It deliberately does **not** implement `Map` (that contract is
  defined over `Object` keys and would reintroduce the boxing it exists to
  avoid), and mirrors the map operations with primitive keys instead.
- All three store a `null` value faithfully and report it from `containsKey`;
  only `get` cannot tell a stored `null` from an absent key. **None is
  thread-safe.**

## Adding a new source
Implement `IterableDataSource` (five methods; `nextRows` is a default). If it can
report its columns, also implement `ColumnarDataSource` so schema-dependent
callers can use it. Add `readAll()` via `InMemoryDataSource` for an in-memory
variant. Keep JDBC specifics in `source.db` (ArchUnit enforces this).

## References
- `README.md` — *Data* (worked uniqueness example, source list, interfaces)
- `AGENTS.md` — *Data* summary (source of truth)
- `data/.../uniqueness/mcp/MCP_USAGE.md` — running the uniqueness MCP server
