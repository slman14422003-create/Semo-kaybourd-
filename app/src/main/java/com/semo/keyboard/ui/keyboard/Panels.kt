package com.semo.keyboard.ui.keyboard

import android.graphics.Paint
import android.util.Patterns
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.semo.keyboard.R
import com.semo.keyboard.data.ClipImageStore
import com.semo.keyboard.domain.logic.EmojiCategory
import com.semo.keyboard.domain.logic.KeyboardLayoutProvider
import com.semo.keyboard.domain.model.ClipItem
import com.semo.keyboard.domain.model.clipImageName
import com.semo.keyboard.domain.model.isClipImage
import com.semo.keyboard.domain.model.isImage
import com.semo.keyboard.domain.model.EditAction
import com.semo.keyboard.domain.model.KeyAction
import com.semo.keyboard.domain.model.KeyboardLanguage
import com.semo.keyboard.domain.model.KeyboardPage
import com.semo.keyboard.domain.model.KeyboardUiState
import com.semo.keyboard.ui.theme.SemoKeyboardColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ============================ أدوات مشتركة ============================

/**
 * زر بنمط iOS بلا موجة (ripple): يمرّر حالة الضغط للمحتوى. يلغي الضغطة لو سحب الإصبع لتمرير قائمة،
 * ويدعم ضغطة مطوّلة اختيارية.
 */
@Composable
internal fun PressBox(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    content: @Composable BoxScope.(pressed: Boolean) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val tap by rememberUpdatedState(onClick)
    val long by rememberUpdatedState(onLongPress)
    val hasLong = onLongPress != null
    Box(
        modifier = modifier.pointerInput(hasLong) {
            keyGestures(
                onPressedChange = { pressed = it },
                onTap = { tap() },
                onLongPress = if (hasLong) ({ long?.invoke() }) else null
            )
        },
        contentAlignment = Alignment.Center
    ) {
        content(pressed)
    }
}

/** زر يكرّر عمله عند الاستمرار بالضغط (الحذف، أسهم المؤشر): ينفّذ فورًا ثم يتسارع */
@Composable
internal fun RepeatKey(
    modifier: Modifier = Modifier,
    onTick: () -> Unit,
    content: @Composable BoxScope.(pressed: Boolean) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val tick by rememberUpdatedState(onTick)
    Box(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    tick()
                    coroutineScope {
                        val job = launch {
                            delay(380)
                            var n = 0
                            while (true) {
                                tick()
                                n++
                                delay(if (n < 10) 70L else 40L)
                            }
                        }
                        tryAwaitRelease()
                        job.cancel()
                    }
                    pressed = false
                }
            )
        },
        contentAlignment = Alignment.Center
    ) {
        content(pressed)
    }
}

