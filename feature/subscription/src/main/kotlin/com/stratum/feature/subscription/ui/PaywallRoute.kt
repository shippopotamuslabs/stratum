package com.stratum.feature.subscription.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.component.UserMessageEffect
import com.stratum.feature.subscription.PaywallUiEvent
import com.stratum.feature.subscription.PaywallUiState
import com.stratum.feature.subscription.PaywallViewModel

@Composable
@Suppress("UNUSED_PARAMETER")
fun PaywallRoute(
    onDismiss: () -> Unit,
    onNavigateToTerms: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    isDismissable: Boolean = true,
    modifier: Modifier = Modifier,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val userMessage = (uiState as? PaywallUiState.Content)?.userMessage

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                PaywallUiEvent.PurchaseSuccess -> onDismiss()
                is PaywallUiEvent.ShowMessage -> Unit
            }
        }
    }

    UserMessageEffect(
        userMessage = userMessage,
        snackbarHostState = snackbarHostState,
        onUserMessageShown = viewModel::userMessageShown,
    )

    PaywallScreen(
        uiState = uiState,
        isDismissable = isDismissable,
        snackbarHostState = snackbarHostState,
        onPurchaseClick = viewModel::onPurchaseClicked,
        onRestorePurchasesClick = viewModel::onRestorePurchasesClicked,
        onSelectProduct = viewModel::onSelectProduct,
        onCloseClick = onDismiss,
        onRetryClick = viewModel::loadProductDetails,
        modifier = modifier,
    )
}
