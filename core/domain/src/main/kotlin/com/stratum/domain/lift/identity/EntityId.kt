package com.stratum.domain.lift.identity

import com.stratum.domain.program.identity.ProgramId

/**
 * Marker for type-safe entity identifiers (the pocket-crew identity pattern).
 *
 * Each aggregate gets its own `@JvmInline value class XxxId(override val value: String)
 * : EntityId`. Distinct ID types are not assignable to each other at compile time, so a
 * `LiftId` cannot be passed where a `ProgramId` (OS-6) is expected — the bug the compiler
 * now catches instead of the user. Storage layers unwrap `.value` only at true String
 * boundaries (Room `@TypeConverter`, asset JSON, nav routes).
 *
 * One shared marker (rather than one per aggregate) lets later layers refer to "any Stratum
 * id" generically if needed, while the per-aggregate value class keeps call sites specific.
 */
interface EntityId {
    val value: String
}
