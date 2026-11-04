package io.github.tima22814.retromusic.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun gloss(vararg c: Long) = Brush.verticalGradient(
    0f to Color(c[0]), 0.5f to Color(c[1]), 0.51f to Color(c[2]), 1f to Color(c[3]),
)

/** All colours and glossy fills of one look. Read through [C] inside composables. */
@Immutable
class Palette(
    val light: Boolean,
    val Bg: Color,
    val Row: Color,
    val RowPress: Color,
    val Sep: Color,
    val Fg: Color,
    val Fg2: Color,
    val Muted: Color,
    val Dim: Color,
    val Accent: Color,
    val HeaderTop: Color,
    val Nav: Brush,
    val NavTitle: Color,
    val NavShadow: Color,
    val Button: Brush,
    val ButtonPressed: Brush,
    val ButtonText: Color,
    val ButtonBorder: Color,
    val Section: Brush,
    val SectionText: Color,
    val SectionTop: Color,
    val SectionBottom: Color,
    val Tabs: Brush,
    val TabIcon: Brush,
    val TabOn: Color,
    val TabOff: Color,
    val Controls: Brush,
)

/* Dark follows Omarchy vantablack: black base, grey accents, white controls. */
val DarkPalette = Palette(
    light = false,
    Bg = Color(0xFF000000),
    Row = Color(0xFF090909),
    RowPress = Color(0xFF1A1A1A),
    Sep = Color(0xFF1A1A1A),
    Fg = Color(0xFFFFFFFF),
    Fg2 = Color(0xFFECECEC),
    Muted = Color(0xFF7A7A7A),
    Dim = Color(0xFF505050),
    Accent = Color(0xFF8D8D8D),
    HeaderTop = Color(0xFF141414),
    Nav = gloss(0xFF2A2A2A, 0xFF161616, 0xFF0B0B0B, 0xFF050505),
    NavTitle = Color.White,
    NavShadow = Color.Black,
    Button = gloss(0xFFFFFFFF, 0xFFE6E6E6, 0xFFCFCFCF, 0xFFBDBDBD),
    ButtonPressed = Brush.verticalGradient(listOf(Color(0xFFBDBDBD), Color(0xFFD9D9D9))),
    ButtonText = Color.Black,
    ButtonBorder = Color.Black,
    Section = Brush.verticalGradient(listOf(Color(0xF23C3C3C), Color(0xF2222222))),
    SectionText = Color.White,
    SectionTop = Color(0xFF444444),
    SectionBottom = Color.Black,
    Tabs = gloss(0xFF2B2B2B, 0xFF141414, 0xFF000000, 0xFF000000),
    TabIcon = gloss(0xFFFFFFFF, 0xFFE9E9E9, 0xFFBDBDBD, 0xFFF5F5F5),
    TabOn = Color.White,
    TabOff = Color(0xFF505050),
    Controls = gloss(0xFF262626, 0xFF121212, 0xFF000000, 0xFF000000),
)

/* Light is the classic look: blue-grey glossy bars, white lists, blue buttons and tab icons. */
val LightPalette = Palette(
    light = true,
    Bg = Color.White,
    Row = Color.White,
    RowPress = Color(0xFFD5E3F5),
    Sep = Color(0xFFE0E0E0),
    Fg = Color.Black,
    Fg2 = Color(0xFF222222),
    Muted = Color(0xFF7F7F7F),
    Dim = Color(0xFFA6A6A6),
    Accent = Color(0xFF5E6E82),
    HeaderTop = Color(0xFFF2F4F7),
    Nav = gloss(0xFFB2BFD0, 0xFF8EA1B9, 0xFF8195AF, 0xFF6D84A2),
    NavTitle = Color.White,
    NavShadow = Color(0xFF2D3642),
    Button = gloss(0xFF91A6C3, 0xFF6E89AF, 0xFF5B78A3, 0xFF4E6D9B),
    ButtonPressed = Brush.verticalGradient(listOf(Color(0xFF3D5A85), Color(0xFF5274A5))),
    ButtonText = Color.White,
    ButtonBorder = Color(0xFF2F3E53),
    Section = Brush.verticalGradient(listOf(Color(0xF2A5B1BD), Color(0xF28F9CAB))),
    SectionText = Color.White,
    SectionTop = Color(0xFFC0C8D0),
    SectionBottom = Color(0xFF8A96A3),
    Tabs = gloss(0xFF2B2B2B, 0xFF141414, 0xFF000000, 0xFF000000),
    TabIcon = gloss(0xFF8FD0FF, 0xFF3AA3F2, 0xFF0B6FD9, 0xFF2A8BEA),
    TabOn = Color.White,
    TabOff = Color(0xFF9A9A9A),
    Controls = gloss(0xFF262626, 0xFF121212, 0xFF000000, 0xFF000000),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

val C: Palette
    @Composable @ReadOnlyComposable get() = LocalPalette.current

/** Screens that are black on the original in both looks (Now Playing, Cover Flow, alerts). */
@Composable
fun AlwaysDark(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalPalette provides DarkPalette, content = content)

/* ---------- icons (same shapes as the desktop version) ---------- */

private fun icon(w: Float, h: Float, vararg paths: Pair<String, Float>): ImageVector {
    val b = ImageVector.Builder(defaultWidth = w.dp, defaultHeight = h.dp, viewportWidth = w, viewportHeight = h)
    for ((d, alpha) in paths) b.addPath(PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.White), fillAlpha = alpha)
    return b.build()
}

