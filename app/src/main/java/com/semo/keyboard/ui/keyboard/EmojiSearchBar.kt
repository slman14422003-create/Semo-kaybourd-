package com.semo.keyboard.ui.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.semo.keyboard.R
import com.semo.keyboard.domain.model.KeyboardUiState
import com.semo.keyboard.ui.appString
import com.semo.keyboard.ui.theme.SemoKeyboardColors

@Composable
internal fun EmojiSearchBar(
    state: KeyboardUiState,
    colors: SemoKeyboardColors,
    stripHeight: Dp,
    viewModel: KeyboardViewModel
) {
    val ui = state.emojiSearch ?: return
    val enter = rememberBarEnter()
    val pillHeight = (stripHeight - 8.dp).coerceAtLeast(34.dp)
    val hint = appString(R.string.emoji_search_hint)
    val empty = appString(R.string.emoji_no_results)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(stripHeight)
            .barEntrance(enter)
            .padding(horizontal = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BarCapsule(
            colors = colors,
            height = pillHeight,
            borderColor = colors.keyAccent.copy(alpha = 0.55f),
            glow = 0f,
            glowColor = colors.keyAccent,
            modifier = Modifier.weight(0.4f)
        ) {
            Text(
                text = ui.query.ifEmpty { hint },
                color = if (ui.query.isEmpty()) colors.textMuted else colors.text,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .weight(0.6f)
                .height(stripHeight),
            contentAlignment = Alignment.CenterStart
        ) {
            if (ui.results.isEmpty() && ui.query.isNotBlank()) {
                Text(text = empty, color = colors.textMuted, fontSize = 13.sp)
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(ui.results, key = { it }) { emoji ->
                        PressBox(
                            modifier = Modifier.size(width = 40.dp, height = stripHeight),
                            onClick = { viewModel.onEmojiPressed(emoji) }
                        ) { _ ->
                            Text(text = emoji, fontSize = 24.sp)
                        }
                    }
                }
            }
        }
        BarCloseButton(colors, stripHeight, pillHeight) {
            viewModel.onSelectionTick()
            viewModel.closeEmojiSearch()
        }
    }
}
