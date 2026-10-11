package com.semo.keyboard.ui.keyboard

import android.graphics.Paint
import android.graphics.Typeface
import android.util.Patterns
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
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
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.semo.keyboard.R
import com.semo.keyboard.data.ClipImageStore
import com.semo.keyboard.domain.logic.EmojiCategory
import com.semo.keyboard.domain.logic.KeyboardLayoutProvider
import com.semo.keyboard.domain.model.ClipItem
import com.semo.keyboard.domain.model.clipImageName
import com.semo.keyboard.domain.logic.ClipClassifier
import com.semo.keyboard.domain.logic.ClipKind
import com.semo.keyboard.domain.model.clipDisplayText
import com.semo.keyboard.domain.model.clipLongLength
import com.semo.keyboard.domain.model.isClipImage
import com.semo.keyboard.domain.model.isClipLong
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
import kotlin.math.floor
import kotlin.math.max

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

/** رسم احتياطي مباشر بالخط (يُستعمل فقط لو لم تجهز صورة الإيموجي بعد، وللنافذة الصغيرة لدرجات البشرة) */
private val emojiPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

private fun DrawScope.drawEmojiGlyph(emoji: String, textSizePx: Float) {
    drawEmojiGlyphAt(emoji, size.width / 2f, size.height / 2f, textSizePx)
}

