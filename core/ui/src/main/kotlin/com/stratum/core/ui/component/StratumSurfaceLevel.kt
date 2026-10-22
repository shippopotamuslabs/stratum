package com.stratum.core.ui.component

/** Elevation level for [StratumSurface] (§12.3 CARD). */
enum class StratumSurfaceLevel {
    /** Default card: surface-1, medium corner. */
    Rest,

    /** Raised card: large corner; Neo adds the hard-offset shadow. */
    Elevated,

    /** Inset well: sunken container, no border or shadow in either family. */
    Sunken,
}