private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r},$cy a$r,$r 0 1,0 ${2 * r},0 a$r,$r 0 1,0 ${-2 * r},0z"

object Icons {
    val Folders = icon(28f, 26f, "M3 5h8l2 2.5h12v15H3z" to 1f)
    val Artists = icon(28f, 26f, circle(14f, 8f, 5.5f) to 1f, "M3 25c0-6.5 5-10 11-10s11 3.5 11 10z" to 1f)
    val Albums = icon(
        28f, 26f, "M3 2h19v19H3z" to .55f,
        "M6 5h19v19H6zm9.5 4.5a5 5 0 1 0 0 10 5 5 0 0 0 0-10zm0 3.5a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3z" to 1f,
    )
    val Songs = icon(28f, 26f, "M11 3l13-2v17.5a3.5 3 0 1 1-2.5-2.9V6.3L13.5 7.5v13a3.5 3 0 1 1-2.5-2.9z" to 1f)
    val More = icon(28f, 26f, circle(5f, 13f, 3f) to 1f, circle(14f, 13f, 3f) to 1f, circle(23f, 13f, 3f) to 1f)
    val Genre = icon(24f, 24f, "M8 3h7l-3 9h4l-6 9 1.5-7H8z" to 1f)
    val ShuffleRow = icon(24f, 24f, "M3 7h4l9 10h3v-3l4 4-4 4v-3h-4L6 9H3zm13-2h3V2l4 4-4 4V7h-3l-2.5 2.8-1.4-1.6zM3 15h3l2.5-2.8 1.4 1.6L7 17H3z" to 1f)
    val Refresh = icon(24f, 24f, "M12 4V1L8 5l4 4V6a6 6 0 1 1-6 6H4a8 8 0 1 0 8-8z" to 1f)
    val Play = icon(24f, 24f, "M6 3l15 9-15 9z" to 1f)
    val Pause = icon(24f, 24f, "M5 3h5v18H5zm9 0h5v18h-5z" to 1f)
    val Prev = icon(30f, 24f, "M3 4h3v16H3zm3 8l11-8v16zm10 0l11-8v16z" to 1f)
    val Next = icon(30f, 24f, "M24 4h3v16h-3zm0 8L13 4v16zm-10 0L3 4v16z" to 1f)
    val Repeat = icon(26f, 22f, "M5 9a4 4 0 0 1 4-4h10V2l5 4.5L19 11V8H9a1 1 0 0 0-1 1v2H5zm16 4a4 4 0 0 1-4 4H7v3l-5-4.5L7 11v3h10a1 1 0 0 0 1-1v-2h3z" to 1f)
    val Shuffle = icon(26f, 22f, "M2 5h5l10 11h3v-3l5 4.5-5 4.5v-3h-4.5L5 8H2zm15 0h3V2l5 4.5L20 11V8h-2l-3 3.4-2-2.2zM2 14h3l2.8-3.1 2 2.2L6.5 17H2z" to 1f)
    val List = icon(20f, 16f, "M0 1h3v2H0zm5 0h15v2H5zM0 7h3v2H0zm5 0h15v2H5zM0 13h3v2H0zm5 0h15v2H5z" to 1f)
    val Sun = icon(
        24f, 24f, circle(12f, 12f, 4.5f) to 1f,
        "M11 1h2v4h-2zm0 18h2v4h-2zM1 11h4v2H1zm18 0h4v2h-4zM4.2 5.6l1.4-1.4 2.8 2.8-1.4 1.4zm11.4 11.4 1.4-1.4 2.8 2.8-1.4 1.4zM4.2 18.4l2.8-2.8 1.4 1.4-2.8 2.8zM15.6 7l2.8-2.8 1.4 1.4L17 8.4z" to 1f,
    )
    val Moon = icon(24f, 24f, "M14.5 2.5A9.5 9.5 0 1 0 21.5 16 7.5 7.5 0 0 1 14.5 2.5z" to 1f)
    val VolLow = icon(24f, 24f, "M3 9v6h4l5 5V4L7 9z" to 1f)
    val VolHigh = icon(24f, 24f, "M3 9v6h4l5 5V4L7 9zm13.5 3A4.5 4.5 0 0 0 14 8v8a4.5 4.5 0 0 0 2.5-4zM14 3.2v2.1a7 7 0 0 1 0 13.4v2.1a9 9 0 0 0 0-17.6z" to 1f)
}