private fun DrawScope.drawEmojiGlyphAt(emoji: String, cx: Float, cy: Float, textSizePx: Float) {
    drawIntoCanvas { canvas ->
        val p = emojiPaint
        p.textSize = textSizePx
        val fm = p.fontMetrics
        canvas.nativeCanvas.drawText(emoji, cx, cy - (fm.ascent + fm.descent) / 2f, p)
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

private fun buildEmojiSections(categories: List<EmojiCategory>, recents: List<String>, arabic: Boolean): List<EmojiSection> {
    val sections = ArrayList<EmojiSection>()
    if (recents.isNotEmpty()) {
        sections.add(EmojiSection(tr(arabic, "المستخدمة مؤخرًا", "Frequently Used"), R.drawable.ic_emoji_recent, recents.distinct()))
    }
    for (cat in categories) {
        val title = if (arabic) cat.titleAr else cat.titleEn.uppercase()
        sections.add(EmojiSection(title, emojiCategoryIcon(cat.titleEn), cat.emojis.distinct()))
    }
    return sections
}

private const val CODE_BASE = 10_000

/**
 * تخطيط صفحات الإيموجي بنمط آيفون: أعمدة أفقية، كل عمود [rows] إيموجي، وكل قسم يبدأ بعمود جديد.
 * كل القياسات بالبكسل. رأس القسم (العنوان) بالأعلى بارتفاع [headerH].
 */
private class PagerLayout(
    val rows: Int,
    val cellW: Float,
    val cellH: Float,
    val headerH: Float,
    val startCol: IntArray,
    val counts: IntArray,
    val totalCols: Int
) {
    val contentW: Float get() = totalCols * cellW

    fun sectionAtCol(col: Int): Int {
        var lo = 0
        var hi = startCol.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (startCol[mid] <= col) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** القسم الذي يعتبر "الحالي" عند هذا الإزاحة (لتمييز أيقونة الفئة بالشريط السفلي) */
    fun sectionAtOffset(offset: Float): Int =
        sectionAtCol(((offset / cellW) + 0.35f).toInt().coerceIn(0, (totalCols - 1).coerceAtLeast(0)))

    /** رمز الخلية تحت اللمس (قسم * 10000 + فهرس) أو -1 */
    fun hitCode(x: Float, y: Float, offset: Float): Int {
        if (y < headerH) return -1
        val row = ((y - headerH) / cellH).toInt()
        if (row !in 0 until rows) return -1
        val col = ((x + offset) / cellW).toInt()
        if (col < 0 || col >= totalCols) return -1
        val section = sectionAtCol(col)
        val index = (col - startCol[section]) * rows + row
        return if (index < counts[section]) section * CODE_BASE + index else -1
    }
}

private fun buildPagerLayout(sections: List<EmojiSection>, widthPx: Float, heightPx: Float, density: Float): PagerLayout {
    val headerH = 24f * density
    val rows = (((heightPx - headerH) / density) / 38f).toInt().coerceIn(3, 5)
    val cellH = (heightPx - headerH) / rows
    val cellW = 46f * density
    val startCol = IntArray(sections.size)
    val counts = IntArray(sections.size)
    var col = 0
    sections.forEachIndexed { i, section ->
        startCol[i] = col
        counts[i] = section.emojis.size
        col += (section.emojis.size + rows - 1) / rows
    }
    return PagerLayout(rows, cellW, cellH, headerH, startCol, counts, col)
}

/** يرسم الصفحات الظاهرة فقط: Bitmap جاهز لكل إيموجي + عنوان القسم الملتصق بالحافة اليسرى */
private fun DrawScope.drawEmojiPager(
    layout: PagerLayout,
    sections: List<EmojiSection>,
    offset: Float,
    pressedCode: Int,
    highlight: Color,
    titlePaint: Paint
) {
    // قراءة رقم النسخة تجعل الرسمة تتجدد تلقائيًا كلما وصلت صور جديدة من الخيط الخلفي
    if (EmojiBitmapCache.version.intValue < 0) return
    if (layout.totalCols == 0) return
    val cw = layout.cellW
    val ch = layout.cellH
    val rows = layout.rows
    val firstCol = floor(offset / cw).toInt().coerceAtLeast(0)
    val lastCol = floor((offset + size.width) / cw).toInt().coerceAtMost(layout.totalCols - 1)
    val firstSection = layout.sectionAtCol(firstCol)

    // تمييز الخلية المضغوطة
    if (pressedCode >= 0) {
        val ps = pressedCode / CODE_BASE
        val pi = pressedCode % CODE_BASE
        val pcol = layout.startCol[ps] + pi / rows
        val prow = pi % rows
        drawRoundRect(
            color = highlight,
            topLeft = Offset(pcol * cw - offset + 3f, layout.headerH + prow * ch + 2f),
            size = Size(cw - 6f, ch - 4f),
            cornerRadius = CornerRadius(ch * 0.26f)
        )
    }

    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        var s = firstSection
        for (col in firstCol..lastCol) {
            while (s + 1 < layout.startCol.size && layout.startCol[s + 1] <= col) s++
            val emojis = sections[s].emojis
            val local = col - layout.startCol[s]
            val cx = col * cw - offset + cw / 2f
            for (row in 0 until rows) {
                val index = local * rows + row
                if (index >= emojis.size) break
                val emoji = emojis[index]
                val cy = layout.headerH + row * ch + ch / 2f
                val bitmap = EmojiBitmapCache.get(emoji)
                if (bitmap != null) {
                    nc.drawBitmap(bitmap, cx - bitmap.width / 2f, cy - bitmap.height / 2f, null)
                } else if (EmojiBitmapCache.emPx > 0f) {
                    val p = emojiPaint
                    p.textSize = EmojiBitmapCache.emPx
                    val fm = p.fontMetrics
                    nc.drawText(emoji, cx, cy - (fm.ascent + fm.descent) / 2f, p)
                }
            }
        }
        // عناوين الأقسام: العنوان يلتصق بالحافة اليسرى ما دام قسمه ظاهرًا
        val pad = 10f * (cw / 46f)
        var t = firstSection
        while (t < sections.size && layout.startCol[t] <= lastCol) {
            val title = sections[t].title
            val sectionStart = layout.startCol[t] * cw - offset
            val sectionEnd = (layout.startCol[t] + (layout.counts[t] + rows - 1) / rows) * cw - offset
            val textWidth = titlePaint.measureText(title)
            var x = max(sectionStart, 0f) + pad
            if (x + textWidth > sectionEnd - pad) x = max(sectionStart + pad, sectionEnd - pad - textWidth)
            nc.drawText(title, x, layout.headerH * 0.72f, titlePaint)
            t++
        }
    }
}

/**
 * لوحة الإيموجي بنمط آيفون: صفحات أفقية (4 صفوف) بعنوان قسم صغير بالأعلى، وشريط سفلي فيه ABC وأيقونات
 * الفئات الخطية والحذف. الرسم كله بلوحة رسم واحدة بلا مكوّنات لكل خلية، والصور جاهزة مسبقًا
 * ([EmojiBitmapCache]) فيصير التمرير سلسًا حتى بالسحب السريع. الضغط المطوّل على إيموجي يدعم درجات
 * البشرة يعرض الدرجات.
 */
@Composable
internal fun EmojiPanel(
    state: KeyboardUiState,
    colors: SemoKeyboardColors,
    panelHeight: Dp,
    viewModel: KeyboardViewModel
) {
    val arabic = state.language == KeyboardLanguage.ARABIC
    val density = LocalDensity.current
    // "المستخدمة مؤخرًا" تُلتقط مرة عند فتح اللوحة كي لا تتحرك الخلايا تحت الإصبع أثناء الاستعمال
    val recents = remember { state.recentEmojis }
    val sections = remember(arabic) { buildEmojiSections(supportedEmojiCache, recents, arabic) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(density.density) { EmojiBitmapCache.configure(density.density) }

    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val layout = remember(viewport, sections, density.density) {
        if (viewport.width > 0 && viewport.height > 0) {
            buildPagerLayout(sections, viewport.width.toFloat(), viewport.height.toFloat(), density.density)
        } else null
    }
    val maxOffset = if (layout != null) (layout.contentW - viewport.width).coerceAtLeast(0f) else 0f

    var offset by remember { mutableFloatStateOf(0f) }
    var pressedCode by remember { mutableIntStateOf(-1) }
    var picker by remember { mutableStateOf<List<String>?>(null) }

    val scrollState = rememberScrollableState { delta ->
        val old = offset
        val updated = (old + delta).coerceIn(0f, maxOffset)
        offset = updated
        updated - old
    }
    val currentSection by remember(layout) {
        derivedStateOf { layout?.sectionAtOffset(offset) ?: 0 }
    }

    // تحضير صور الإيموجي بالخلفية بدءًا من القسم الظاهر (يُعاد عند تغيّر القسم الحالي)
    LaunchedEffect(sections, currentSection) {
        val count = sections.size
        val order = ArrayList<String>()
        for (k in 0 until count) order.addAll(sections[(currentSection + k) % count].emojis)
        withContext(Dispatchers.Default) { EmojiBitmapCache.prefetch(order) }
    }

    val titlePaint = remember(colors.textMuted, density.density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.textMuted.toArgb()
            textSize = 11.5f * density.fontScale * density.density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.05f
        }
    }
    val highlight = colors.chip

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(panelHeight)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { viewport = it }
        ) {
            if (layout != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .scrollable(
                            state = scrollState,
                            orientation = Orientation.Horizontal,
                            reverseDirection = true
                        )
                        .pointerInput(layout, sections) {
                            detectTapGestures(
                                onPress = { pos ->
                                    val code = layout.hitCode(pos.x, pos.y, offset)
                                    coroutineScope {
                                        // التمييز يتأخر قليلًا كي لا يومض عند بدء التمرير بالسحب
                                        val job = launch {
                                            delay(60)
                                            pressedCode = code
                                        }
                                        tryAwaitRelease()
                                        job.cancel()
                                    }
                                    pressedCode = -1
                                },
                                onTap = { pos ->
                                    val code = layout.hitCode(pos.x, pos.y, offset)
                                    if (code >= 0) {
                                        viewModel.onEmojiPressed(sections[code / CODE_BASE].emojis[code % CODE_BASE])
                                    }
                                },
                                onLongPress = { pos ->
                                    val code = layout.hitCode(pos.x, pos.y, offset)
                                    if (code >= 0) {
                                        val emoji = sections[code / CODE_BASE].emojis[code % CODE_BASE]
                                        val variants = skinToneVariants(emoji)
                                        if (variants.isEmpty()) viewModel.onEmojiPressed(emoji) else picker = variants
                                    }
                                }
                            )
                        }
                        .drawBehind { drawEmojiPager(layout, sections, offset, pressedCode, highlight, titlePaint) }
                )
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
                            val target = ((layout?.startCol?.get(index) ?: 0) * (layout?.cellW ?: 0f)).coerceIn(0f, maxOffset)
                            scope.launch { scrollState.animateScrollBy(target - offset, tween(280)) }
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
                                tint = if (selected || pressed) colors.text else colors.textMuted,
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

// ============================ الحافظة ============================

private val DeleteRed = Color(0xFFFF453A)
private val PasteGreen = Color(0xFF30D158)

/** اسم النوع بصيغة الجمع لشريط التصفية أعلى الحافظة */
private fun kindFilterLabel(kind: ClipKind, arabic: Boolean): String = when (kind) {
    ClipKind.TEXT -> tr(arabic, "نصوص", "Text")
    ClipKind.LINK -> tr(arabic, "روابط", "Links")
    ClipKind.CODE -> tr(arabic, "أكواد", "Code")
    ClipKind.IMAGE -> tr(arabic, "صور", "Images")
}

/** وسم البطاقة (مفرد): نوع العنصر، ومع النص الطويل يُضاف عدد أحرفه */

/**
 * الحافظة بنمط بطاقات مدمجة: صف علوي واحد (أزرار التصفية أو العنوان + لصق + مسح الكل)، وبطاقتان بكل صف.
 * ضغطة على البطاقة = لصق، وتحتها أزرار ظاهرة للتثبيت والحذف.
 */
@Composable
internal fun ClipboardPanel(
    state: KeyboardUiState,
    colors: SemoKeyboardColors,
    panelHeight: Dp,
    bottomInset: Dp,
    viewModel: KeyboardViewModel
) {
    val arabic = state.language == KeyboardLanguage.ARABIC
    // تصفية حسب النوع (نصوص/روابط/أكواد/صور): تظهر الأزرار فقط لو الحافظة فيها أكثر من نوع
    var filter by remember { mutableStateOf<ClipKind?>(null) }
    val presentKinds = remember(state.clipItems) { state.clipItems.map { ClipClassifier.kindOf(it) }.toSet() }
    val activeFilter = filter?.takeIf { it in presentKinds }
    val shownItems = remember(state.clipItems, activeFilter) {
        if (activeFilter == null) state.clipItems else state.clipItems.filter { ClipClassifier.kindOf(it) == activeFilter }
    }
    val pinned = remember(shownItems) { shownItems.filter { it.pinned } }
    val recent = remember(shownItems) { shownItems.filter { !it.pinned } }
    val hasUnpinned = remember(state.clipItems) { state.clipItems.any { !it.pinned } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(panelHeight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // زر أبج دائري بالصف العلوي (بدل الشريط السفلي): المحتوى ياخذ كل المساحة تحته
            PressBox(modifier = Modifier.size(34.dp), onClick = {
                viewModel.onSelectionTick()
                viewModel.openPage(KeyboardPage.LETTERS)
            }) { pressed ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (pressed) colors.keyPressed else colors.chip, RoundedCornerShape(17.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tr(arabic, "أبج", "ABC"),
                        color = colors.text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (state.clipboardEnabled && presentKinds.size >= 2) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClipFilterChip(tr(arabic, "الكل", "All"), activeFilter == null, colors) {
                        viewModel.onSelectionTick()
                        filter = null
                    }
                    ClipKind.values().filter { it in presentKinds }.forEach { kind ->
                        ClipFilterChip(kindFilterLabel(kind, arabic), activeFilter == kind, colors) {
                            viewModel.onSelectionTick()
                            filter = kind
                        }
                    }
                }
            } else {
                Text(
                    text = tr(arabic, "الحافظة", "Clipboard"),
                    color = colors.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 6.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            // اللصق أخضر والحذف أحمر
            ClipRoundButton(R.drawable.ic_clip_paste, accent = true, ink = Color.White, colors = colors, fill = PasteGreen) {
                viewModel.onSelectionTick()
                viewModel.onEditAction(EditAction.PASTE)
            }
            if (hasUnpinned) {
                Spacer(Modifier.width(6.dp))
                ClipRoundButton(R.drawable.ic_clip_trash, accent = false, ink = Color.White, colors = colors, fill = DeleteRed) {
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
                    tr(arabic, "انسخ نصًا أو رابطًا أو كودًا أو صورة وسيظهر هنا", "Copy text, a link, code or an image and it shows up here"),
                    colors
                )
                shownItems.isEmpty() -> ClipEmptyState(
                    tr(arabic, "ما في عناصر من هذا النوع", "Nothing of this type"),
                    tr(arabic, "اختر «الكل» لعرض كل المنسوخ", "Choose All to see everything copied"),
                    colors
                )
                else -> LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    // ينتهي المحتوى فوق أزرار شريط التنقل لكن يمتد خلفها بالتمرير
                    contentPadding = PaddingValues(start = 2.dp, end = 2.dp, bottom = 6.dp + bottomInset),
                    verticalItemSpacing = 6.dp,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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

    }
}

/** زر دائري صغير بأيقونة (لصق/مسح الكل) بالصف العلوي للحافظة */
@Composable
private fun ClipRoundButton(
    iconRes: Int,
    accent: Boolean,
    ink: Color,
    colors: SemoKeyboardColors,
    fill: Color? = null,
    onClick: () -> Unit
) {
    PressBox(modifier = Modifier.size(34.dp), onClick = onClick) { pressed ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    when {
                        fill != null && pressed -> fill.copy(alpha = 0.75f)
                        fill != null -> fill
                        accent && pressed -> colors.keyAccent.copy(alpha = 0.75f)
                        accent -> colors.keyAccent
                        pressed -> colors.keyPressed
                        else -> colors.chip
                    },
                    RoundedCornerShape(17.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(painter = painterResource(iconRes), contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ClipFilterChip(label: String, selected: Boolean, colors: SemoKeyboardColors, onClick: () -> Unit) {
    PressBox(modifier = Modifier.height(28.dp), onClick = onClick) { pressed ->
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .background(
                    when {
                        selected -> colors.keyAccent
                        pressed -> colors.keyPressed
                        else -> colors.chip
                    },
                    RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (selected) colors.textOnAccent else colors.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
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
private fun ClipCard(item: ClipItem, arabic: Boolean, colors: SemoKeyboardColors, viewModel: KeyboardViewModel) {
    val kind = remember(item.text) { ClipClassifier.kindOf(item.text) }
    val shown = remember(item.text) { item.text.clipDisplayText() }
    var menu by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.key, RoundedCornerShape(14.dp))
            .pointerInput(item.text, menu) {
                detectTapGestures(
                    onTap = {
                        if (menu) menu = false else {
                            viewModel.onSelectionTick()
                            viewModel.onClipPaste(item.text)
                        }
                    },
                    onLongPress = {
                        viewModel.onSelectionTick()
                        menu = true
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp)
        ) {
            when (kind) {
                ClipKind.IMAGE -> ClipImageThumb(item.text.clipImageName(), colors)
                ClipKind.CODE -> Text(
                    text = shown,
                    color = colors.text,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = TextDirection.Ltr)
                )
                ClipKind.LINK -> Text(
                    text = shown.trim(),
                    color = colors.keyAccent,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = TextDirection.Ltr)
                )
                ClipKind.TEXT -> Text(
                    text = shown,
                    color = colors.text,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = TextDirection.Content)
                )
            }
            if (item.pinned) {
                Spacer(Modifier.height(4.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_clip_pin_fill),
                    contentDescription = null,
                    tint = colors.keyAccent,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        if (menu) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .background(colors.keyPressed.copy(alpha = 0.96f), RoundedCornerShape(14.dp)),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClipMenuAction(
                    iconRes = if (item.pinned) R.drawable.ic_clip_pin_fill else R.drawable.ic_clip_pin,
                    label = if (item.pinned) tr(arabic, "إلغاء التثبيت", "Unpin") else tr(arabic, "تثبيت", "Pin"),
                    ink = colors.keyAccent
                ) {
                    menu = false
                    viewModel.onClipTogglePin(item.text)
                }
                ClipMenuAction(
                    iconRes = R.drawable.ic_clip_trash,
                    label = tr(arabic, "حذف", "Delete"),
                    ink = DeleteRed
                ) {
                    menu = false
                    viewModel.onClipDelete(item.text)
                }
            }
        }
    }
}

@Composable
private fun ClipMenuAction(iconRes: Int, label: String, ink: Color, onClick: () -> Unit) {
    PressBox(modifier = Modifier.size(width = 64.dp, height = 44.dp), onClick = onClick) { _ ->
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
            Icon(painter = painterResource(iconRes), contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
            Text(text = label, color = ink, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ClipImageThumb(name: String, colors: SemoKeyboardColors) {
    val context = LocalContext.current
    val store = remember(context) { ClipImageStore(context) }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, name) {
        value = withContext(Dispatchers.IO) { store.thumbnail(name)?.asImageBitmap() }
    }
    val image = bitmap
    if (image != null) {
        val ratio = (image.width.toFloat() / image.height.toFloat()).coerceIn(1.2f, 2.0f)
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
