# Callable Function Versioning (STR-48)

Mobile clients update slowly: when a Firebase Callable's request/response
shape changes, old app versions in the wild must keep working. This guide
defines the one versioning scheme used by every callable in `functions/src`.
Companion code: `functions/src/versioning/callableVersioning.ts`.
Out of scope: per-version JSON Schema contract tests (STR-49).

## 1. Mechanism: version in the function name

Breaking changes ship as a **new function with a `_v<N>` suffix**. The client
calls exactly one name per adapter; routing happens at the Firebase layer
(the function name is the endpoint path, so this is the callable equivalent
of version-in-URL-path per Google AIP-185).

```ts
// index.ts — v1 untouched, v2 deployed alongside during the window.
export { inferEquipmentIncrements, inferEquipmentIncrements_v2 };
```

```kotlin
// FirebaseIncrementScanAdapter.kt — new app versions flip one constant.
const val CALLABLE_NAME = "inferEquipmentIncrements_v2"
```

All currently deployed names are **implicit v1** — they are never renamed to
`_v1` (a rename would break every installed client for zero benefit). The
first breaking change to a callable mints its `_v2`.

Why not the alternatives:

- **Version field in the body** (`data.apiVersion`): mixes two contracts in
  one deployment, so per-version timeout/memory/secrets config and
  per-version deploy/rollback are lost, and every handler pays an
  in-process dispatch tax forever. Rejected.
- **Version header** (Stripe's `Stripe-Version`, AIP-185's
  `X-Goog-Api-Version`): impossible — the callable protocol rejects requests
  carrying any header outside its allowlist (`Content-Type`, `Authorization`,
  `Firebase-Instance-ID-Token`, `X-Firebase-AppCheck`).

## 2. What counts as breaking

Additive changes **never** bump the version (same rule as Google and Stripe).
Within a major version the only allowed evolution is:

| Change | Version bump? |
|--------|---------------|
| New optional request field (server defaults when absent) | No |
| New response field (clients ignore unknown fields) | No |
| New enum value in a response (clients fall back) | No |
| Remove/rename a request or response field | Yes — new `_v<N>` |
| Narrow a field (optional → required, looser → stricter validation) | Yes — new `_v<N>` |
| Change a field's type or units | Yes — new `_v<N>` |

Client parsers must stay tolerant: unknown fields ignored, unknown enum
values mapped to a sane default, malformed list items dropped. The response
shape is a contract — see STR-49 for the CI enforcement.

## 3. Deprecation policy

- Support **current + previous major** only. When `_v3` ships, `_v1` is
  deleted; `_v2` becomes the previous major.
- Minimum window: **90 days** after the superseding app version reaches 100%
  on Play. (Shorter than Google's 180-day guidance because Stratum has one
  first-party client, not a public API.)
- Sunset = **delete the old export**. There is no graceful-degradation shim:
  old clients that call a deleted function get `404 / not-found`, which the
  adapters already convert into their normal failure paths (e.g. manual
  entry fallback for the increment scan).

## 4. How deprecation is communicated

Response **headers cannot carry the signal** — the callable protocol fixes
the response headers to `Content-Type` only. Deprecation travels in-band,
in an optional `_meta` block spread into the `result` object:

```jsonc
// result of a deprecated v1 during its sunset window.
{
  "unit": "lb",
  "tiers": [ /* … */ ],
  "microStops": [],
  "_meta": {
    "version": 1,
    "deprecation": {
      "sunsetDate": "2027-03-01",
      "message": "inferEquipmentIncrements is superseded by inferEquipmentIncrements_v2."
    }
  }
}
```

Rules for `_meta`:

- Every versioned callable returns `_meta.version` (its own major number).
- `_meta.deprecation` is present **only** while that version is deprecated.
- Old clients ignore `_meta` entirely (unknown-field tolerance, §2); new
  clients may log it. No client behavior may depend on `_meta`.
- Out-of-band channels (Play release notes, staged rollout) announce the
  migration; `_meta` is the machine-readable backstop, not the announcement.

Build the block with `buildResultMeta()` from
`functions/src/versioning/callableVersioning.ts` — never hand-roll the shape.
Log served versions with `logCallableVersion()` so Cloud Logging shows which
majors still receive traffic before a sunset deletion.

## 5. Worked example: deprecating `inferEquipmentIncrements` v1

The reference migration (this issue) establishes the v1 baseline: the
callable returns `{...scan, _meta: {version: 1}}` with no deprecation block,
and `IncrementScanResponseParserTest` proves a `_meta`-carrying payload still
parses. The first breaking change then follows this script:

1. Implement the new contract as `inferEquipmentIncrements_v2` in
   `functions/src/incrementscan/` (new file; v1 file untouched) and export
   both from `index.ts`. Deploy — both versions serve.
2. Add `_meta.deprecation` (`sunsetDate`, `message`) to the v1 result via
   `buildResultMeta(1, { sunsetDate, message })`.
3. Ship the app version flipping `CALLABLE_NAME` to
   `inferEquipmentIncrements_v2`; roll out on Play.
4. Watch `logCallableVersion` output until v1 traffic is negligible and the
   90-day window has elapsed.
5. Delete the v1 export and its files. Done — v2 is now the only major.

## Sources

- Firebase callable protocol: request/response shape and header restrictions —
  <https://firebase.google.com/docs/functions/callable-reference>
- Google AIP-185 (major-version-in-path, coexistence, deprecation windows) —
  <https://google.aip.dev/185>
- Stripe API versioning (additive-within-major, default old behavior) —
  <https://docs.stripe.com/api/versioning>
