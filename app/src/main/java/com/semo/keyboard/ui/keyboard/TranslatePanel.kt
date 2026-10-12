package com.semo.keyboard.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.semo.keyboard.R
import com.semo.keyboard.domain.logic.TranslateLanguages
import com.semo.keyboard.domain.model.KeyboardUiState
import com.semo.keyboard.ui.theme.SemoKeyboardColors

@Composable
internal fun TranslateBar(
    state: KeyboardUiState,
    colors: SemoKeyboardColors,
    stripHeight: Dp,
    viewModel: KeyboardViewModel
) {
    val ui = state.translate ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(stripHeight)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PressBox(modifier = Modifier.size(width = 52.dp, height = 40.dp), onClick = {
            viewModel.onSelectionTick()
            viewModel.toggleTranslatePicker()
        }) { pressed ->
            Box(
                modifier = Modifier
                    .size(width = 42.dp, height = 30.dp)
                    .background(if (pressed) colors.keyPressed else colors.keySpecial, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = TranslateLanguages.shortCode(state.translateTarget),
                    color = colors.keyAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(34.dp)
                .background(colors.chip, RoundedCornerShape(17.dp))
                .clickable {
                    if (ui.error) viewModel.retryTranslate() else viewModel.applyTranslation()
                }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val hint = when {
                    ui.error -> stringResource(R.string.translate_error)
                    ui.result.isNotEmpty() -> ui.result
                    ui.loading -> ""
                    else -> stringResource(R.string.translate_hint)
                }
                Text(
                    text = hint,
                    color = if (ui.result.isNotEmpty() && !ui.error) colors.text else colors.textMuted,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = TextDirection.Content),
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (ui.loading) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.6.dp,
                        color = colors.keyAccent
                    )
                }
            }
        }
        PressBox(modifier = Modifier.size(width = 48.dp, height = 40.dp), onClick = {
            viewModel.onSelectionTick()
            viewModel.closeTranslate()
        }) { pressed ->
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(if (pressed) colors.keyPressed else colors.keySpecial, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "✕", color = colors.text, fontSize = 13.sp)
            }
        }
    }
}

@Composable
internal fun TranslatePanel(
    state: KeyboardUiState,
    colors: SemoKeyboardColors,
    height: Dp,
    viewModel: KeyboardViewModel
) {
    val all = remember { TranslateLanguages.sorted() }
    val recents = state.translateRecents.filter { it in all }
    val names = remember { HashMap<String, Pair<String, String>>() }
    fun info(code: String): Pair<String, String> =
        names.getOrPut(code) { TranslateLanguages.displayName(code) to TranslateLanguages.nativeName(code) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (recents.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.translate_recent),
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 6.dp, top = 2.dp)
                )
            }
            items(recents, key = { "r$it" }) { code ->
                LanguageCell(code, info(code), code == state.translateTarget, colors, viewModel)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.translate_all),
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 6.dp, top = 6.dp)
                )
            }
        }
        items(all, key = { "a$it" }) { code ->
            LanguageCell(code, info(code), code == state.translateTarget, colors, viewModel)
        }
    }
}

@Composable
private fun LanguageCell(
    code: String,
    names: Pair<String, String>,
    selected: Boolean,
    colors: SemoKeyboardColors,
    viewModel: KeyboardViewModel
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(if (selected) colors.keyAccent else colors.key, RoundedCornerShape(10.dp))
            .clickable {
                viewModel.onSelectionTick()
                viewModel.setTranslateTarget(code)
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = names.first,
                color = if (selected) colors.textOnAccent else colors.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(textDirection = TextDirection.Content)
            )
            if (names.second.isNotEmpty() && !names.second.equals(names.first, true)) {
                Text(
                    text = names.second,
                    color = if (selected) colors.textOnAccent.copy(alpha = 0.8f) else colors.textMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = TextDirection.Content)
                )
            }
        }
        Text(
            text = TranslateLanguages.shortCode(code),
            color = if (selected) colors.textOnAccent else colors.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
