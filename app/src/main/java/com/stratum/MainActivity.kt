package com.stratum

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StratumTheme {
                StratumApp()
            }
        }
    }
}

@Composable
private fun StratumApp(modifier: Modifier = Modifier) {
    StratumScaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Greeting(
            name = "Stratum",
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
fun Greeting(
    name: String,
    modifier: Modifier = Modifier,
) {
    StratumText(
        text = "Hello $name!",
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        Greeting("Stratum")
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingNeoPreview() {
    StratumTheme(themeId = StratumThemeId.NeoBrutalist) {
        Greeting("Stratum")
    }
}