@Composable
fun Ico(v: ImageVector, size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Image(v, null, modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

/* ---------- navigation bar ---------- */

/** Classic arrow-shaped bar button: pointing left for "back", right for "now playing". */
fun arrowShape(pointLeft: Boolean): Shape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    val a = h * 0.42f
    val r = h * 0.18f
    if (pointLeft) {
        moveTo(0f, h / 2); lineTo(a, 0f); lineTo(w - r, 0f); quadraticTo(w, 0f, w, r)
        lineTo(w, h - r); quadraticTo(w, h, w - r, h); lineTo(a, h); close()
    } else {
        moveTo(w, h / 2); lineTo(w - a, h); lineTo(r, h); quadraticTo(0f, h, 0f, h - r)
        lineTo(0f, r); quadraticTo(0f, 0f, r, 0f); lineTo(w - a, 0f); close()
    }
}

val RoundedButton: Shape = RoundedCornerShape(5.dp)

@Composable
fun GlossButton(
    text: String,
    onClick: () -> Unit,
    shape: Shape = RoundedButton,
    padStart: Dp = 10.dp,
    padEnd: Dp = 10.dp,
    fontSize: Int = 12,
    content: (@Composable BoxScope.() -> Unit)? = null,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Box(
        Modifier
            .height(30.dp)
            .widthIn(min = 38.dp, max = 120.dp)
            .background(if (pressed) C.ButtonPressed else C.Button, shape)
            .border(1.dp, C.ButtonBorder, shape)
            .clickable(src, null, onClick = onClick)
            .padding(start = padStart, end = padEnd),
        contentAlignment = Alignment.Center,
    ) {
        if (content != null) content() else Text(
            text, color = C.ButtonText, fontSize = fontSize.sp, fontWeight = FontWeight.Bold,
            maxLines = 2, lineHeight = (fontSize + 1).sp, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun BackButton(label: String, onClick: () -> Unit) =
    GlossButton(label, onClick, arrowShape(true), padStart = 15.dp, padEnd = 9.dp)

@Composable
fun NowPlayingButton(onClick: () -> Unit) =
    GlossButton("Сейчас\nиграет", onClick, arrowShape(false), padStart = 8.dp, padEnd = 15.dp, fontSize = 10)

@Composable
fun NavBar(
    title: String,
    brush: Brush = C.Nav,
    left: (@Composable () -> Unit)? = null,
    right: (@Composable () -> Unit)? = null,
    center: (@Composable () -> Unit)? = null,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(brush)
            .drawBehind {
                drawLine(Color.White.copy(alpha = .12f), Offset(0f, 0.5f), Offset(size.width, 0.5f), 1f)
                drawLine(Color.Black, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f)
            },
    ) {
        Box(Modifier.align(Alignment.Center).fillMaxWidth(0.56f), contentAlignment = Alignment.Center) {
            if (center != null) center() else Text(
                title, color = C.NavTitle, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = Shadow(C.NavShadow, Offset(0f, -2f), 0f)),
            )
        }
        if (left != null) Box(Modifier.align(Alignment.CenterStart).padding(start = 7.dp)) { left() }
        if (right != null) Box(Modifier.align(Alignment.CenterEnd).padding(end = 7.dp)) { right() }
    }
}

/* ---------- list rows ---------- */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    chevron: Boolean = false,
    minHeight: Dp = 44.dp,
    onLongClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sep = C.Sep
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .background(if (pressed) C.RowPress else C.Row)
            .combinedClickable(src, null, onLongClick = onLongClick, onClick = onClick)
            .drawBehind { drawLine(sep, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f) }
            .padding(start = 10.dp, end = if (chevron) 8.dp else 30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { content() }
        if (chevron) Chevron()
    }
}

@Composable
fun Chevron() {
    val dim = C.Dim
    Box(
        Modifier
            .padding(start = 8.dp, end = 6.dp)
            .size(12.dp)
            .drawBehind {
                val s = size.minDimension
                val stroke = 2.5.dp.toPx()
                drawLine(dim, Offset(s * .3f, s * .1f), Offset(s * .7f, s * .5f), stroke)
                drawLine(dim, Offset(s * .7f, s * .5f), Offset(s * .3f, s * .9f), stroke)
            },
    )
}

@Composable
fun TwoLines(t1: String, t2: String?, modifier: Modifier = Modifier, playing: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(t1, color = C.Fg, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!t2.isNullOrEmpty()) Text(t2, color = C.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (playing) PlayingBadge(Modifier.padding(start = 8.dp))
    }
}

/** Glossy round "now playing" badge at the end of a song row. */
@Composable
fun PlayingBadge(modifier: Modifier = Modifier) {
    Box(
        modifier.size(20.dp).background(C.Button, CircleShape).border(1.dp, Color.Black, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Ico(Icons.Play, 10.dp, Color(0xFF111111), Modifier.padding(start = 1.dp)) }
}

@Composable
fun SideText(text: String) = Text(text, color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))

@Composable
fun SectionHeader(letter: String) {
    val top = C.SectionTop
    val bottom = C.SectionBottom
    Box(
        Modifier
            .fillMaxWidth()
            .height(23.dp)
            .background(C.Section)
            .drawBehind {
                drawLine(top, Offset(0f, 0.5f), Offset(size.width, 0.5f), 1f)
                drawLine(bottom, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f)
            }
            .padding(start = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(letter, color = C.SectionText, fontSize = 16.sp, fontWeight = FontWeight.Bold, style = TextStyle(shadow = Shadow(C.NavShadow.copy(alpha = .5f), Offset(0f, 1.5f), 0f)))
    }
}

/** A–Я index on the right edge; tap or drag to jump. */
@Composable
fun AlphabetIndex(letters: List<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    var height by remember { mutableStateOf(1) }
    var active by remember { mutableStateOf(false) }
    fun pick(y: Float) {
        if (letters.isEmpty()) return
        val i = ((y / height) * letters.size).toInt().coerceIn(0, letters.lastIndex)
        onPick(letters[i])
    }
    Column(
        modifier
            .fillMaxHeight()
            .width(22.dp)
            .background(if (active) Color.White.copy(alpha = .08f) else Color.Transparent, RoundedCornerShape(11.dp))
            .onSizeChanged { height = it.height.coerceAtLeast(1) }
            .pointerInput(letters) { detectTapGestures(onPress = { active = true; pick(it.y); tryAwaitRelease(); active = false }) }
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragStart = { active = true; pick(it.y) },
                    onDragEnd = { active = false },
                    onDragCancel = { active = false },
                ) { change, _ -> pick(change.position.y) }
            }
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (l in letters) Text(l, color = C.Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = 10.sp)
    }
}

