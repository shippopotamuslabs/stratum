// STR-142 allowlist: hero spacers (28.dp) and 2.dp hairlines pinned; row insets adopted.
@file:Suppress("ForbiddenRawSpacingLiteralInUi")
package com.stratum.feature.subscription.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stratum.core.ui.component.StratumActiveBadge
import com.stratum.core.ui.component.StratumBrandFooter
import com.stratum.core.ui.component.StratumButton
import com.stratum.core.ui.component.StratumDetailScaffold
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextButton
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.legal.LegalUrls
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.subscription.PaywallProductUi
import com.stratum.feature.subscription.PaywallUiState
import com.stratum.feature.subscription.R

private const val GOOGLE_PLAY_SUBS_URL =
    "https://play.google.com/store/account/subscriptions?package=com.aether.stratum"

@Composable
fun PaywallScreen(
    uiState: PaywallUiState,
    onPurchaseClick: () -> Unit,
    onRestorePurchasesClick: () -> Unit,
    onSelectProduct: (String) -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDismissable: Boolean = true,
    snackbarHostState: SnackbarHostState? = null,
    onRetryClick: () -> Unit = {},
) {
    val colors = StratumThemeAccessor.colors
    val closeLabel = stringResource(R.string.paywall_close)

    // Glass (§12.8, STR-161): under the route's glass host the top bar overlays
    // and the paywall column carries the top clearance inside its scroll, so
    // content scrolls beneath the glass. Solid hosts keep the in-flow geometry.
    StratumDetailScaffold(
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        topBar = {
            StratumTopBar(
                title = "",
                actions = {
                    if (isDismissable) {
                        StratumIconButton(
                            onClick = onCloseClick,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = closeLabel,
                                tint = colors.textMuted,
                            )
                        }
                    }
                },
            )
        },
    ) { chrome ->
        when (uiState) {
            PaywallUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.accent)
                }
            }
            is PaywallUiState.Error -> {
                PaywallErrorContent(
                    message = uiState.message,
                    onRetryClick = onRetryClick,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = chrome.top)
                        .padding(StratumSpacing.s24),
                )
            }
            is PaywallUiState.Content -> {
                val selected = uiState.selectedProduct
                if (selected == null) {
                    PaywallErrorContent(
                        message = stringResource(R.string.paywall_load_error),
                        onRetryClick = onRetryClick,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = chrome.top)
                            .padding(StratumSpacing.s24),
                    )
                } else {
                    PaywallContent(
                        state = uiState,
                        selectedProduct = selected,
                        onPurchaseClick = onPurchaseClick,
                        onRestorePurchasesClick = onRestorePurchasesClick,
                        onSelectProduct = onSelectProduct,
                        topClearance = chrome.top,
                    )
                }
            }
        }
    }
}

@Composable
private fun PaywallErrorContent(
    message: String,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val typography = StratumThemeAccessor.typography
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            StratumText(
                text = stringResource(R.string.paywall_error_title),
                style = typography.titleMedium,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(StratumSpacing.s8))
            StratumText(
                text = message,
                style = typography.bodyLarge,
                muted = true,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(StratumSpacing.s16))
            StratumButton(
                text = stringResource(R.string.paywall_retry),
                onClick = onRetryClick,
            )
        }
    }
}

