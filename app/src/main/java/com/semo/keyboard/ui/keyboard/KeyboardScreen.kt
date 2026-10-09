package com.semo.keyboard.ui.keyboard

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
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
import com.semo.keyboard.domain.logic.EmojiCategory
import com.semo.keyboard.domain.logic.KeyboardLayoutProvider
import com.semo.keyboard.domain.model.ClipItem
import com.semo.keyboard.domain.model.EditAction
import com.semo.keyboard.domain.model.KeyAction
import com.semo.keyboard.domain.model.KeyDefinition
import com.semo.keyboard.domain.model.KeyboardLanguage
import com.semo.keyboard.domain.model.KeyboardPage
import com.semo.keyboard.domain.model.KeyboardUiState
import com.semo.keyboard.domain.model.OneHandMode
import com.semo.keyboard.domain.model.ThemeMode
import com.semo.keyboard.ui.theme.KeyMetrics
import com.semo.keyboard.ui.theme.SemoKeyboardColors
import com.semo.keyboard.ui.theme.keyMetrics
import com.semo.keyboard.ui.theme.semoColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun tr(arabic: Boolean, ar: String, en: String): String = if (arabic) ar else en

/** صف مفاتيح بلا خلفية (أيقونات عائمة) أو فراغات فقط = الصف الأخير تحت المسافة */
private fun isUtilityRow(row: List<KeyDefinition>): Boolean =
    row.isNotEmpty() && row.all { it.plain || it.isSpacer }

/**
 * جذر واجهة اللوحة. نثبّت اتجاه التخطيط LTR دائمًا: مواضع المفاتيح يجب ألا تنعكس
 * على أجهزة اللغة العربية (RTL)، وإلا ينقلب ترتيب الحروف الإنكليزية والعربية معًا.
 *
 * [onChrome] تُبلّغ الخدمة بلون اللوحة ليُلوَّن به شريط التنقل ويُضبط تباين أيقوناته.
 */