/* ---------- tab bar ---------- */

@Composable
fun TabBar(tabs: List<Pair<String, ImageVector>>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(C.Tabs)
            .drawBehind { drawLine(Color.White.copy(alpha = .1f), Offset(0f, 0.5f), Offset(size.width, 0.5f), 1f) }
            .padding(horizontal = 2.dp, vertical = 3.dp),
    ) {
        tabs.forEachIndexed { i, (label, ic) ->
            val on = i == selected
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 1.dp)
                    .background(if (on) Color.White.copy(alpha = .13f) else Color.Transparent, RoundedCornerShape(3.dp))
                    .clickable { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (on) {
                    // Glossy white icon, like the selected tab on the original.
                    Image(
                        ic, null, Modifier.size(28.dp, 26.dp).glossIcon(C.TabIcon),
                        colorFilter = ColorFilter.tint(Color.White),
                    )
                } else {
                    Ico(ic, 26.dp, C.TabOff, Modifier.size(28.dp, 26.dp))
                }
                Spacer(Modifier.height(1.dp))
                Text(label, color = if (on) C.TabOn else C.TabOff, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

private fun Modifier.glossIcon(brush: Brush): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(brush, blendMode = BlendMode.SrcIn)
    }

@Composable
fun EmptyText(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) {
        Text(text, color = C.Muted, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 40.dp))
    }
}

/** Dark glass alert with white buttons, styled after the classic confirmation dialogs. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) = AlwaysDark {
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .6f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 28.dp)
                .background(
                    Brush.verticalGradient(listOf(Color(0xF22E2E2E), Color(0xF2111111))),
                    RoundedCornerShape(10.dp),
                )
                .border(1.5.dp, Color(0xFF555555), RoundedCornerShape(10.dp))
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, color = C.Fg, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                message, color = C.Fg2, fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { WideButton("Отмена", onDismiss) }
                Box(Modifier.weight(1f)) { WideButton(confirm) { onConfirm(); onDismiss() } }
            }
        }
    }
}

@Composable
private fun WideButton(text: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Box(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(if (pressed) C.ButtonPressed else C.Button, RoundedCornerShape(7.dp))
            .border(1.dp, Color.Black, RoundedCornerShape(7.dp))
            .clickable(src, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
}
