package com.semo.keyboard.ui.keyboard

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.semo.keyboard.R
import com.semo.keyboard.domain.logic.KeyboardLayoutProvider
import com.semo.keyboard.domain.model.ClipItem
import com.semo.keyboard.domain.model.EditAction
import com.semo.keyboard.domain.model.KeyAction
import com.semo.keyboard.domain.model.KeyDefinition
import com.semo.keyboard.domain.model.KeyboardLanguage
import com.semo.keyboard.domain.model.KeyboardPage
import com.semo.keyboard.domain.model.KeyboardStyle
import com.semo.keyboard.domain.model.KeyboardUiState
import com.semo.keyboard.domain.model.OneHandMode
import com.semo.keyboard.domain.model.ThemeMode
import com.semo.keyboard.ui.theme.KeyMetrics
import com.semo.keyboard.ui.theme.SemoKeyboardColors
import com.semo.keyboard.ui.theme.keyMetrics
import com.semo.keyboard.ui.theme.semoColors
import kotlin.math.abs
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun tr(arabic: Boolean, ar: String, en: String): String = if (arabic) ar else en

/**
 * جذر واجهة اللوحة. نثبّت اتجاه التخطيط LTR دائمًا: مواضع المفاتيح يجب ألا تنعكس
 * على أجهزة اللغة العربية (RTL)، وإلا ينقلب ترتيب الحروف الإنكليزية والعربية معًا.
 */
@Composable
fun KeyboardScreen(viewModel: KeyboardViewModel) {
    val state by viewModel.uiState.collectAsState()
    val isDark = when (state.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = remember(state.style, isDark) { semoColors(state.style, isDark) }
    val metrics = remember(state.style, state.size) { keyMetrics(state.style, state.size) }
    val rows = remember(
        state.page, state.shiftState, state.language, state.numberRow, state.enterKind,
        state.englishLayout, state.arabicLayout, state.style, state.arabicDigits
    ) { KeyboardLayoutProvider.rows(state) }

    // من أندرويد 15 اللوحة تُرسم خلف شريط التنقل، فنضيف حشوة سفلية بمقداره
    val edgeToEdgeIme = Build.VERSION.SDK_INT >= 35
    val bottomInsets = if (edgeToEdgeIme) WindowInsets.navigationBars else WindowInsets(0, 0, 0, 0)
    // زر إخفاء اللوحة الذي يرسمه النظام يقع بمنطقة شريط التنقل، فنترك له مساحة كافية كي لا يغطي مفتاح Return
    val extraBottom = if (edgeToEdgeIme) 20.dp else 4.dp

    val panelShape = if (state.style == KeyboardStyle.IOS26) {
        RoundedCornerShape(topStart = metrics.panelRadius, topEnd = metrics.panelRadius)
    } else {
        RectangleShape
    }

    // ارتفاع صفحات الإيموجي/الحافظة/التحرير = أربعة صفوف + الصف السفلي الإضافي
    val panelHeight = metrics.rowHeight * 4 + metrics.utilHeight
    val sideHeight = metrics.stripHeight + panelHeight
    val oneHanded = state.oneHand != OneHandMode.OFF

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.panel, panelShape)
                .windowInsetsPadding(bottomInsets)
                .padding(bottom = extraBottom)
        ) {
            if (state.oneHand == OneHandMode.RIGHT) {
                OneHandSide(state, colors, sideHeight, viewModel)
            }

            Column(
                modifier = Modifier
                    .weight(if (oneHanded) 0.84f else 1f)
                    .padding(horizontal = metrics.sidePadding)
            ) {
                TopStrip(state, colors, metrics, viewModel)

                when (state.page) {
                    KeyboardPage.EMOJI -> EmojiPanel(state, colors, panelHeight, viewModel)
                    KeyboardPage.CLIPBOARD -> ClipboardPanel(state, colors, panelHeight, viewModel)
                    KeyboardPage.EDIT -> EditPanel(state, colors, panelHeight, viewModel)
                    else -> rows.forEach { row ->
                        val isUtility = row.isNotEmpty() && row.all { it.plain || it.isSpacer }
                        KeyRow(
                            row = row,
                            colors = colors,
                            metrics = metrics,
                            rowHeight = if (isUtility) metrics.utilHeight else metrics.rowHeight,
                            showPreview = state.keyPreview,
                            viewModel = viewModel
                        )
                    }
                }
            }

            if (state.oneHand == OneHandMode.LEFT) {
                OneHandSide(state, colors, sideHeight, viewModel)
            }
        }
    }
}

