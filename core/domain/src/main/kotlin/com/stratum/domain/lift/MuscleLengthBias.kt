package com.stratum.domain.lift

/**
 * Captures where peak tension occurs for a given exercise. Used in exercise selection
 * and program generation to ensure variation between fully stretched and fully contracted profiles.
 */
enum class MuscleLengthBias {
    Lengthened,  // Peak tension in fully stretched position
    Shortened,   // Peak tension in fully contracted position
    MidRange     // Balanced resistance profile or peak tension in mid-range
}
