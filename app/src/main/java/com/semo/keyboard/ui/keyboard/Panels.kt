package com.semo.keyboard.ui.keyboard

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import android.graphics.Paint
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import android.graphics.Typeface
import android.util.Patterns
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.core.tween
import com.semo.keyboard.ui.anim.LocalAnimScale
import com.semo.keyboard.ui.anim.scaledSpring
import com.semo.keyboard.ui.anim.scaledTween
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
    titlePaint: Paint,
    scaleCode: Int,
    pressScale: Float,
    linePaint: Paint
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
            color = highlight.copy(alpha = 0.85f),
            topLeft = Offset(pcol * cw - offset + 2f, layout.headerH + prow * ch + 2f),
            size = Size(cw - 4f, ch - 4f),
            cornerRadius = CornerRadius(ch * 0.34f)
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
                val popped = scaleCode == s * CODE_BASE + index && pressScale > 1.001f
                if (popped) {
                    nc.save()
                    nc.scale(pressScale, pressScale, cx, cy)
                }
                if (bitmap != null) {
                    nc.drawBitmap(bitmap, cx - bitmap.width / 2f, cy - bitmap.height / 2f, null)
                } else if (EmojiBitmapCache.emPx > 0f) {
                    val p = emojiPaint
                    p.textSize = EmojiBitmapCache.emPx
                    val fm = p.fontMetrics
                    nc.drawText(emoji, cx, cy - (fm.ascent + fm.descent) / 2f, p)
                }
                if (popped) nc.restore()
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
            // خط رفيع بعد العنوان حتى نهاية القسم (يفصل الأقسام بصريًا)
            val lineStart = x + textWidth + pad * 0.8f
            val lineEnd = sectionEnd - pad
            if (lineEnd - lineStart > pad * 2f) {
                val ly = layout.headerH * 0.72f - titlePaint.textSize * 0.32f
                nc.drawLine(lineStart, ly, lineEnd, ly, linePaint)
            }
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
    val arabic = state.language.rtl
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
    // تكبير الإيموجي المضغوط بنبضة ناعمة، مع الاحتفاظ بآخر خلية ليرجع حجمها بسلاسة بعد الرفع
    var scaleCode by remember { mutableIntStateOf(-1) }
    if (pressedCode >= 0) scaleCode = pressedCode
    val pressScale by animateFloatAsState(
        targetValue = if (pressedCode >= 0) 1.22f else 1f,
        animationSpec = scaledSpring(LocalAnimScale.current, dampingRatio = 0.76f, stiffness = 520f),
        label = "emojiPress"
    )
    // بعد انتهاء الفلنغ نثبّت على أقرب عمود بنابض ناعم (إحساس صفحات iOS بدل التوقف العشوائي)
    val baseFling = ScrollableDefaults.flingBehavior()
    val snapFling = remember(layout, baseFling) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                with(baseFling) { performFling(initialVelocity) }
                val cw = layout?.cellW ?: return 0f
                val target = (Math.round(offset / cw) * cw).coerceIn(0f, maxOffset)
                val d = target - offset
                if (kotlin.math.abs(d) > 0.5f) {
                    var prev = 0f
                    animate(0f, d, animationSpec = spring(dampingRatio = 1f, stiffness = 320f)) { v, _ ->
                        scrollBy(v - prev)
                        prev = v
                    }
                }
                return 0f
            }
        }
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
    val linePaint = remember(colors.divider, density.density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.divider.toArgb()
            strokeWidth = 1f * density.density
            strokeCap = Paint.Cap.ROUND
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
                            reverseDirection = true,
                            flingBehavior = snapFling
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
                        .drawBehind { drawEmojiPager(layout, sections, offset, pressedCode, highlight, titlePaint, scaleCode, pressScale, linePaint) }
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
                    val pill by animateColorAsState(
                        if (selected) colors.keyAccent.copy(alpha = 0.20f) else Color.Transparent,
                        tween(260, easing = com.semo.keyboard.ui.anim.IosEaseInOut), label = "catPill"
                    )
                    val tint by animateColorAsState(
                        if (selected) colors.keyAccent else colors.textMuted,
                        tween(260, easing = com.semo.keyboard.ui.anim.IosEaseInOut), label = "catTint"
                    )
                    val iconScale by animateFloatAsState(
                        if (selected) 1.1f else 1f,
                        spring(dampingRatio = 0.8f, stiffness = 500f), label = "catScale"
                    )
                    PressBox(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        onClick = {
                            viewModel.onSelectionTick()
                            val target = ((layout?.startCol?.get(index) ?: 0) * (layout?.cellW ?: 0f)).coerceIn(0f, maxOffset)
                            scope.launch { scrollState.animateScrollBy(target - offset, tween(450, easing = com.semo.keyboard.ui.anim.IosPush)) }
                        }
                    ) { pressed ->
                        Box(
                            modifier = Modifier
                                .size(width = 32.dp, height = 30.dp)
                                .background(pill, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(section.iconRes),
                                contentDescription = section.title,
                                tint = if (pressed && !selected) colors.text else tint,
                                modifier = Modifier
                                    .size(19.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    }
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

/** وسم البطاقة (مفرد): نوع العنصر، ومع النص الطويل يُضاف عدد أحرفه */

/**
 * الحافظة: صف علوي (زر الرجوع للوحة | العنوان | تثبيت (عرض المثبّتة فقط) | حذف غير المثبّت) وتحته شبكة بطاقات
 * بثلاثة أعمدة بارتفاع موحّد. ضغطة = لصق، وضغطة مطوّلة = خياران (تثبيت/حذف) على نفس البطاقة.
 */
@Composable
internal fun ClipboardPanel(
    state: KeyboardUiState,
    colors: SemoKeyboardColors,
    panelHeight: Dp,
    bottomInset: Dp,
    viewModel: KeyboardViewModel
) {
    val arabic = state.language.rtl
    var pinnedOnly by remember { mutableStateOf(false) }
    val hasPinned = remember(state.clipItems) { state.clipItems.any { it.pinned } }
    val hasUnpinned = remember(state.clipItems) { state.clipItems.any { !it.pinned } }
    val shownItems = remember(state.clipItems, pinnedOnly, hasPinned) {
        if (pinnedOnly && hasPinned) state.clipItems.filter { it.pinned }
        else state.clipItems.sortedByDescending { it.pinned }
    }

    var menuKey by remember { mutableStateOf<String?>(null) }
    val menuItem = menuKey?.let { k -> state.clipItems.firstOrNull { it.text == k } }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(panelHeight)
    ) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PressBox(modifier = Modifier.size(width = 48.dp, height = 40.dp), onClick = {
                viewModel.onSelectionTick()
                viewModel.openPage(KeyboardPage.LETTERS)
            }) { _ ->
                Icon(
                    painter = painterResource(R.drawable.ic_clip_keyboard),
                    contentDescription = null,
                    tint = colors.text,
                    modifier = Modifier.size(26.dp)
                )
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .width(1.dp)
                    .height(18.dp)
                    .background(colors.divider)
            )
            Text(
                text = tr(arabic, "الحافظة", "Clipboard") + if (state.clipItems.isNotEmpty()) "  ·  ${state.clipItems.size}" else "",
                color = colors.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            PressBox(modifier = Modifier.size(width = 52.dp, height = 40.dp), onClick = {
                viewModel.onSelectionTick()
                pinnedOnly = !pinnedOnly
            }) { _ ->
                Icon(
                    painter = painterResource(if (pinnedOnly) R.drawable.ic_clip_pin_fill else R.drawable.ic_clip_pin),
                    contentDescription = null,
                    tint = if (pinnedOnly) colors.keyAccent else colors.text,
                    modifier = Modifier.size(26.dp)
                )
            }
            PressBox(modifier = Modifier.size(width = 52.dp, height = 40.dp), onClick = {
                if (hasUnpinned) {
                    viewModel.onSelectionTick()
                    viewModel.onClipClearUnpinned()
                }
            }) { _ ->
                Icon(
                    painter = painterResource(R.drawable.ic_clip_trash),
                    contentDescription = null,
                    tint = if (hasUnpinned) colors.text else colors.textMuted,
                    modifier = Modifier.size(26.dp)
                )
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
                shownItems.isEmpty() -> ClipEmptyState(
                    tr(arabic, "الحافظة فاضية", "Nothing copied yet"),
                    tr(arabic, "انسخ نصًا أو رابطًا أو كودًا أو صورة وسيظهر هنا", "Copy text, a link, code or an image and it shows up here"),
                    colors
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 8.dp + bottomInset),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(shownItems, key = { it.text }) { item ->
                        ClipCard(item, colors, item.text == menuKey, { menuKey = item.text }, viewModel, Modifier.animateItem())
                    }
                }
            }
        }
    }
    if (menuItem != null) {
        ClipActionBubble(menuItem, arabic, colors, bottomInset, { menuKey = null }, viewModel)
    }
    }
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
private fun ClipBody(item: ClipItem, kind: ClipKind, colors: SemoKeyboardColors, maxTextLines: Int) {
    val shown = remember(item.text) { item.text.clipDisplayText() }
    when (kind) {
        ClipKind.IMAGE -> ClipImageThumb(item.text.clipImageName(), colors)
        ClipKind.CODE -> Text(
            text = shown,
            color = colors.text,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = maxTextLines + 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(textDirection = TextDirection.Ltr)
        )
        ClipKind.LINK -> Text(
            text = shown.trim(),
            color = colors.keyAccent,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            maxLines = maxTextLines - 2,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(textDirection = TextDirection.Ltr)
        )
        ClipKind.TEXT -> Text(
            text = shown,
            color = colors.text,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            maxLines = maxTextLines,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(textDirection = TextDirection.Content)
        )
    }
}

@Composable
private fun ClipCard(
    item: ClipItem,
    colors: SemoKeyboardColors,
    selected: Boolean,
    onMenu: () -> Unit,
    viewModel: KeyboardViewModel,
    modifier: Modifier = Modifier
) {
    val kind = remember(item.text) { ClipClassifier.kindOf(item.text) }
    val shape = RoundedCornerShape(16.dp)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed || selected) 0.96f else 1f,
        spring(dampingRatio = 0.86f, stiffness = 520f),
        label = "clipScale"
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .fillMaxWidth()
            .height(112.dp)
            .drawBehind {
                drawRoundRect(
                    color = colors.keyShadow.copy(alpha = 0.55f),
                    topLeft = Offset(0f, 1.5.dp.toPx()),
                    size = size,
                    cornerRadius = CornerRadius(16.dp.toPx())
                )
            }
            .background(colors.key, shape)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) colors.keyAccent else colors.divider, shape)
            .clip(shape)
            .pointerInput(item.text) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = {
                        viewModel.onSelectionTick()
                        viewModel.onClipPaste(item.text)
                    },
                    onLongPress = {
                        viewModel.onSelectionTick()
                        onMenu()
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            ClipBody(item, kind, colors, 4)
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
        if (item.text.isClipLong()) {
            val n = remember(item.text) { item.text.clipLongLength() }
            Text(
                text = if (n >= 1000) "${n / 1000}K" else "$n",
                color = colors.textMuted,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 6.dp)
                    .background(colors.chip, RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
        if (kind == ClipKind.IMAGE) {
            PressBox(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp),
                onClick = {
                    viewModel.onSelectionTick()
                    viewModel.onClipDelete(item.text)
                }
            ) { _ ->
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_clip_trash),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

/**
 * فقاعة الضغطة المطوّلة على عنصر الحافظة (بنمط قائمة iOS): خلفية معتمة، بطاقة معاينة العنصر بحدّ أزرق،
 * وتحتها فقاعة إجراءات: لصق، تعديل، فتح (للروابط)، تثبيت/إلغاء التثبيت، حذف.
 */
@Composable
private fun BoxScope.ClipActionBubble(
    item: ClipItem,
    arabic: Boolean,
    colors: SemoKeyboardColors,
    bottomInset: Dp,
    onDismiss: () -> Unit,
    viewModel: KeyboardViewModel
) {
    val kind = remember(item.text) { ClipClassifier.kindOf(item.text) }
    val linkUrl = remember(item.text, kind) { if (kind == ClipKind.LINK) ClipClassifier.extractUrl(item.text) else null }
    val context = LocalContext.current
    val cardShape = RoundedCornerShape(18.dp)
    val appear = remember { Animatable(0f) }
    val bubbleScale = LocalAnimScale.current
    LaunchedEffect(Unit) { appear.animateTo(1f, scaledSpring(bubbleScale, dampingRatio = 0.88f, stiffness = 420f)) }
    Box(
        modifier = Modifier
            .matchParentSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.5f * appear.value.coerceIn(0f, 1f))) }
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .graphicsLayer {
                    val sc = 0.84f + 0.16f * appear.value
                    scaleX = sc
                    scaleY = sc
                    alpha = appear.value.coerceIn(0f, 1f)
                }
                .padding(bottom = bottomInset)
                .width(236.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = { }) },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .shadow(14.dp, cardShape)
                    .background(colors.key, cardShape)
                    .border(1.5.dp, colors.keyAccent, cardShape)
                    .clip(cardShape)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                ClipBody(item, kind, colors, 3)
            }
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(14.dp, cardShape)
                    .background(colors.bubble, cardShape)
                    .clip(cardShape)
            ) {
                ClipBubbleRow(R.drawable.ic_clip_paste, tr(arabic, "لصق", "Paste"), colors.text, arabic, colors, true) {
                    onDismiss()
                    viewModel.onClipPaste(item.text)
                }
                if (kind != ClipKind.IMAGE) {
                    ClipBubbleRow(R.drawable.ic_clip_edit, tr(arabic, "تعديل", "Edit"), colors.text, arabic, colors, true) {
                        onDismiss()
                        viewModel.onClipEditStart(item.text)
                    }
                }
                if (linkUrl != null) {
                    ClipBubbleRow(R.drawable.ic_clip_open_link, tr(arabic, "فتح الرابط", "Open link"), colors.text, arabic, colors, true) {
                        onDismiss()
                        runCatching {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(linkUrl))
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    }
                }
                ClipBubbleRow(
                    if (item.pinned) R.drawable.ic_clip_pin_fill else R.drawable.ic_clip_pin,
                    if (item.pinned) tr(arabic, "إلغاء التثبيت", "Unpin") else tr(arabic, "تثبيت", "Pin"),
                    colors.text, arabic, colors, true
                ) {
                    onDismiss()
                    viewModel.onClipTogglePin(item.text)
                }
                ClipBubbleRow(R.drawable.ic_clip_trash, tr(arabic, "حذف", "Delete"), DeleteRed, arabic, colors, false) {
                    onDismiss()
                    viewModel.onClipDelete(item.text)
                }
            }
        }
    }
}