@Composable
fun KeyboardScreen(viewModel: KeyboardViewModel, onChrome: (Int, Boolean) -> Unit = { _, _ -> }) {
    val state by viewModel.uiState.collectAsState()
    val isDark = when (state.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = remember(state.style, isDark) { semoColors(state.style, isDark) }
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val metrics = remember(state.style, state.size, screenWidthDp, screenHeightDp, state.oneHand, state.numberRow) {
        // مع اليد الواحدة اللوحة أضيق (0.84 من العرض)، فنحسب القياسات على عرضها الفعلي
        val width = if (state.oneHand != OneHandMode.OFF) screenWidthDp * 0.84f else screenWidthDp.toFloat()
        val base = keyMetrics(state.style, state.size, width)
        if (screenWidthDp > screenHeightDp) {
            // الوضع الأفقي: العرض كبير فكانت المفاتيح تطلع بأقصى ارتفاع وتغطي معظم الشاشة.
            // نحصر ارتفاع اللوحة بحوالي نصف الشاشة كي يبقى الحقل النصي ظاهرًا.
            val rowsCount = if (state.numberRow) 5f else 4f
            val budget = screenHeightDp * 0.52f - base.stripHeight.value - base.utilHeight.value
            val keyH = (budget / rowsCount - base.rowSpacing.value).coerceIn(30f, base.keyHeight.value)
            base.copy(keyHeight = keyH.dp)
        } else base
    }
    val rows = remember(
        state.page, state.shiftState, state.language, state.numberRow, state.enterKind,
        state.englishLayout, state.arabicLayout, state.style, state.arabicDigits, state.fieldKind
    ) { KeyboardLayoutProvider.rows(state) }
    // من أندرويد 15 اللوحة تُرسم خلف شريط التنقل. فبدل ما نترك مساحة فارغة تحت الصف الأخير،
    // نجعل صف الكرة الأرضية والميكروفون بنفس ارتفاع شريط التنقل ونوسّط أيقوناته فيه (مثل آيفون
    // حيث تقع هذه الأيقونات بمستوى مؤشر الرجوع للرئيسية)، فتتنسّق مع شريط One UI السفلي.
    val edgeToEdgeIme = Build.VERSION.SDK_INT >= 35
    val density = LocalDensity.current
    val navInset = if (edgeToEdgeIme) with(density) { WindowInsets.navigationBars.getBottom(density).toDp() } else 0.dp
    // صف الأيقونات بنفس ارتفاع شريط التنقل بالضبط، وأيقونة الكرة تتوسّط ارتفاعه تمامًا، لأن النظام
    // يوسّط أزرار الشريط (زر إخفاء الكيبورد) بنفس المنتصف. ويمكن للمستخدم تعديل الفرق الصغير
    // بين الأجهزة من الإعدادات (globeOffsetDp).
    val utilHeight = if (edgeToEdgeIme) maxOf(navInset, 48.dp) else metrics.utilHeight
    // أزرار النظام (زر إخفاء الكيبورد) تقع بمنتصف شريط ارتفاعه 48dp من أسفل الشاشة حتى لو أبلغ النظام
    // عن inset أصغر (قياس من جهاز One UI: الـ inset ~24dp بينما مركز الزر على 24dp من الأسفل)
    val navCenter = utilHeight / 2
    val globeCenter = (navCenter + state.globeOffsetDp.dp).coerceIn(14.dp, utilHeight - 14.dp)
    // صفحات الإيموجي/الحافظة/التحرير: شريطها السفلي كان يقع تحت زر إخفاء الكيبورد (يتداخل مع لمس النظام)،
    // فنحجز منطقة شريط التنقل أسفل الصفحة ونقصّر المحتوى بنفس المقدار
    val bottomReserve = if (edgeToEdgeIme) utilHeight else 0.dp
    // نسخّن فلترة الإيموجي بخيط خلفي كي لا تعلّق فتح لوحة الإيموجي أول مرة
    LaunchedEffect(Unit) { withContext(Dispatchers.Default) { supportedEmojiCache.size } }
    val rowHeights = remember(rows, metrics, utilHeight) {
        rows.map { if (isUtilityRow(it)) utilHeight else metrics.rowHeight }
    }

    val currentOnChrome by rememberUpdatedState(onChrome)
    LaunchedEffect(colors.panel, isDark) { currentOnChrome(colors.panel.toArgb(), isDark) }

    // لون اللوحة يمتد خلف الشريط؛ لو كان الشريط أطول من صف الأيقونات نكمّل الفرق بحشوة سفلية
    val extraBottom = if (edgeToEdgeIme) (navInset - utilHeight).coerceAtLeast(0.dp) else 8.dp

    val panelShape = RectangleShape

    // ارتفاع صفحات الإيموجي/الحافظة/التحرير = أربعة صفوف + الصف السفلي الإضافي
    val panelHeight = metrics.rowHeight * 4 + utilHeight
    val sideHeight = metrics.stripHeight + panelHeight
    val oneHanded = state.oneHand != OneHandMode.OFF

    // وضع لوحة اللمس (ضغطة مطوّلة على المسافة): تختفي الحروف ويتحرك المؤشر بالسحب
    var trackpad by remember { mutableStateOf(false) }
    // مسار الإصبع أثناء الكتابة بالسحب
    val trail = remember { mutableStateListOf<Offset>() }
    val swipeEnabled = state.swipeTyping && state.page == KeyboardPage.LETTERS &&
        state.alternates.isEmpty() && !trackpad

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.panel, panelShape)
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
                    KeyboardPage.EMOJI -> EmojiPanel(state, colors, panelHeight - bottomReserve, viewModel)
                    KeyboardPage.CLIPBOARD -> ClipboardPanel(state, colors, panelHeight - bottomReserve, viewModel)
                    KeyboardPage.EDIT -> EditPanel(state, colors, panelHeight - bottomReserve, viewModel)
                    else -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .swipeTyping(swipeEnabled, rows, rowHeights, trail) { path ->
                                viewModel.onSwipeWord(path)
                            }
                    ) {
                        Column {
                            rows.forEachIndexed { index, row ->
                                KeyRow(
                                    row = row,
                                    colors = colors,
                                    metrics = metrics,
                                    rowHeight = rowHeights[index],
                                    showPreview = state.keyPreview,
                                    hideLabels = trackpad,
                                    isTopRow = index == 0,
                                    plainCenterFromBottom = globeCenter,
                                    viewModel = viewModel,
                                    onTrackpad = { active ->
                                        trackpad = active
                                        if (active) viewModel.onTrackpadStart()
                                    }
                                )
                            }
                        }
                        SwipeTrail(trail, colors.keyAccent)
                    }
                }
                if (bottomReserve > 0.dp &&
                    (state.page == KeyboardPage.EMOJI || state.page == KeyboardPage.CLIPBOARD || state.page == KeyboardPage.EDIT)
                ) {
                    Spacer(Modifier.height(bottomReserve))
                }
            }

            if (state.oneHand == OneHandMode.LEFT) {
                OneHandSide(state, colors, sideHeight, viewModel)
            }
        }
    }
}

