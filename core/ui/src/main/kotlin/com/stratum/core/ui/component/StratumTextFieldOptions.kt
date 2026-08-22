package com.stratum.core.ui.component

/** Optional extras for [StratumTextField] so the composable stays under Detekt's parameter cap. */
data class StratumTextFieldOptions(
    val supportingText: String? = null,
    val minLines: Int = 1,
    val maxLines: Int? = null,
    val showClearButton: Boolean = false,
)