@Composable
private fun PaywallContent(
    state: PaywallUiState.Content,
    selectedProduct: PaywallProductUi,
    onPurchaseClick: () -> Unit,
    onRestorePurchasesClick: () -> Unit,
    onSelectProduct: (String) -> Unit,
    topClearance: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val typography = StratumThemeAccessor.typography
    val uriHandler = LocalUriHandler.current
    val trialDays = selectedProduct.freeTrialDays

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = StratumSpacing.s24)
            .padding(top = StratumSpacing.s16 + topClearance, bottom = StratumSpacing.s16),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (trialDays > 0) {
            StratumActiveBadge(
                label = stringResource(R.string.paywall_trial_badge, trialDays),
                active = true,
            )
            Spacer(modifier = Modifier.height(StratumSpacing.s16))
        }

        StratumText(
            text = stringResource(R.string.paywall_title),
            style = typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
            ),
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(StratumSpacing.s8))

        StratumText(
            text = stringResource(R.string.paywall_subtitle),
            style = typography.bodyLarge,
            muted = true,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(28.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(StratumSpacing.s16),
        ) {
            BenefitRow(
                icon = Icons.Default.AutoAwesome,
                title = stringResource(R.string.paywall_benefit_programs_title),
                description = stringResource(R.string.paywall_benefit_programs_body),
            )
            BenefitRow(
                icon = Icons.Default.FitnessCenter,
                title = stringResource(R.string.paywall_benefit_progression_title),
                description = stringResource(R.string.paywall_benefit_progression_body),
            )
            BenefitRow(
                icon = Icons.Default.CloudSync,
                title = stringResource(R.string.paywall_benefit_imports_title),
                description = stringResource(R.string.paywall_benefit_imports_body),
            )
            BenefitRow(
                icon = Icons.AutoMirrored.Filled.ShowChart,
                title = stringResource(R.string.paywall_benefit_analytics_title),
                description = stringResource(R.string.paywall_benefit_analytics_body),
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        StratumText(
            text = stringResource(R.string.paywall_choose_plan),
            style = typography.sectionLabel,
            muted = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(StratumSpacing.s12))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
        ) {
            state.products.forEach { product ->
                val isSelected = product.productId == state.selectedProductId
                PlanSelectionCard(
                    product = product,
                    isSelected = isSelected,
                    onClick = { onSelectProduct(product.productId) },
                )
            }
        }

        Spacer(modifier = Modifier.height(StratumSpacing.s12))

        if (!state.isEntitled) {
            StratumText(
                text = stringResource(
                    R.string.paywall_auto_renew,
                    selectedProduct.formattedPrice,
                    selectedProduct.billingPeriodText,
                ),
                style = typography.sectionLabel,
                color = colors.accent,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(StratumSpacing.s24))

        if (state.errorMessage != null) {
            StratumText(
                text = state.errorMessage,
                style = typography.sectionLabel,
                color = colors.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = StratumSpacing.s12),
            )
        }

        if (state.isEntitled) {
            if (!state.isComplimentary) {
                StratumButton(
                    text = stringResource(R.string.paywall_cta_manage),
                    onClick = { uriHandler.openUri(GOOGLE_PLAY_SUBS_URL) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                StratumButton(
                    text = stringResource(R.string.paywall_cta_lifetime),
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            val ctaText = if (selectedProduct.freeTrialDays > 0) {
                stringResource(R.string.paywall_cta_trial, selectedProduct.freeTrialDays)
            } else {
                stringResource(
                    R.string.paywall_cta_subscribe,
                    selectedProduct.formattedPrice,
                    selectedProduct.billingPeriodText,
                )
            }

            StratumButton(
                text = ctaText,
                onClick = onPurchaseClick,
                enabled = !state.isPurchasing && !state.isRestoring,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(StratumSpacing.s8))

        if (!state.isEntitled) {
            StratumTextButton(
                text = if (state.isRestoring) {
                    stringResource(R.string.paywall_restoring)
                } else {
                    stringResource(R.string.paywall_restore)
                },
                onClick = onRestorePurchasesClick,
                enabled = !state.isPurchasing && !state.isRestoring,
            )
        }

        Spacer(modifier = Modifier.height(StratumSpacing.s16))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StratumText(
                text = stringResource(R.string.paywall_terms),
                style = typography.sectionLabel,
                muted = true,
                // §12.5: heightIn floors the legal links at the 48dp
                // target height at any font scale; the padding is the
                // visual breathing room inside that target.
                modifier = Modifier
                    .clickable { uriHandler.openUri(LegalUrls.TERMS) }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = StratumSpacing.s8, vertical = StratumSpacing.s16),
            )
            StratumText(
                text = "•",
                style = typography.sectionLabel,
                muted = true,
            )
            StratumText(
                text = stringResource(R.string.paywall_privacy),
                style = typography.sectionLabel,
                muted = true,
                modifier = Modifier
                    .clickable { uriHandler.openUri(LegalUrls.PRIVACY) }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = StratumSpacing.s8, vertical = StratumSpacing.s16),
            )
        }

        Spacer(modifier = Modifier.height(StratumSpacing.s8))

        StratumBrandFooter()
    }
}

@Composable
private fun PlanSelectionCard(
    product: PaywallProductUi,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val typography = StratumThemeAccessor.typography
    val shapes = StratumThemeAccessor.shapes

    val borderColor = if (isSelected) colors.accent else colors.borderSubtle
    val backgroundColor = if (isSelected) colors.surfaceRaised else colors.surfaceSunken
    val title = if (product.isAnnual) {
        stringResource(R.string.paywall_plan_annual)
    } else {
        stringResource(R.string.paywall_plan_monthly)
    }
    val description = if (product.isAnnual) {
        stringResource(R.string.paywall_billed_annually, product.formattedPrice)
    } else {
        stringResource(R.string.paywall_billed_monthly, product.formattedPrice)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shapes.panel)
            .background(color = backgroundColor, shape = shapes.panel)
            .border(
                width = if (isSelected) 2.dp else shapes.borderWidth,
                color = borderColor,
                shape = shapes.panel,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s16),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) colors.accent else colors.surfaceSunken)
                    .border(
                        width = 1.5.dp,
                        color = if (isSelected) colors.accent else colors.borderStrong,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.paywall_selected),
                        tint = colors.onAccent,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(StratumSpacing.s16))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
                ) {
                    StratumText(
                        text = title,
                        style = typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                    )
                    if (product.badge != null) {
                        StratumActiveBadge(
                            label = product.badge,
                            active = true,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                StratumText(
                    text = description,
                    style = typography.bodyLarge,
                    muted = true,
                )
            }

            Spacer(modifier = Modifier.width(StratumSpacing.s8))

            Column(horizontalAlignment = Alignment.End) {
                StratumNumber(
                    text = product.pricePerMonthText,
                    style = typography.dataLarge.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) colors.accent else colors.textPrimary,
                    ),
                )
                StratumText(
                    text = product.formattedPrice,
                    style = typography.bodyLarge,
                    muted = true,
                )
            }
        }
    }
}

@Composable
private fun BenefitRow(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val typography = StratumThemeAccessor.typography
    val shapes = StratumThemeAccessor.shapes

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(shapes.button)
                .background(colors.surfaceSunken),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.width(StratumSpacing.s16))

        Column(modifier = Modifier.weight(1f)) {
            StratumText(
                text = title,
                style = typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            StratumText(
                text = description,
                style = typography.bodyLarge,
                muted = true,
            )
        }
    }
}