// ============================ الكتابة بالسحب ============================

/** أثر الإصبع أثناء السحب. القراءة داخل الرسم فقط كي لا تُعاد تركيبة اللوحة مع كل نقطة. */
@Composable
private fun BoxScope.SwipeTrail(points: SnapshotStateList<Offset>, color: Color) {
    Canvas(modifier = Modifier.matchParentSize()) {
        if (points.size < 2) return@Canvas
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
        }
        drawPath(
            path = path,
            color = color.copy(alpha = 0.85f),
            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * يراقب اللمس بمرحلة Initial (قبل المفاتيح) دون أن يستهلك الضغطة الأولى، فتبقى الكتابة العادية سليمة.
 * عندما يمر الإصبع على 3 حروف مختلفة على الأقل ويبتعد عن نقطة البداية، نعتبرها كتابة بالسحب:
 * نستهلك الأحداث (فتُلغى ضغطة المفتاح الأصلي) ونرسم الأثر، وعند الرفع نرسل الحروف لفك الكلمة.
 * تحديد الحرف تحت الإصبع حسابي بحت من أوزان المفاتيح وارتفاعات الصفوف، فلا حاجة لقياس كل مفتاح.
 */
private fun Modifier.swipeTyping(
    enabled: Boolean,
    rows: List<List<KeyDefinition>>,
    rowHeights: List<Dp>,
    trail: SnapshotStateList<Offset>,
    onWord: (List<String>) -> Unit
): Modifier {
    if (!enabled) return this
    return pointerInput(rows, rowHeights) {
        val heightsPx = rowHeights.map { it.toPx() }
        val minStep = 3.dp.toPx()
        val startDistance = 18.dp.toPx()

        fun letterAt(pos: Offset): String? {
            if (pos.x < 0f || pos.x >= size.width) return null
            var top = 0f
            for (i in rows.indices) {
                val bottom = top + heightsPx[i]
                if (pos.y < top) return null
                if (pos.y < bottom) {
                    val row = rows[i]
                    val total = row.sumOf { it.weight.toDouble() }.toFloat()
                    if (total <= 0f) return null
                    var left = 0f
                    for (key in row) {
                        val right = left + size.width * (key.weight / total)
                        if (pos.x < right) {
                            val action = key.action
                            return if (!key.isSpacer && action is KeyAction.Character &&
                                action.char.length == 1 && action.char[0].isLetter()
                            ) action.char else null
                        }
                        left = right
                    }
                    return null
                }
                top = bottom
            }
            return null
        }

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val first = letterAt(down.position) ?: return@awaitEachGesture
            val letters = ArrayList<String>()
            letters.add(first)
            var swiping = false
            var lastPoint = down.position
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.changedToUpIgnoreConsumed()) {
                        if (swiping) {
                            change.consume()
                            onWord(letters.toList())
                        }
                        break
                    }
                    // إصبع ثانٍ (كتابة سريعة بإصبعين): نترك الكتابة العادية
                    if (!swiping && event.changes.size > 1) break

                    val pos = change.position
                    val letter = letterAt(pos)
                    if (letter != null && letter != letters.last()) letters.add(letter)
                    if (!swiping && letters.size >= 3 && (pos - down.position).getDistance() > startDistance) {
                        swiping = true
                        trail.clear()
                        trail.add(down.position)
                        lastPoint = down.position
                    }
                    if (swiping) {
                        if ((pos - lastPoint).getDistance() >= minStep) {
                            trail.add(pos)
                            lastPoint = pos
                        }
                        change.consume()
                    }
                }
            } finally {
                trail.clear()
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
                    StripIconButton(R.drawable.ic_ios_emoji, colors) { viewModel.openPage(KeyboardPage.EMOJI) }
                    StripIconButton(R.drawable.ic_tool_clipboard, colors) { viewModel.openPage(KeyboardPage.CLIPBOARD) }
                    StripIconButton(R.drawable.ic_tool_cursor, colors) { viewModel.openPage(KeyboardPage.EDIT) }
                    StripIconButton(R.drawable.ic_tool_onehand, colors) { viewModel.cycleOneHand() }
                    StripIconButton(R.drawable.ic_tool_settings, colors) { viewModel.openSettings() }
                }
            } else {
                SuggestionsRow(state, colors, viewModel)
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

private data class SuggestionSlot(val text: String, val bold: Boolean, val onClick: () -> Unit)

/** اختصار التعبير الحسابي الطويل كي يتسع بالشريط */
private fun shortExpression(expr: String): String =
    if (expr.length > 22) "…" + expr.takeLast(21) else expr

/**
 * شريط الاقتراحات بنمط iOS QuickType: ثلاث خانات تفصلها خطوط رفيعة؛ الأولى الكلمة كما كُتبت بين
 * علامتي اقتباس، والثانية الاقتراح الأول (بخط عريض). وعند كتابة عملية حسابية يظهر ناتجها بدل الخانات.
 */
@Composable
private fun RowScope.SuggestionsRow(
    state: KeyboardUiState,
    colors: SemoKeyboardColors,
    viewModel: KeyboardViewModel
) {
    val math = state.mathResult
    if (math != null) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable { viewModel.onMathChosen() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${shortExpression(math.expression)} = ${math.value}",
                color = colors.suggestion,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
        return
    }

    val slots = buildList {
        if (state.literal.isNotEmpty()) {
            add(SuggestionSlot("“${state.literal}”", false) { viewModel.onSuggestionChosen(state.literal) })
            state.suggestions.take(2).forEachIndexed { index, word ->
                add(SuggestionSlot(word, index == 0) { viewModel.onSuggestionChosen(word) })
            }
        } else {
            state.suggestions.take(3).forEach { word ->
                add(SuggestionSlot(word, false) { viewModel.onSuggestionChosen(word) })
            }
        }
    }

    Row(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        slots.forEachIndexed { index, slot ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(22.dp)
                        .background(colors.divider)
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(onClick = slot.onClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = slot.text,
                    color = colors.suggestion,
                    fontSize = 17.sp,
                    fontWeight = if (slot.bold) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = TextDirection.Content),
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
        }
        if (slots.isEmpty()) Spacer(Modifier.weight(1f))
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
    hideLabels: Boolean,
    isTopRow: Boolean,
    plainCenterFromBottom: Dp,
    viewModel: KeyboardViewModel,
    onTrackpad: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        row.forEach { key ->
            KeyButton(
                def = key,
                colors = colors,
                metrics = metrics,
                rowHeight = rowHeight,
                showPreview = showPreview,
                hideLabels = hideLabels,
                isTopRow = isTopRow,
                plainCenterFromBottom = plainCenterFromBottom,
                onKey = viewModel::onKeyPressed,
                onLongPress = viewModel::onKeyLongPressed,
                onCursor = viewModel::onCursorMove,
                onTrackpad = onTrackpad
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
    hideLabels: Boolean,
    isTopRow: Boolean,
    plainCenterFromBottom: Dp,
    onKey: (KeyAction) -> Unit,
    onLongPress: (KeyDefinition) -> Unit,
    onCursor: (Int, Int) -> Unit,
    onTrackpad: (Boolean) -> Unit
) {
    if (def.isSpacer) {
        Spacer(Modifier.weight(def.weight))
        return
    }

    var pressed by remember { mutableStateOf(false) }
    val currentOnKey by rememberUpdatedState(onKey)
    val currentOnLong by rememberUpdatedState(onLongPress)
    val currentOnCursor by rememberUpdatedState(onCursor)
    val currentOnTrackpad by rememberUpdatedState(onTrackpad)
    val hasAlternates = def.longPressChars.isNotEmpty()
    val isBackspace = def.action == KeyAction.Backspace
    val isSpace = def.action == KeyAction.Space
    val isLetterKey = def.action is KeyAction.Character
    val special = isSpecial(def)

    val shiftOn = def.action == KeyAction.Shift && def.isAccent
    val bg = when {
        // الكرة الأرضية: قرص بنفس شكل زر إخفاء الكيبورد بالنظام لتتقابلا بصريًا
        def.plain -> if (pressed) colors.key else colors.keySpecial
        shiftOn -> colors.shiftActive
        def.isAccent -> colors.keyAccent
        pressed && special -> colors.key
        // مع معاينة الحرف تبقى مفاتيح الحروف بلونها لأن الفقاعة هي التي تُظهر الضغط (مثل iOS)
        pressed && !(showPreview && isLetterKey) -> colors.keyPressed
        special -> colors.keySpecial
        else -> colors.key
    }
    val textColor = when {
        shiftOn -> colors.onShiftActive
        def.isAccent -> colors.textOnAccent
        else -> colors.text
    }
    val shadowColor = colors.keyShadow

    // شريط المسافة: ضغطة = مسافة، وضغطة مطوّلة ثم سحب = لوحة لمس لتحريك المؤشر (مثل آيفون).
    // باقي المفاتيح: ضغط/ضغط مطوّل.
    val gestureModifier = if (isSpace) {
        Modifier.pointerInput(def) {
            spaceGestures(
                onPressedChange = { pressed = it },
                onTap = { currentOnKey(KeyAction.Space) },
                onTrackpad = { currentOnTrackpad(it) },
                onMove = { dx, dy -> currentOnCursor(dx, dy) }
            )
        }
    } else if (isBackspace) {
        // الحذف: فوري ثم تكرار تلقائي عند الاستمرار بالضغط
        Modifier.pointerInput(def) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    currentOnKey(KeyAction.Backspace)
                    coroutineScope {
                        val repeat = launch {
                            delay(400)
                            var repeats = 0
                            while (true) {
                                currentOnKey(KeyAction.Backspace)
                                repeats++
                                // يتسارع الحذف مع طول الضغط (مثل iOS)
                                delay(if (repeats < 10) 55L else 32L)
                            }
                        }
                        tryAwaitRelease()
                        repeat.cancel()
                    }
                    pressed = false
                }
            )
        }
    } else {
        // باقي المفاتيح: ضغطة تُسجَّل عند رفع الإصبع حتى لو تحرّك قليلًا (بدون حدّ الانزلاق)،
        // لأن detectTapGestures كان يُلغي الضغطة بالكتابة السريعة فتبدو اللوحة "ما بتلحق".
        val longPress: (() -> Unit)? = when {
            hasAlternates -> ({ currentOnLong(def) })
            def.longPressAction != null -> ({ currentOnKey(def.longPressAction) })
            else -> null
        }
        Modifier.pointerInput(def) {
            keyGestures(
                onPressedChange = { pressed = it },
                onTap = { currentOnKey(def.action) },
                onLongPress = longPress
            )
        }
    }

    // منطقة اللمس تشمل الفراغات بين المفاتيح (تقلّل الضغطات الضائعة)، والشكل المرئي أصغر منها
    val verticalPadding = if (def.plain) 0.dp else metrics.rowSpacing / 2
    Box(
        modifier = Modifier
            .weight(def.weight)
            .height(rowHeight)
            .zIndex(if (pressed) 1f else 0f)
            .then(gestureModifier)
            .padding(
                // القرص يبدأ بنفس الهامش (~12dp) الذي يبعد به زر النظام عن الحافة المقابلة
                start = metrics.keySpacing / 2 + (if (def.plain) 6.dp else 0.dp),
                end = metrics.keySpacing / 2,
                top = if (def.plain) 0.dp else verticalPadding,
                // القرص (ارتفاعه 42dp) تلتصق بأسفل الصف مع حشوة سفلية تجعل مركزها
                // على بعد plainCenterFromBottom من أسفل الشاشة = نفس مستوى أزرار شريط التنقل
                bottom = if (def.plain) (plainCenterFromBottom - 21.dp).coerceAtLeast(0.dp) else verticalPadding
            ),
        contentAlignment = if (def.plain) Alignment.BottomStart else Alignment.TopStart
    ) {
        Box(
            modifier = (if (def.plain) Modifier.widthIn(max = 92.dp).fillMaxWidth().height(42.dp).offset(y = (21.dp - plainCenterFromBottom).coerceAtLeast(0.dp)) else Modifier.fillMaxSize())
                .drawBehind {
                    if (!def.plain) {
                        drawRoundRect(
                            color = shadowColor,
                            topLeft = Offset(0f, 1.dp.toPx()),
                            size = size,
                            cornerRadius = CornerRadius(metrics.keyRadius.toPx())
                        )
                    }
                }
                .background(bg, if (def.plain) RoundedCornerShape(50) else metrics.keyShape),
            contentAlignment = Alignment.Center
        ) {
            if (!hideLabels) {
                KeyContent(def, textColor, iconSize = if (def.plain) 24.dp else 23.dp)
            }
        }

        // فقاعة معاينة الحرف بشكل بالون iOS: رأس أعرض من المفتاح يتصل به برقبة، ويغطي المفتاح كله.
        // بالصف الأول نقصّر الرأس كي لا تقصّه نافذة اللوحة.
        if (showPreview && pressed && isLetterKey && !hideLabels) {
            val density = LocalDensity.current
            val headHeight = if (isTopRow) 40.dp else 56.dp
            val inset = 10.dp
            val balloonShape = remember(density, metrics) {
                BalloonShape(
                    insetPx = with(density) { inset.toPx() },
                    keyHeightPx = with(density) { metrics.keyHeight.toPx() },
                    headRadiusPx = with(density) { 11.dp.toPx() },
                    keyRadiusPx = with(density) { metrics.keyRadius.toPx() }
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .layout { measurable, constraints ->
                        val insetPx = inset.roundToPx()
                        val headPx = headHeight.roundToPx()
                        val keyW = constraints.maxWidth
                        val w = keyW + insetPx * 2
                        val h = headPx + metrics.keyHeight.roundToPx()
                        val placeable = measurable.measure(Constraints.fixed(w, h))
                        layout(keyW, metrics.keyHeight.roundToPx()) {
                            placeable.place(-insetPx, -headPx)
                        }
                    }
                    .shadow(3.dp, balloonShape)
                    .background(colors.bubble, balloonShape)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(headHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = def.label, color = colors.text, fontSize = 34.sp, maxLines = 1)
                }
            }
        }
    }
}

/** شكل بالون معاينة الحرف: رأس مدوّر أعرض من المفتاح + رقبة + قاعدة بشكل المفتاح */
private class BalloonShape(
    private val insetPx: Float,
    private val keyHeightPx: Float,
    private val headRadiusPx: Float,
    private val keyRadiusPx: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val keyTop = h - keyHeightPx
        val head = Path().apply {
            addRoundRect(RoundRect(0f, 0f, w, keyTop, CornerRadius(headRadiusPx)))
        }
        val neck = Path().apply {
            addRect(androidx.compose.ui.geometry.Rect(insetPx, keyTop - headRadiusPx, w - insetPx, keyTop + keyRadiusPx))
        }
        val base = Path().apply {
            addRoundRect(RoundRect(insetPx, keyTop, w - insetPx, h, CornerRadius(keyRadiusPx)))
        }
        val joined = Path.combine(PathOperation.Union, Path.combine(PathOperation.Union, head, neck), base)
        return Outline.Generic(joined)
    }
}

