package com.stratum.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Full-width lift hero image with a letter fallback when [imageUrl] is missing.
 */
@Composable
fun StratumLiftImage(
    name: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    val initial =
        remember(name) {
            name.firstOrNull { it.isLetterOrDigit() }?.uppercase().orEmpty()
        }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(color = colors.surfaceSunken, shape = shapes.panel)
                .border(width = shapes.borderWidth, color = colors.borderSubtle, shape = shapes.panel)
                .clip(shapes.panel),
        contentAlignment = Alignment.Center,
    ) {
        StratumText(
            text = initial,
            style = StratumThemeAccessor.typography.titleLarge,
            muted = true,
        )
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
