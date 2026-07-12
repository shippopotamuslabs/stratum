package com.stratum.domain.program.generation.structure

/**
 * CP-SAT session region filter — ordinal freeze for solver domains:
 * - [UpperOnly] = 0
 * - [LowerOnly] = 1
 * - [Unrestricted] = 2
 *
 * This is **not** a split type (Chest/Quads vs Back/Hams emerge from activation + recovery).
 * Declaration order must stay fixed so warm-start and wire codecs stay compatible.
 */
enum class SessionRegionFilter {
    UpperOnly,
    LowerOnly,
    Unrestricted,
}