/**
 * إيماءة المفتاح العادي: الضغطة تُسجَّل عند رفع الإصبع بدون إلغاء بسبب حركة صغيرة،
 * وتُلغى فقط لو استهلكها حدث آخر (كالكتابة بالسحب). الضغطة المطوّلة (إن وُجدت) تُنفَّذ بعد المهلة.
 */
private suspend fun PointerInputScope.keyGestures(
    onPressedChange: (Boolean) -> Unit,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)?
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onPressedChange(true)
        try {
            // 1 = رُفع الإصبع (ضغطة)، 2 = أُلغيت، null = انتهت مهلة الضغط المطوّل
            val timeout = if (onLongPress != null) viewConfiguration.longPressTimeoutMillis else Long.MAX_VALUE
            val outcome: Int? = withTimeoutOrNull(timeout) {
                var result = 0
                while (result == 0) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    result = when {
                        change == null -> 2
                        change.isConsumed -> 2
                        change.changedToUpIgnoreConsumed() -> 1
                        else -> 0
                    }
                }
                result
            }
            when (outcome) {
                1 -> onTap()
                null -> {
                    onLongPress?.invoke()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUpIgnoreConsumed()) break
                    }
                }
                else -> Unit
            }
        } finally {
            onPressedChange(false)
        }
    }
}