@Composable
private fun ClipBubbleRow(
    iconRes: Int,
    label: String,
    ink: Color,
    arabic: Boolean,
    colors: SemoKeyboardColors,
    divider: Boolean,
    onClick: () -> Unit
) {
    PressBox(modifier = Modifier.fillMaxWidth().height(46.dp), onClick = onClick) { pressed ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (pressed) colors.keyPressed else Color.Transparent)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = @Composable {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = ink,
                        modifier = Modifier.size(20.dp)
                    )
                }
                val text = @Composable {
                    Text(text = label, color = ink, fontSize = 16.sp, maxLines = 1)
                }
                if (arabic) {
                    icon()
                    Spacer(Modifier.weight(1f))
                    text()
                } else {
                    text()
                    Spacer(Modifier.weight(1f))
                    icon()
                }
            }
            if (divider) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.divider)
                )
            }
        }
    }
}

/** شريط التعديل بدل شريط الاقتراحات: ✕ إلغاء | النص قيد التعديل | ✓ حفظ (الكتابة تذهب لنص التعديل لا لحقل التطبيق) */
@Composable
internal fun ClipEditBar(
    state: KeyboardUiState,
    colors: SemoKeyboardColors,
    stripHeight: Dp,
    viewModel: KeyboardViewModel
) {
    val arabic = state.language.rtl
    val text = state.clipEdit?.text ?: ""
    val preview = remember(text) {
        val t = text.replace("\n", "↵")
        if (t.length > 60) "…" + t.takeLast(59) else t
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(stripHeight)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PressBox(modifier = Modifier.size(width = 44.dp, height = 40.dp), onClick = {
            viewModel.onSelectionTick()
            viewModel.onClipEditCancel()
        }) { pressed ->
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(if (pressed) colors.keyPressed else colors.keySpecial, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "✕", color = colors.text, fontSize = 14.sp)
            }
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
                .background(colors.chip, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = tr(arabic, "تعديل", "Edit"),
                color = colors.keyAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (preview.isEmpty()) tr(arabic, "اكتب النص المعدّل…", "Type the edited text…") else preview,
                color = if (preview.isEmpty()) colors.textMuted else colors.text,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(textDirection = TextDirection.Content),
                modifier = Modifier.weight(1f)
            )
        }
        PressBox(modifier = Modifier.size(width = 44.dp, height = 40.dp), onClick = {
            viewModel.onSelectionTick()
            viewModel.onClipEditSave()
        }) { pressed ->
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(if (pressed) colors.keyAccent.copy(alpha = 0.8f) else colors.keyAccent, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "✓", color = colors.textOnAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
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
    val arabic = state.language.rtl
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
