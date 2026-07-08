package com.stratum.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stratum.R
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import kotlinx.serialization.Serializable

/** Nav3 key for the Settings screen (stub). */
@Serializable
data object SettingsKey : NavKey

/**
 * Registers the Settings entry in a Nav3 [EntryProviderScope]. A minimal placeholder until the
 * settings feature slice is built.
 */
fun EntryProviderScope<NavKey>.settingsEntry(
    onBackClick: () -> Unit,
) {
    entry<SettingsKey> {
        SettingsScreen(onBackClick = onBackClick)
    }
}

@Composable
private fun SettingsScreen(onBackClick: () -> Unit) {
    StratumScaffold(
        topBar = {
            StratumTopBar(
                title = stringResource(R.string.settings_title),
                navigationIcon = {
                    StratumTopBarBackButton(
                        onClick = onBackClick,
                        contentDescription = stringResource(R.string.settings_navigate_back),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StratumText(
                text = stringResource(R.string.settings_placeholder),
                muted = true,
                textAlign = TextAlign.Center,
            )
        }
    }
}