/**
 * إيماءة شريط المسافة: لمسة قصيرة = مسافة. ضغطة مطوّلة = وضع لوحة اللمس: سحب بأي اتجاه يحرّك
 * المؤشر (كل ~10dp أفقيًا = حرف، وكل ~26dp عموديًا = سطر). الحركة قبل انتهاء مهلة الضغط المطوّل تُلغي الإيماءة.
 */
private suspend fun PointerInputScope.spaceGestures(
    onPressedChange: (Boolean) -> Unit,
    onTap: () -> Unit,
    onTrackpad: (Boolean) -> Unit,
    onMove: (Int, Int) -> Unit
) {
    val stepX = 10.dp.toPx()
    val stepY = 26.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onPressedChange(true)
        try {
            // 1 = رُفع الإصبع (لمسة قصيرة)، 2 = تحرك الإصبع (إلغاء)، null = انتهت المهلة (ضغطة مطوّلة)
            val outcome: Int? = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var result = 0
                while (result == 0) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    result = when {
                        change == null -> 2
                        change.changedToUpIgnoreConsumed() -> 1
                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop -> 2
                        else -> 0
                    }
                }
                result
            }
            when (outcome) {
                1 -> onTap()
                null -> {
                    onTrackpad(true)
                    var accX = 0f
                    var accY = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUpIgnoreConsumed()) {
                            change.consume()
                            break
                        }
                        val delta = change.positionChange()
                        accX += delta.x
                        accY += delta.y
                        val stepsX = (accX / stepX).toInt()
                        val stepsY = (accY / stepY).toInt()
                        if (stepsX != 0 || stepsY != 0) {
                            onMove(stepsX, stepsY)
                            accX -= stepsX * stepX
                            accY -= stepsY * stepY
                        }
                        change.consume()
                    }
                }
                else -> Unit
            }
        } finally {
            onPressedChange(false)
            onTrackpad(false)
        }
    }
}