/** شريط سفلي مشترك لصفحات الإيموجي/الحافظة/التحرير بنمط iOS: زر ABC على اليسار + محتوى إضافي */
@Composable
internal fun PanelBottomBar(
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
        PressBox(modifier = Modifier.size(width = 58.dp, height = 40.dp), onClick = onBack) { pressed ->
            Text(
                text = tr(arabic, "أبج", "ABC"),
                color = if (pressed) colors.textMuted else colors.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
        trailing()
    }
}

// ============================ الإيموجي ============================

/** يخفي الإيموجي التي لا يملك خط الجهاز رمزًا لها (كي لا تظهر مربعات فارغة) */
private fun supportedEmojiCategories(): List<EmojiCategory> {
    val paint = Paint()
    return KeyboardLayoutProvider.emojiCategories.map { cat ->
        val ok = cat.emojis.filter { runCatching { paint.hasGlyph(it) }.getOrDefault(true) }
        // لو الفحص رفض معظم القائمة فهو غير موثوق على هذا الجهاز: نعرض القائمة كاملة
        cat.copy(emojis = if (ok.size * 2 >= cat.emojis.size) ok else cat.emojis)
    }.filter { it.emojis.isNotEmpty() }
}

internal val supportedEmojiCache: List<EmojiCategory> by lazy { supportedEmojiCategories() }

/**
 * رسم الإيموجي مباشرة بـ Paint أصلي بدل Text: تركيب نص Compose لكل خلية (مع خط الإيموجي) كان سبب
 * التقطيع عند التمرير، أما drawText فيستعمل ذاكرة الرموز المخزّنة بالمعالج الرسومي ويرسم عشرات الخلايا
 * بلا أي قياس أو تخطيط.
 */
private val emojiPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

private fun DrawScope.drawEmojiGlyph(emoji: String, textSizePx: Float) {
    drawIntoCanvas { canvas ->
        val p = emojiPaint
        p.textSize = textSizePx
        val fm = p.fontMetrics
        canvas.nativeCanvas.drawText(emoji, size.width / 2f, size.height / 2f - (fm.ascent + fm.descent) / 2f, p)
    }
}

private val SKIN_TONES = listOf("\uD83C\uDFFB", "\uD83C\uDFFC", "\uD83C\uDFFD", "\uD83C\uDFFE", "\uD83C\uDFFF")

/** درجات البشرة المتاحة للإيموجي البسيط (👍 ✋ 🙏 ...) وإلا قائمة فارغة. الأولى = الأصلية. */
private fun skinToneVariants(emoji: String): List<String> {
    val base = emoji.replace("\uFE0F", "")
    if (base.isEmpty() || base.codePointCount(0, base.length) != 1) return emptyList()
    if (base.codePointAt(0) < 0x261D) return emptyList()
    val variants = SKIN_TONES.map { base + it }
    val supported = variants.all { runCatching { emojiPaint.hasGlyph(it) }.getOrDefault(false) }
    return if (supported) listOf(emoji) + variants else emptyList()
}

private class EmojiSection(val title: String, val iconRes: Int, val emojis: List<String>)

private sealed interface EmojiEntry {
    val key: String
}

private class EmojiHeaderEntry(val title: String, section: Int) : EmojiEntry {
    override val key: String = "h$section"
}

private class EmojiGlyphEntry(val emoji: String, section: Int) : EmojiEntry {
    override val key: String = "e$section|$emoji"
}

private class EmojiLayout(val entries: List<EmojiEntry>, val sectionStarts: IntArray)

private fun emojiCategoryIcon(titleEn: String): Int = when (titleEn) {
    "Smileys" -> R.drawable.ic_emoji_smileys
    "People" -> R.drawable.ic_emoji_people
    "Hearts" -> R.drawable.ic_emoji_hearts
    "Nature" -> R.drawable.ic_emoji_nature
    "Food" -> R.drawable.ic_emoji_food
    "Activities" -> R.drawable.ic_emoji_activities
    "Travel" -> R.drawable.ic_emoji_travel
    "Objects" -> R.drawable.ic_emoji_objects
    "Symbols" -> R.drawable.ic_emoji_symbols
    "Flags" -> R.drawable.ic_emoji_flags
    else -> R.drawable.ic_emoji_smileys
}

private fun buildEmojiLayout(categories: List<EmojiCategory>, recents: List<String>, arabic: Boolean): Pair<List<EmojiSection>, EmojiLayout> {
    val sections = ArrayList<EmojiSection>()
    if (recents.isNotEmpty()) {
        sections.add(EmojiSection(tr(arabic, "المستخدمة مؤخرًا", "Frequently Used"), R.drawable.ic_emoji_recent, recents.distinct()))
    }
    for (cat in categories) {
        sections.add(EmojiSection(if (arabic) cat.titleAr else cat.titleEn, emojiCategoryIcon(cat.titleEn), cat.emojis.distinct()))
    }
    val entries = ArrayList<EmojiEntry>()
    val starts = IntArray(sections.size)
    sections.forEachIndexed { index, section ->
        starts[index] = entries.size
        entries.add(EmojiHeaderEntry(if (arabic) section.title else section.title.uppercase(), index))
        section.emojis.forEach { entries.add(EmojiGlyphEntry(it, index)) }
    }
    return sections to EmojiLayout(entries, starts)
}

/**
 * لوحة الإيموجي بنمط iOS: قائمة واحدة متصلة بعناوين أقسام صغيرة (SMILEYS, ANIMALS...)، وشريط سفلي
 * فيه ABC وأيقونات الفئات الخطية والحذف. الضغطة المطوّلة على إيموجي يدعم درجات البشرة تعرض الدرجات.
 */
@Composable
internal fun EmojiPanel(
    state: KeyboardUiState,
    colors: SemoKeyboardColors,
    panelHeight: Dp,
    viewModel: KeyboardViewModel
) {
    val arabic = state.language == KeyboardLanguage.ARABIC
    // "المستخدمة مؤخرًا" تُلتقط مرة عند فتح اللوحة كي لا تتحرك الخلايا تحت الإصبع أثناء الاستعمال
    val recents = remember { state.recentEmojis }
    val built = remember(arabic) { buildEmojiLayout(supportedEmojiCache, recents, arabic) }
    val sections = built.first
    val layout = built.second
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val currentSection by remember(layout) {
        derivedStateOf {
            val first = gridState.firstVisibleItemIndex
            var index = 0
            for (i in layout.sectionStarts.indices) if (layout.sectionStarts[i] <= first) index = i
            index
        }
    }
    var picker by remember { mutableStateOf<List<String>?>(null) }
    val onEmoji = remember(viewModel) { { e: String -> viewModel.onEmojiPressed(e) } }
    val onLong = remember(viewModel) {
        { e: String ->
            val variants = skinToneVariants(e)
            if (variants.isEmpty()) {
                viewModel.onEmojiPressed(e)
            } else {
                picker = variants
            }
            Unit
        }
    }

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
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 6.dp)
            ) {
                items(
                    items = layout.entries,
                    key = { it.key },
                    span = { entry ->
                        if (entry is EmojiHeaderEntry) GridItemSpan(maxLineSpan) else GridItemSpan(1)
                    },
                    contentType = { entry -> if (entry is EmojiHeaderEntry) 0 else 1 }
                ) { entry ->
                    when (entry) {
                        is EmojiHeaderEntry -> Text(
                            text = entry.title,
                            color = colors.textMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.6.sp,
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 3.dp)
                        )
                        is EmojiGlyphEntry -> EmojiCellView(entry.emoji, colors.chip, onEmoji, onLong)
                    }
                }
            }

            picker?.let { variants ->
                // خلفية شفافة: لمسها يغلق قائمة درجات البشرة
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures(onTap = { picker = null }) }
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .shadow(10.dp, RoundedCornerShape(18.dp))
                        .background(colors.bubble, RoundedCornerShape(18.dp))
                        .padding(horizontal = 6.dp, vertical = 5.dp)
                ) {
                    variants.forEach { variant ->
                        PressBox(
                            modifier = Modifier.size(44.dp),
                            onClick = {
                                viewModel.onEmojiPressed(variant)
                                picker = null
                            }
                        ) { pressed ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .drawBehind {
                                        if (pressed) {
                                            drawRoundRect(
                                                color = colors.chip,
                                                size = size,
                                                cornerRadius = CornerRadius(12.dp.toPx())
                                            )
                                        }
                                        drawEmojiGlyph(variant, 28.dp.toPx())
                                    }
                            )
                        }
                    }
                }
            }
        }

        PanelBottomBar(
            arabic = arabic,
            colors = colors,
            onBack = {
                viewModel.onSelectionTick()
                viewModel.openPage(KeyboardPage.LETTERS)
            }
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                sections.forEachIndexed { index, section ->
                    val selected = index == currentSection
                    PressBox(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        onClick = {
                            viewModel.onSelectionTick()
                            scope.launch { gridState.scrollToItem(layout.sectionStarts[index]) }
                        }
                    ) { pressed ->
                        Box(
                            modifier = Modifier
                                .size(width = 30.dp, height = 30.dp)
                                .background(
                                    if (selected) colors.keySpecial else Color.Transparent,
                                    RoundedCornerShape(9.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(section.iconRes),
                                contentDescription = section.title,
                                tint = if (selected) colors.text else if (pressed) colors.text else colors.textMuted,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
            RepeatKey(
                modifier = Modifier.size(width = 50.dp, height = 40.dp),
                onTick = { viewModel.onKeyPressed(KeyAction.Backspace) }
            ) { pressed ->
                Icon(
                    painter = painterResource(R.drawable.ic_ios_backspace),
                    contentDescription = null,
                    tint = if (pressed) colors.textMuted else colors.text,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun EmojiCellView(
    emoji: String,
    highlight: Color,
    onTap: (String) -> Unit,
    onLong: (String) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val tap by rememberUpdatedState(onTap)
    val long by rememberUpdatedState(onLong)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .graphicsLayer { }
            .drawBehind {
                if (pressed) {
                    drawRoundRect(
                        color = highlight,
                        topLeft = Offset(3.dp.toPx(), 2.dp.toPx()),
                        size = Size(size.width - 6.dp.toPx(), size.height - 4.dp.toPx()),
                        cornerRadius = CornerRadius(11.dp.toPx())
                    )
                }
                drawEmojiGlyph(emoji, 29.dp.toPx())
            }
            .pointerInput(emoji) {
                keyGestures(
                    onPressedChange = { pressed = it },
                    onTap = { tap(emoji) },
                    onLongPress = { long(emoji) }
                )
            }
    )
}

// ============================ الحافظة ============================

private val DeleteRed = Color(0xFFFF453A)

private fun clipKindLabel(text: String, arabic: Boolean): String {
    val t = text.trim()
    return when {
        text.isClipImage() -> tr(arabic, "صورة", "Image")
        Patterns.WEB_URL.matcher(t).matches() -> tr(arabic, "رابط", "Link")
        Patterns.EMAIL_ADDRESS.matcher(t).matches() -> tr(arabic, "بريد", "Email")
        t.length >= 5 && t.all { it.isDigit() || it == '+' || it == '-' || it == '(' || it == ')' || it == ' ' } ->
            tr(arabic, "رقم", "Number")
        t.length > 140 -> tr(arabic, "نص طويل", "Long text")
        else -> tr(arabic, "نص", "Text")
    }
}

/**
 * الحافظة بنمط بطاقات iOS: بطاقتان بكل صف بأطوال حسب النص، قسم للمثبّتة وقسم للأحدث، ولصق سريع.
 * ضغطة = لصق، ضغطة مطوّلة = أزرار تثبيت/حذف على البطاقة نفسها.
 */
@Composable
internal fun ClipboardPanel(
    state: KeyboardUiState,
    colors: SemoKeyboardColors,
    panelHeight: Dp,
    viewModel: KeyboardViewModel
) {
    val arabic = state.language == KeyboardLanguage.ARABIC
    val pinned = remember(state.clipItems) { state.clipItems.filter { it.pinned } }
    val recent = remember(state.clipItems) { state.clipItems.filter { !it.pinned } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(panelHeight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = tr(arabic, "الحافظة", "Clipboard"),
                color = colors.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            ClipHeaderChip(tr(arabic, "لصق", "Paste"), R.drawable.ic_clip_paste, true, colors) {
                viewModel.onSelectionTick()
                viewModel.onEditAction(EditAction.PASTE)
            }
            if (recent.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                ClipHeaderChip(tr(arabic, "مسح", "Clear"), R.drawable.ic_clip_trash, false, colors) {
                    viewModel.onSelectionTick()
                    viewModel.onClipClearUnpinned()
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                !state.clipboardEnabled -> ClipEmptyState(
                    tr(arabic, "سجل الحافظة متوقف", "Clipboard history is off"),
                    tr(arabic, "فعّله من إعدادات التطبيق", "Turn it on in the app settings"),
                    colors
                )
                state.clipItems.isEmpty() -> ClipEmptyState(
                    tr(arabic, "الحافظة فاضية", "Nothing copied yet"),
                    tr(arabic, "انسخ نصًا وسيظهر هنا للصقه بلمسة", "Copy some text and it will show up here"),
                    colors
                )
                else -> LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 2.dp, end = 2.dp, bottom = 8.dp),
                    verticalItemSpacing = 8.dp,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (pinned.isNotEmpty()) {
                        item(key = "pinned-label", span = StaggeredGridItemSpan.FullLine) {
                            ClipSectionLabel(tr(arabic, "المثبّتة", "PINNED"), colors)
                        }
                        items(pinned, key = { it.text }) { item -> ClipCard(item, arabic, colors, viewModel) }
                    }
                    if (recent.isNotEmpty()) {
                        if (pinned.isNotEmpty()) {
                            item(key = "recent-label", span = StaggeredGridItemSpan.FullLine) {
                                ClipSectionLabel(tr(arabic, "الأحدث", "RECENT"), colors)
                            }
                        }
                        items(recent, key = { it.text }) { item -> ClipCard(item, arabic, colors, viewModel) }
                    }
                }
            }
        }

        PanelBottomBar(arabic, colors, onBack = {
            viewModel.onSelectionTick()
            viewModel.openPage(KeyboardPage.LETTERS)
        })
    }
}

@Composable
private fun ClipSectionLabel(text: String, colors: SemoKeyboardColors) {
    Text(
        text = text,
        color = colors.textMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 6.dp, top = 4.dp)
    )
}

@Composable
private fun ClipEmptyState(title: String, hint: String, colors: SemoKeyboardColors) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.ic_tool_clipboard),
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(34.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(text = title, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(text = hint, color = colors.textMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ClipHeaderChip(
    label: String,
    iconRes: Int,
    accent: Boolean,
    colors: SemoKeyboardColors,
    onClick: () -> Unit
) {
    PressBox(modifier = Modifier.height(32.dp), onClick = onClick) { pressed ->
        val fill = when {
            accent && pressed -> colors.keyAccent.copy(alpha = 0.75f)
            accent -> colors.keyAccent
            pressed -> colors.keyPressed
            else -> colors.chip
        }
        val ink = if (accent) colors.textOnAccent else colors.text
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .background(fill, RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(text = label, color = ink, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
private fun ClipCard(item: ClipItem, arabic: Boolean, colors: SemoKeyboardColors, viewModel: KeyboardViewModel) {
    var menu by remember(item.text) { mutableStateOf(false) }
    val kind = remember(item.text, arabic) { clipKindLabel(item.text, arabic) }
    PressBox(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            if (menu) {
                menu = false
            } else {
                viewModel.onSelectionTick()
                viewModel.onClipPaste(item.text)
            }
        },
        onLongPress = {
            viewModel.onSelectionTick()
            menu = !menu
        }
    ) { pressed ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (pressed) colors.keyPressed else colors.key, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (item.isImage) {
                ClipImageThumb(item.text.clipImageName(), colors)
            } else {
                Text(
                    text = item.text,
                    color = colors.text,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = TextDirection.Content)
                )
            }
            Spacer(Modifier.height(8.dp))
            if (menu) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ClipActionButton(
                        label = if (item.pinned) tr(arabic, "إلغاء التثبيت", "Unpin") else tr(arabic, "تثبيت", "Pin"),
                        iconRes = if (item.pinned) R.drawable.ic_clip_pin_fill else R.drawable.ic_clip_pin,
                        ink = colors.keyAccent,
                        colors = colors,
                        modifier = Modifier.weight(1f)
                    ) { viewModel.onClipTogglePin(item.text) }
                    ClipActionButton(
                        label = tr(arabic, "حذف", "Delete"),
                        iconRes = R.drawable.ic_clip_trash,
                        ink = DeleteRed,
                        colors = colors,
                        modifier = Modifier.weight(1f)
                    ) { viewModel.onClipDelete(item.text) }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = kind, color = colors.textMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    if (item.pinned) {
                        Icon(
                            painter = painterResource(R.drawable.ic_clip_pin_fill),
                            contentDescription = null,
                            tint = colors.keyAccent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

/** مصغّرة صورة من الحافظة (تُفك بخيط خلفي وتُخزَّن مؤقتًا كي لا تتقطع القائمة) */
@Composable
private fun ClipImageThumb(name: String, colors: SemoKeyboardColors) {
    val context = LocalContext.current
    val store = remember(context) { ClipImageStore(context) }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, name) {
        value = withContext(Dispatchers.IO) { store.thumbnail(name)?.asImageBitmap() }
    }
    val image = bitmap
    if (image != null) {
        val ratio = (image.width.toFloat() / image.height.toFloat()).coerceIn(0.7f, 1.8f)
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(9.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .background(colors.chip, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_tool_clipboard),
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ClipActionButton(
    label: String,
    iconRes: Int,
    ink: Color,
    colors: SemoKeyboardColors,
    modifier: Modifier,
    onClick: () -> Unit
) {
    PressBox(modifier = modifier.height(30.dp), onClick = onClick) { pressed ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(if (pressed) colors.keyPressed else colors.chip, RoundedCornerShape(9.dp)),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(text = label, color = ink, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

// ============================ التحرير ============================

/**
 * لوحة التحرير: صف لتحديد الكل/قص/نسخ/لصق، ثم أسهم المؤشر بشكل حرف T مقلوب (تتكرر عند الاستمرار)
 * مع البداية/النهاية والتراجع/الإعادة.
 */
@Composable
internal fun EditPanel(
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
                EditTextKey(tr(arabic, "تحديد الكل", "Select All"), colors) { viewModel.onEditAction(EditAction.SELECT_ALL) }
                EditTextKey(tr(arabic, "قص", "Cut"), colors) { viewModel.onEditAction(EditAction.CUT) }
                EditTextKey(tr(arabic, "نسخ", "Copy"), colors) { viewModel.onEditAction(EditAction.COPY) }
                EditTextKey(tr(arabic, "لصق", "Paste"), colors) { viewModel.onEditAction(EditAction.PASTE) }
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditTextKey(tr(arabic, "البداية", "Start"), colors) { viewModel.onEditAction(EditAction.HOME) }
                EditArrowKey(-90f, colors) { viewModel.onEditAction(EditAction.UP) }
                EditTextKey(tr(arabic, "النهاية", "End"), colors) { viewModel.onEditAction(EditAction.END) }
                EditTextKey(tr(arabic, "تراجع", "Undo"), colors) { viewModel.onEditAction(EditAction.UNDO) }
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditArrowKey(180f, colors) { viewModel.onEditAction(EditAction.LEFT) }
                EditArrowKey(90f, colors) { viewModel.onEditAction(EditAction.DOWN) }
                EditArrowKey(0f, colors) { viewModel.onEditAction(EditAction.RIGHT) }
                EditTextKey(tr(arabic, "إعادة", "Redo"), colors) { viewModel.onEditAction(EditAction.REDO) }
            }
        }
        PanelBottomBar(arabic, colors, onBack = {
            viewModel.onSelectionTick()
            viewModel.openPage(KeyboardPage.LETTERS)
        }) {
            Text(
                text = tr(arabic, "اضغط مطولًا على المسافة لتحريك المؤشر", "Touch and hold the space bar to move the cursor"),
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
private fun RowScope.EditTextKey(label: String, colors: SemoKeyboardColors, onClick: () -> Unit) {
    PressBox(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        onClick = onClick
    ) { pressed ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (pressed) colors.keyPressed else colors.key, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = colors.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RowScope.EditArrowKey(rotation: Float, colors: SemoKeyboardColors, onTick: () -> Unit) {
    RepeatKey(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        onTick = onTick
    ) { pressed ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (pressed) colors.keyPressed else colors.keySpecial, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_tool_chevron),
                contentDescription = null,
                tint = colors.text,
                modifier = Modifier
                    .size(24.dp)
                    .rotate(rotation)
            )
        }
    }
}