/** الجانب الفارغ بوضع اليد الواحدة، مع زر لتوسيع اللوحة */
@Composable
private fun RowScope.OneHandSide(
    state: KeyboardUiState,
    colors: SemoKeyboardColors,
    height: Dp,
    viewModel: KeyboardViewModel
) {
    val expandAngle = if (state.oneHand == OneHandMode.RIGHT) 180f else 0f
    Box(
        modifier = Modifier
            .weight(0.16f)
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        // السهم يشير نحو الجهة التي ستتمدد إليها اللوحة (يمين عندما تكون اللوحة على اليسار والعكس)
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 64.dp)
                .background(colors.key, RoundedCornerShape(14.dp))
                .clickable { viewModel.setOneHand(OneHandMode.OFF) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_tool_chevron),
                contentDescription = tr(state.language == KeyboardLanguage.ARABIC, "توسيع", "Expand"),
                tint = colors.text,
                modifier = Modifier
                    .size(22.dp)
                    .rotate(expandAngle)
            )
        }
    }
}

// ============================ الشريط العلوي ============================

@Composable
private fun TopStrip(
    state: KeyboardUiState,
    colors: SemoKeyboardColors,
    metrics: KeyMetrics,
    viewModel: KeyboardViewModel
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.stripHeight)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.alternates.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                state.alternates.forEach { alt ->
                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 34.dp)
                            .background(colors.chip, RoundedCornerShape(8.dp))
                            .clickable { viewModel.onAlternateChosen(alt) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = alt, fontSize = 20.sp, color = colors.text)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "✕",
                color = colors.textMuted,
                fontSize = 16.sp,
                modifier = Modifier
                    .clickable { viewModel.dismissAlternates() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        } else {
            StripIconButton(
                iconRes = R.drawable.ic_tool_chevron,
                colors = colors,
                rotate = if (state.toolbarOpen) 180f else 0f,
                onClick = { viewModel.toggleToolbar() }
            )
            if (state.toolbarOpen) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StripIconButton(R.drawable.ic_key_emoji, colors) { viewModel.openPage(KeyboardPage.EMOJI) }
                    StripIconButton(R.drawable.ic_tool_clipboard, colors) { viewModel.openPage(KeyboardPage.CLIPBOARD) }
                    StripIconButton(R.drawable.ic_tool_cursor, colors) { viewModel.openPage(KeyboardPage.EDIT) }
                    StripIconButton(R.drawable.ic_tool_onehand, colors) { viewModel.cycleOneHand() }
                    StripIconButton(R.drawable.ic_tool_settings, colors) { viewModel.openSettings() }
                    StripIconButton(R.drawable.ic_key_hide, colors) { viewModel.onKeyPressed(KeyAction.Hide) }
                }
            } else {
                SuggestionsRow(state.suggestions, colors) { viewModel.onSuggestionChosen(it) }
            }
        }
    }
}

@Composable
private fun StripIconButton(
    iconRes: Int,
    colors: SemoKeyboardColors,
    rotate: Float = 0f,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 40.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier
                .size(22.dp)
                .rotate(rotate)
        )
    }
}

@Composable
private fun RowScope.SuggestionsRow(
    suggestions: List<String>,
    colors: SemoKeyboardColors,
    onPick: (String) -> Unit
) {
    val items = suggestions.take(3)
    Row(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, word ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(colors.divider)
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onPick(word) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = word,
                    color = colors.suggestion,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
        if (items.isEmpty()) Spacer(Modifier.weight(1f))
    }
}

// ============================ صفوف المفاتيح ============================

@Composable
private fun KeyRow(
    row: List<KeyDefinition>,
    colors: SemoKeyboardColors,
    metrics: KeyMetrics,
    rowHeight: Dp,
    showPreview: Boolean,
    viewModel: KeyboardViewModel
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        row.forEach { key ->
            KeyButton(
                def = key,
                colors = colors,
                metrics = metrics,
                rowHeight = rowHeight,
                showPreview = showPreview,
                onKey = viewModel::onKeyPressed,
                onLongPress = viewModel::onKeyLongPressed,
                onCursor = viewModel::onCursorMove
            )
        }
    }
}

