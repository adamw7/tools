# Data Sources

**Load**: `view .claude/skills/data-sources/SKILL.md`

---

## Description

Helps Claude pick and use the `data` module's data sources (CSV, JDBC,
Parquet/DuckDB, JSON, YAML, TOON), keep to the `ColumnarDataSource` vs
forward-only schema contract, and run the uniqueness/key checker.

---

## Use Cases

- "Read this Parquet file" / "load a JDBC query as rows"
- "Are these columns a key?" / "find a smaller key"
- "Add a new data source for format X"

---

## Examples

```
> view .claude/skills/data-sources/SKILL.md
> "Check if CUSTOMER_ID + ORDER_ID is unique in this CSV"
→ InMemoryCSVDataSource + InMemoryUniquenessCheck.exec(...); read getBetterOptions()
```

---

## Notes / Tips

- Pick `InMemory…` when the data fits in heap; `Iterable…` for large/streaming.
- The uniqueness check needs a `ColumnarDataSource` — forward-only JSON/YAML/TOON
  iterables can't be used there by design.
