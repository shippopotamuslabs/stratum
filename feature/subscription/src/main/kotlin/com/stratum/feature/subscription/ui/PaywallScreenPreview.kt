package com.stratum.feature.subscription.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId
import com.stratum.feature.subscription.PaywallCopy
import com.stratum.feature.subscription.PaywallProductUi
import com.stratum.feature.subscription.PaywallUiState

@PreviewLightDark
@Composable
private fun PaywallScreenPreview() {
    PaywallPreviewContent(
        family = StratumThemeId.Tonal,
        uiState = previewPaywallContent(),
    )
}

@PreviewLightDark
@Composable
private fun PaywallScreenErrorPreview() {
    PaywallPreviewContent(
        family = StratumThemeId.Tonal,
        uiState = previewPaywallError(),
    )
}

@PreviewLightDark
@Composable
private fun PaywallScreenEntitledPreview() {
    PaywallPreviewContent(
        family = StratumThemeId.Tonal,
        uiState = previewPaywallEntitled(),
    )
}

@Composable
internal fun PaywallPreviewContent(
    family: StratumThemeId,
    uiState: PaywallUiState,
) {
    StratumTheme(themeId = family) {
        PaywallScreen(
            uiState = uiState,
            onPurchaseClick = {},
            onRestorePurchasesClick = {},
            onSelectProduct = {},
            onCloseClick = {},
            onRetryClick = {},
        )
    }
}

internal fun previewPaywallContent(): PaywallUiState =
    PaywallUiState.Content(
        products = previewProducts(),
        selectedProductId = "stratum_membership_annual",
    )

internal fun previewPaywallError(): PaywallUiState =
    PaywallUiState.Error(message = PaywallCopy.LOAD_ERROR)

internal fun previewPaywallEntitled(): PaywallUiState =
    PaywallUiState.Content(
        products = previewProducts(),
        selectedProductId = "stratum_membership_annual",
        isEntitled = true,
    )

internal fun previewProducts() = listOf(
    PaywallProductUi(
        productId = "stratum_membership_annual",
        formattedPrice = "$49.99",
        pricePerMonthText = "$4.17 / mo",
        freeTrialDays = 14,
        billingPeriodText = "year",
        badge = "SAVE 48%",
        title = "Annual Plan",
        description = "Billed annually at $49.99",
        isAnnual = true,
    ),
    PaywallProductUi(
        productId = "stratum_membership_monthly",
        formattedPrice = "$7.99",
        pricePerMonthText = "$7.99 / mo",
        freeTrialDays = 0,
        billingPeriodText = "month",
        badge = null,
        title = "Monthly Plan",
        description = "Billed monthly at $7.99",
        isAnnual = false,
    ),
)
