package com.stratum.core.ui.component

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.stratum.core.ui.theme.StratumSpacing
import kotlinx.coroutines.flow.first

/**
 * Horizontal strip the caller syncs with its own scrolling list.
 *
 * [itemContent] owns taps and selection chrome. This strip does not scroll the
 * caller's list and does not pick a landing offset. When [selectedIndex] changes
 * it scrolls only far enough to keep that item on screen; an item that is already
 * fully visible does not move. [trailingContent] is a separate slot and is not a
 * selectable index.
 */
@Composable
fun StratumSyncedItemStrip(
    itemCount: Int,
    selectedIndex: Int?,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(horizontal = StratumSpacing.s16),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(StratumSpacing.s4),
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    itemKey: (index: Int) -> Any = { it },
    trailingKey: Any = "stratum-synced-strip-trailing",
    trailingContent: (@Composable () -> Unit)? = null,
    itemContent: @Composable (index: Int, selected: Boolean) -> Unit,
) {
    LaunchedEffect(selectedIndex, itemCount) {
        val index = selectedIndex ?: return@LaunchedEffect
        if (index !in 0 until itemCount) return@LaunchedEffect
        // One shot per selection. Collecting layout forever would fight a manual drag.
        val layout =
            snapshotFlow { state.layoutInfo }
                .first { it.totalItemsCount > 0 }
        val visible = layout.visibleItemsInfo.firstOrNull { it.index == index }
        val reveal =
            stripReveal(
                itemIndex = index,
                itemOffsetPx = visible?.offset,
                itemSizePx = visible?.size,
                viewportStartPx = layout.viewportStartOffset,
                viewportEndPx = layout.viewportEndOffset,
            )
        val scrollToIndex = reveal.scrollToIndex
        if (scrollToIndex != null) {
            state.animateScrollToItem(scrollToIndex)
        } else if (reveal.scrollByPx != 0) {
            state.animateScrollBy(reveal.scrollByPx.toFloat())
        }
    }

    LazyRow(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
    ) {
        items(count = itemCount, key = itemKey) { index ->
            itemContent(index, index == selectedIndex)
        }
        if (trailingContent != null) {
            item(key = trailingKey) {
                trailingContent()
            }
        }
    }
}
