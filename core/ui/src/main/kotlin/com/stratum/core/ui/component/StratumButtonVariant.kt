package com.stratum.core.ui.component

/**
 * Button shape language for [StratumButton] (Button Shape Gospel, §12.7).
 * One screen uses one language, never mixed in a row; the square variants are
 * for dense surfaces (toolbars, filter bars, table-like actions).
 */
enum class StratumButtonVariant {
    /** Gospel default: full pill (`ShippopotamusButtonShape`). */
    Pill,

    /** Square variant, medium (`ShippopotamusButtonShapeSquare`, 12.dp under Tonal). */
    Square,

    /** Square variant, large (16.dp under Tonal). */
    SquareLarge,
}
