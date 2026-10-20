package com.stratum.core.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Button Shape Gospel tokens (§12.7 of `contracts/UI_DESIGN.md`, canonical
 * 2026-10-20) under their canonical cross-app names. Theme specs resolve them
 * per family through [StratumShapes]: Tonal takes the values here verbatim,
 * while Neo Brutalist's derivations collapse onto its own 4dp geometry under
 * the sanctioned carve-out (§11.3 ruling 1).
 */

/** Gospel default button shape: full pill. */
val ShippopotamusButtonShape: Shape = CircleShape

/**
 * Square variant for dense surfaces (toolbars, filter bars, table-like
 * actions), medium size. The large size is 16.dp — per family it is the large
 * rung of the corner scale ([StratumShapes.buttonSquareLarge]).
 */
val ShippopotamusButtonShapeSquare: Shape = RoundedCornerShape(12.dp)

/** Pressed shape: both the pill and the square variant morph to this on press. */
val ShippopotamusPressedShape: Shape = RoundedCornerShape(12.dp)

/**
 * Resting corner the pill's press morph animates FROM (§12.7 sketch value).
 * A corner radius renders capped at half the button's height, so the value
 * must exceed twice the tallest gospel button (56.dp large) to read as a full
 * pill at rest; it is only meaningful inside the gospel button heights
 * (40/56 dp), which is every button the system ships.
 */
val ShippopotamusPillRestCorner: Dp = 100.dp