@Composable
private fun RowScope.KeyButton(
    def: KeyDefinition,
    colors: SemoKeyboardColors,
    metrics: KeyMetrics,
    rowHeight: Dp,
    showPreview: Boolean,
    onKey: (KeyAction) -> Unit,
    onLongPress: (KeyDefinition) -> Unit,
    onCursor: (Int) -> Unit
) {
    if (def.isSpacer) {
        Spacer(Modifier.weight(def.weight))
        return
    }

    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, tween(50), label = "keyScale")
    val currentOnKey by rememberUpdatedState(onKey)
    val currentOnLong by rememberUpdatedState(onLongPress)
    val currentOnCursor by rememberUpdatedState(onCursor)
    val hasAlternates = def.longPressChars.isNotEmpty()
    val isBackspace = def.action == KeyAction.Backspace
    val isSpace = def.action == KeyAction.Space
    val special = isSpecial(def)

    val shiftOn = def.action == KeyAction.Shift && def.isAccent
    val bg = when {
        def.plain -> Color.Transparent
        shiftOn -> colors.shiftActive
        def.isAccent -> colors.keyAccent
        pressed && special -> colors.key
        pressed -> colors.keyPressed
        special -> colors.keySpecial
        else -> colors.key
    }
    val textColor = when {
        shiftOn -> colors.onShiftActive
        def.isAccent -> colors.textOnAccent
        else -> colors.text
    }
    val shadowColor = colors.keyShadow

    val longPressHandler: ((Offset) -> Unit)? = when {
        hasAlternates -> ({ _: Offset -> currentOnLong(def) })
        def.longPressAction != null -> ({ _: Offset -> currentOnKey(def.longPressAction) })
        else -> null
    }

    // شريط المسافة: ضغطة = مسافة، وسحب أفقي = تحريك المؤشر. باقي المفاتيح: ضغط/ضغط مطوّل.
    val gestureModifier = if (isSpace) {
        Modifier.pointerInput(def) {
            spaceGestures(
                onPressedChange = { pressed = it },
                onTap = { currentOnKey(KeyAction.Space) },
                onMove = { currentOnCursor(it) }
            )
        }
    } else {
        Modifier.pointerInput(def) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    if (isBackspace) {
                        // حذف فوري ثم تكرار تلقائي عند الاستمرار بالضغط
                        currentOnKey(KeyAction.Backspace)
                        coroutineScope {
                            val repeat = launch {
                                delay(400)
                                while (true) {
                                    currentOnKey(KeyAction.Backspace)
                                    delay(50)
                                }
                            }
                            tryAwaitRelease()
                            repeat.cancel()
                        }
                    } else {
                        tryAwaitRelease()
                    }
                    pressed = false
                },
                onTap = { if (!isBackspace) currentOnKey(def.action) },
                onLongPress = longPressHandler
            )
        }
    }

    // منطقة اللمس تشمل الفراغات بين المفاتيح (تقلّل الضغطات الضائعة)، والشكل المرئي أصغر منها
    val verticalPadding = if (def.plain) 2.dp else metrics.rowSpacing / 2
    Box(
        modifier = Modifier
            .weight(def.weight)
            .height(rowHeight)
            .zIndex(if (pressed) 1f else 0f)
            .then(gestureModifier)
            .padding(horizontal = metrics.keySpacing / 2, vertical = verticalPadding)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
                .drawBehind {
                    if (!def.plain) {
                        drawRoundRect(
                            color = shadowColor,
                            topLeft = Offset(0f, 1.5.dp.toPx()),
                            size = size,
                            cornerRadius = CornerRadius(metrics.keyRadius.toPx())
                        )
                    }
                }
                .background(bg, metrics.keyShape),
            contentAlignment = Alignment.Center
        ) {
            KeyContent(def, textColor, iconSize = if (def.plain) 26.dp else 20.dp)
        }

        // فقاعة معاينة الحرف فوق المفتاح أثناء الضغط (مثل iOS)
        if (showPreview && pressed && def.action is KeyAction.Character) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .requiredSize(width = 58.dp, height = 70.dp)
                    .offset(y = (-64).dp)
                    .shadow(6.dp, RoundedCornerShape(14.dp))
                    .background(colors.bubble, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = def.label, color = colors.text, fontSize = 36.sp, maxLines = 1)
            }
        }
    }
}

