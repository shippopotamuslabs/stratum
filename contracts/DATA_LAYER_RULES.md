# DATA_LAYER_RULES.md
**Stratum — Data Layer Contract v1.0**

Binding rules for Room schema, repositories, and persistence patterns in `:core:data`.
Cross-reference `ARCHITECTURE.md` §6 and `AGENTS.md` §4.5 (schema lock).

Never modify this file without an explicit **"Contract Waiver"** from the user.

---

## 1. Typed identifiers

- Domain id value classes (`ProgramId`, `LiftId`, `WorkoutId`, etc.) are the source of truth
  in `:core:domain`.
- Room entity primary and foreign keys store the underlying primitive (`String`, `Long`, etc.)
  and convert via dedicated `TypeConverter`s — never raw untyped strings in repository APIs.
- New id types require a domain value class, converter registration in `EnumConverters` or a
  sibling converter object, and a schema export update when entities change.

---

## 2. EnumConverters registry

- All persisted enums and sealed ordinals map through the central `EnumConverters` (or
  feature-scoped converter types registered on `StratumDatabase`).
- Do not persist enum `name` strings ad hoc in DAO queries; use converter-backed columns so
  renames and exhaustiveness stay compile-time checked.
- When adding an enum to the schema, extend the converter registry and add a contract test
  (`ProgramSchemaContractTest` pattern) that round-trips every variant.

---

## 3. Foreign keys and indexes (history)

- Raw history tables (`logged_sets`, import staging tables, session headers) must declare FK
  relationships before bulk import paths ship.
- Add query indexes for hot paths: session date range scans, lift id lookups, import job id
  joins.
- Repositories must not load entire history tables into memory for filtering — see §5.

---

## 4. Analytics tiering

Stratum separates persistence into tiers for scalability:

| Tier | Purpose | Examples |
|------|---------|----------|
| **Raw** | Immutable logged facts | `logged_sets`, import snapshots |
| **Weekly** | Rolled-up aggregates for dashboards | per-lift weekly volume, session counts |
| **Model** | Derived intelligence outputs | response profiles, pattern features |

- Weekly aggregate tables are distinct from raw `logged_sets`; analytics reads prefer weekly
  tier unless drill-down requires raw rows.
- Derived/model tables are rebuildable from raw + weekly inputs; design pipelines accordingly.

---

## 5. Query patterns

- Prefer SQL window functions, bounded `LIMIT`/`OFFSET`, and indexed range predicates over
  load-all-then-filter in Kotlin.
- Flow-based observation should map at the DAO boundary; repositories return domain types,
  not entities, via mappers.
- Pagination and incremental import reconciliation use explicit cursors or job ids — not
  full-table scans on the main thread.

---

## 6. Provenance fields

Derived analytics and model-tier tables must record:

- `pipelineVersion` — semver or integer pipeline id so rebuilds can detect stale rows
- `extractedAt` / `computedAt` — UTC timestamp of when the row was produced

Raw tier rows use session/set timestamps from the user log; provenance fields apply to
machine-derived outputs only.

---

## 7. Schema version policy

- **Versioning:** every entity change bumps `StratumDatabase` and checks in the exported
  JSON under `core/data/schemas/` (see `ARCHITECTURE.md` §6). Pure additive changes use
  `AutoMigration`; backfill or reshaping requires a manual migration plus a migration test
  (`*Migration*Test` pattern). During early development an uninstall/reinstall may
  substitute for a migration only when no shipped build carries the old schema.
- **Post-release:** additive migrations only with explicit maintainer approval; never drop
  columns users depend on without a data migration plan.
- Schema JSON exports under `core/data/schemas/` are checked in and reviewed with every
  entity change.
- **Schema lock:** no new tables/columns for transient UI state (`is_loading`, `is_editing`,
  etc.) — see `AGENTS.md` §4.5.

---

## 8. Maintenance

Review when:

- Adding history import, analytics, or sync pipelines
- Introducing new id types or aggregate tables
- Changing Room version or migration strategy

Keep aligned with `ARCHITECTURE.md` §6 and repository implementations in `:core:data`.