@Composable
private fun KeyContent(def: KeyDefinition, textColor: Color, iconSize: Dp) {
    val iconRes = if (def.textOnly) null else when (def.action) {
        KeyAction.Shift -> when {
            def.label == "⇪" -> R.drawable.ic_ios_shift_locked
            def.isAccent -> R.drawable.ic_ios_shift_fill
            else -> R.drawable.ic_ios_shift
        }
        KeyAction.Backspace -> R.drawable.ic_ios_backspace
        KeyAction.Globe, KeyAction.SwitchLanguage -> R.drawable.ic_ios_globe
        KeyAction.Emoji -> R.drawable.ic_ios_emoji
        KeyAction.Mic -> R.drawable.ic_ios_mic
        KeyAction.Enter -> R.drawable.ic_ios_return
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
                isLetter && def.label.length > 1 -> 16.sp
                isLetter -> 25.sp
                def.action == KeyAction.Space -> 16.sp
                def.action == KeyAction.Enter -> 16.sp
                def.label.length > 2 -> 16.sp
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

/** يخفي الإيموجي التي لا يملك خط الجهاز رمزًا لها (كي لا تظهر مربعات فارغة) */
private fun supportedEmojiCategories(): List<EmojiCategory> {
    val paint = android.graphics.Paint()
    return KeyboardLayoutProvider.emojiCategories.map { cat ->
        val ok = cat.emojis.filter { runCatching { paint.hasGlyph(it) }.getOrDefault(true) }
        // لو الفحص رفض معظم القائمة فهو غير موثوق على هذا الجهاز: نعرض القائمة كاملة
        cat.copy(emojis = if (ok.size * 2 >= cat.emojis.size) ok else cat.emojis)
    }.filter { it.emojis.isNotEmpty() }
}

private val supportedEmojiCache: List<EmojiCategory> by lazy { supportedEmojiCategories() }

@Composable
private fun EmojiPanel(
    state: KeyboardUiState,
    colors: SemoKeyboardColors,
    panelHeight: Dp,
    viewModel: KeyboardViewModel
) {
    val arabic = state.language == KeyboardLanguage.ARABIC
    val categories = supportedEmojiCache
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
                Column(modifier = Modifier.fillMaxSize()) {
                    val title = when {
                        tab == 0 -> tr(arabic, "المستخدمة مؤخرًا", "Frequently Used")
                        arabic -> categories[tab - 1].titleAr
                        else -> categories[tab - 1].titleEn
                    }
                    Text(
                        text = title,
                        color = colors.textMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(8),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        items(list) { emoji ->
                            Box(
                                modifier = Modifier
                                    .height(44.dp)
                                    .pointerInput(emoji) { detectTapGestures(onTap = { viewModel.onEmojiPressed(emoji) }) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 26.sp, color = colors.text, maxLines = 1)
                            }
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
                    painter = painterResource(R.drawable.ic_ios_backspace),
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