/** إيماءة شريط المسافة: لمسة قصيرة = مسافة، سحب أفقي = خطوات مؤشر كل ~12dp */
private suspend fun PointerInputScope.spaceGestures(
    onPressedChange: (Boolean) -> Unit,
    onTap: () -> Unit,
    onMove: (Int) -> Unit
) {
    val stepPx = 12.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onPressedChange(true)
        var total = 0f
        var steps = 0
        var moved = false
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.changedToUpIgnoreConsumed()) {
                    if (!moved) onTap()
                    break
                }
                total += change.positionChange().x
                if (!moved && abs(total) > viewConfiguration.touchSlop) moved = true
                if (moved) {
                    val target = (total / stepPx).toInt()
                    if (target != steps) {
                        onMove(target - steps)
                        steps = target
                    }
                    change.consume()
                }
            }
        } finally {
            onPressedChange(false)
        }
    }
}

@Composable
private fun KeyContent(def: KeyDefinition, textColor: Color, iconSize: Dp) {
    val iconRes = if (def.textOnly) null else when (def.action) {
        KeyAction.Shift -> if (def.label == "⇪") R.drawable.ic_key_shift_locked else R.drawable.ic_key_shift
        KeyAction.Backspace -> R.drawable.ic_key_backspace
        KeyAction.Globe, KeyAction.SwitchLanguage -> R.drawable.ic_key_globe
        KeyAction.Emoji -> R.drawable.ic_key_emoji
        KeyAction.Enter -> R.drawable.ic_key_enter
        else -> null
    }
    if (iconRes != null) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = def.label,
            tint = textColor,
            modifier = Modifier.size(iconSize)
        )
    } else {
        val isLetter = def.action is KeyAction.Character
        Text(
            text = def.label,
            color = textColor,
            fontSize = when {
                def.action == KeyAction.Space -> 15.sp
                isLetter -> 25.sp
                def.action == KeyAction.Enter -> 16.sp
                def.label.length > 2 -> 15.sp
                else -> 18.sp
            },
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

private fun isSpecial(def: KeyDefinition): Boolean = when (def.action) {
    KeyAction.Shift, KeyAction.Backspace, KeyAction.Globe, KeyAction.Emoji,
    KeyAction.SwitchToSymbols, KeyAction.SwitchToSymbols2, KeyAction.SwitchToLetters,
    KeyAction.SwitchLanguage -> true
    KeyAction.Enter -> !def.isAccent
    else -> false
}

// ============================ الصفحات الإضافية ============================

/** شريط سفلي مشترك لصفحات الإيموجي/الحافظة/التحرير: زر الرجوع للحروف + محتوى إضافي */
@Composable
private fun PanelBottomBar(
    arabic: Boolean,
    colors: SemoKeyboardColors,
    onBack: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .size(width = 64.dp, height = 36.dp)
                .background(colors.keySpecial, RoundedCornerShape(10.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Text(text = tr(arabic, "أبج", "ABC"), color = colors.text, fontSize = 15.sp)
        }
        trailing()
    }
}

@Composable
private fun EmojiPanel(
    state: KeyboardUiState,
    colors: SemoKeyboardColors,
    panelHeight: Dp,
    viewModel: KeyboardViewModel
) {
    val arabic = state.language == KeyboardLanguage.ARABIC
    val categories = KeyboardLayoutProvider.emojiCategories
    // التبويب 0 = الأخيرة، والباقي = فئات الإيموجي
    var tab by remember { mutableIntStateOf(if (state.recentEmojis.isEmpty()) 1 else 0) }
    val list = if (tab == 0) state.recentEmojis else categories[tab - 1].emojis

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(panelHeight)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (list.isEmpty()) {
                Text(
                    text = tr(arabic, "ما في إيموجي أخيرة بعد", "No recent emoji yet"),
                    color = colors.textMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(list) { emoji ->
                        Box(
                            modifier = Modifier
                                .height(44.dp)
                                .pointerInput(emoji) { detectTapGestures(onTap = { viewModel.onEmojiPressed(emoji) }) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 24.sp, color = colors.text)
                        }
                    }
                }
            }
        }

        PanelBottomBar(arabic, colors, onBack = { viewModel.openPage(KeyboardPage.LETTERS) }) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icons = listOf("🕘") + categories.map { it.icon }
                icons.forEachIndexed { index, icon ->
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 36.dp)
                            .background(
                                if (index == tab) colors.chip else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { tab = index },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = icon, fontSize = 18.sp, color = colors.text)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 44.dp)
                    .clickable { viewModel.onKeyPressed(KeyAction.Backspace) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_key_backspace),
                    contentDescription = null,
                    tint = colors.text,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun ClipboardPanel(
    state: KeyboardUiState,
    colors: SemoKeyboardColors,
    panelHeight: Dp,
    viewModel: KeyboardViewModel
) {
    val arabic = state.language == KeyboardLanguage.ARABIC
    // المثبّتة أولًا، مع الحفاظ على ترتيب الأحدث ضمن كل مجموعة
    val clips = remember(state.clipItems) { state.clipItems.sortedByDescending { it.pinned } }
    val hasUnpinned = clips.any { !it.pinned }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(panelHeight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = tr(arabic, "الحافظة", "Clipboard"),
                color = colors.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (hasUnpinned) {
                Text(
                    text = tr(arabic, "مسح غير المثبّت", "Clear unpinned"),
                    color = colors.keyAccent,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable { viewModel.onClipClearUnpinned() }
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                !state.clipboardEnabled -> Text(
                    text = tr(arabic, "الحافظة متوقفة من إعدادات التطبيق", "Clipboard history is off in settings"),
                    color = colors.textMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
                clips.isEmpty() -> Text(
                    text = tr(arabic, "انسخ نص وبيظهر هون", "Copy some text and it will show up here"),
                    color = colors.textMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    items(clips, key = { it.text }) { item -> ClipRow(item, colors, viewModel) }
                }
            }
        }

        PanelBottomBar(arabic, colors, onBack = { viewModel.openPage(KeyboardPage.LETTERS) })
    }
}

@Composable
private fun ClipRow(item: ClipItem, colors: SemoKeyboardColors, viewModel: KeyboardViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.key, RoundedCornerShape(10.dp))
            .clickable { viewModel.onClipPaste(item.text) }
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.text,
            color = colors.text,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(textDirection = TextDirection.Content),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (item.pinned) "★" else "☆",
            color = if (item.pinned) colors.keyAccent else colors.textMuted,
            fontSize = 20.sp,
            modifier = Modifier
                .clickable { viewModel.onClipTogglePin(item.text) }
                .padding(horizontal = 10.dp, vertical = 2.dp)
        )
        Text(
            text = "✕",
            color = colors.textMuted,
            fontSize = 16.sp,
            modifier = Modifier
                .clickable { viewModel.onClipDelete(item.text) }
                .padding(start = 4.dp, end = 12.dp, top = 2.dp, bottom = 2.dp)
        )
    }
}

