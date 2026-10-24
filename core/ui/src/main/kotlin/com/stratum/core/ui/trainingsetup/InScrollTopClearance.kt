package com.stratum.core.ui.trainingsetup

import androidx.compose.ui.unit.Dp
import com.stratum.core.ui.theme.StratumSpacing

/**
 * In-scroll top clearance for content that scrolls beneath the glass top bar (§12.8).
 *
 * The clearance is added **inside** the scroll (the padding sits after `verticalScroll` in the
 * modifier chain), so the first row starts below the overlay bar and glazes beneath the glass
 * while scrolled content travels under it. One rhythm step ([StratumSpacing.s12]) plus the
 * route's glass bar clearance ([topClearance], 0 on solid hosts so in-flow geometry is exact).
 */
fun inScrollTopClearance(topClearance: Dp): Dp = StratumSpacing.s12 + topClearance