@Composable
private fun EditPanel(
    state: KeyboardUiState,
    colors: SemoKeyboardColors,
    panelHeight: Dp,
    viewModel: KeyboardViewModel
) {
    val arabic = state.language == KeyboardLanguage.ARABIC
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(panelHeight)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolButton(tr(arabic, "تحديد الكل", "Select all"), colors) { viewModel.onEditAction(EditAction.SELECT_ALL) }
                ToolButton(tr(arabic, "قص", "Cut"), colors) { viewModel.onEditAction(EditAction.CUT) }
                ToolButton(tr(arabic, "نسخ", "Copy"), colors) { viewModel.onEditAction(EditAction.COPY) }
                ToolButton(tr(arabic, "لصق", "Paste"), colors) { viewModel.onEditAction(EditAction.PASTE) }
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolButton("◀", colors) { viewModel.onEditAction(EditAction.LEFT) }
                ToolButton("▶", colors) { viewModel.onEditAction(EditAction.RIGHT) }
                ToolButton(tr(arabic, "البداية", "Start"), colors) { viewModel.onEditAction(EditAction.HOME) }
                ToolButton(tr(arabic, "النهاية", "End"), colors) { viewModel.onEditAction(EditAction.END) }
            }
        }
        PanelBottomBar(arabic, colors, onBack = { viewModel.openPage(KeyboardPage.LETTERS) }) {
            Text(
                text = tr(arabic, "اسحب على المسافة لتحريك المؤشر", "Swipe the space bar to move the cursor"),
                color = colors.textMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            )
        }
    }
}

@Composable
private fun RowScope.ToolButton(label: String, colors: SemoKeyboardColors, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(colors.key, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = colors.text,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
