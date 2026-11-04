package io.github.tima22814.retromusic.ui

import android.content.Context
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import io.github.tima22814.retromusic.PlaybackState
import io.github.tima22814.retromusic.PlayerViewModel

@Composable
fun NowPlaying(vm: PlayerViewModel, state: PlaybackState, onBack: () -> Unit) = AlwaysDark {
    NowPlayingContent(vm, state, onBack)
}

@Composable
private fun NowPlayingContent(vm: PlayerViewModel, state: PlaybackState, onBack: () -> Unit) {
    val t = state.current ?: return
    var flipped by remember { mutableStateOf(false) }
    var showScrub by remember { mutableStateOf(false) }
    val angle by animateFloatAsState(if (flipped) 180f else 0f, tween(600), label = "flip")

    BackHandler { if (flipped) flipped = false else onBack() }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        NavBar(
            title = t.title,
            brush = Brush.verticalGradient(listOf(Color(0xE6282828), Color(0xE6000000))),
            left = { BackButton("Медиатека", onBack) },
            right = {
                // Shows the list icon on the cover side and the cover thumbnail on the list side.
                GlossButton("", { flipped = !flipped }, padStart = 0.dp, padEnd = 0.dp) {
                    if (flipped) Art(t, Modifier.size(36.dp, 28.dp)) else Ico(Icons.List, 18.dp, Color(0xFF111111))
                }
            },
            center = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(t.artist, color = C.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.title, color = C.Fg, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.album, color = C.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (angle <= 90f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = -angle; cameraDistance = 14 * density * (size.width / (400 * density)).coerceAtLeast(1f) }
                        .clickable(remember { MutableInteractionSource() }, null) { showScrub = !showScrub },
                ) {
                    Art(t, Modifier.fillMaxSize(), ContentScale.Fit, noteSize = 120)
                    if (showScrub) Scrubber(vm, state)
                }
            } else {
                Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f - angle; cameraDistance = 14 * density * (size.width / (400 * density)).coerceAtLeast(1f) }) {
                    QueueList(vm, state)
                }
            }
        }

        Controls(vm, state)
    }
}

@Composable
private fun Scrubber(vm: PlayerViewModel, state: PlaybackState) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val dur = state.durationMs.coerceAtLeast(1)
    val frac = dragging ?: (state.positionMs.toFloat() / dur).coerceIn(0f, 1f)
    val pos = (frac * dur).toLong()
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = .72f))
            .clickable(remember { MutableInteractionSource() }, null) {}
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(fmt(pos), color = C.Fg2, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(42.dp), textAlign = TextAlign.Center)
            GlossSlider(frac, { dragging = it }, { vm.seekTo((it * dur).toLong()); dragging = null }, Modifier.weight(1f))
            Text("-" + fmt(dur - pos), color = C.Fg2, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ModeButton(Icons.Repeat, state.repeat != Player.REPEAT_MODE_OFF, badge = if (state.repeat == Player.REPEAT_MODE_ONE) "1" else null) { vm.cycleRepeat() }
            Text(
                "${state.queuePos + 1} из ${state.queue.size}", color = C.Muted, fontSize = 11.sp,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
            )
            ModeButton(Icons.Shuffle, state.shuffle) { vm.setShuffle(!state.shuffle) }
        }
    }
}

@Composable
private fun ModeButton(icon: ImageVector, on: Boolean, badge: String? = null, onClick: () -> Unit) {
    PressGlow(Modifier.size(40.dp, 30.dp), onClick) { lit ->
        val tint = lerp(if (on) C.Fg else C.Dim, Color(0xFF5C5C5C), lit)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Ico(icon, 26.dp, tint)
            if (badge != null) Text(badge, color = tint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun QueueList(vm: PlayerViewModel, state: PlaybackState) {
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { if (state.queuePos > 2) listState.scrollToItem(state.queuePos - 2) }
    Column(Modifier.fillMaxSize().background(C.Row)) {
        Box(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF161616), C.Row))).padding(10.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Очередь · ${songsWord(state.queue.size)}", color = C.Muted, fontSize = 13.sp) }
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            itemsIndexed(state.queue) { i, q ->
                ListRow({ if (i == state.queuePos) vm.toggle() else vm.playQueueAt(i) }) {
                    Text((i + 1).toString(), color = C.Muted, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp))
                    TwoLines(q.title, q.artist, Modifier.weight(1f), playing = i == state.queuePos)
                    SideText(fmt(q.durationMs))
                }
            }
        }
    }
}

@Composable
private fun Controls(vm: PlayerViewModel, state: PlaybackState) {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableIntStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    LaunchedEffect(state.isPlaying) { vol = am.getStreamVolume(AudioManager.STREAM_MUSIC) }

    Column(
        Modifier
            .fillMaxWidth()
            .background(C.Controls)
            .drawBehind { drawLine(Color.White.copy(alpha = .1f), Offset(0f, 0.5f), Offset(size.width, 0.5f), 1f) }
            .padding(bottom = 18.dp),
    ) {
        // The bar is split into three keys (play a little narrower); each lights up across its whole part.
        Row(Modifier.fillMaxWidth().height(76.dp).padding(2.dp)) {
            CtlButton(Glyphs.Prev, 36, Modifier.weight(1f)) { vm.prev() }
            CtlButton(if (state.isPlaying) Glyphs.Pause else Glyphs.Play, 40, Modifier.weight(0.8f)) { vm.toggle() }
            CtlButton(Glyphs.Next, 36, Modifier.weight(1f)) { vm.next() }
        }
        Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico(Icons.VolLow, 16.dp, C.Muted)
            GlossSlider(
                vol.toFloat() / max,
                { vol = (it * max).toInt(); am.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0) },
                {},
                Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Ico(Icons.VolHigh, 16.dp, C.Muted)
        }
    }
}

@Composable
private fun CtlButton(icon: Glyph, size: Int, modifier: Modifier, onClick: () -> Unit) {
    PressGlow(modifier.fillMaxHeight().padding(horizontal = 1.dp), onClick) { lit ->
        GlyphIco(icon, size.dp, lerp(C.Fg, Color(0xFF5C5C5C), lit))
    }
}

/** Turns into a flat matte light key while pressed, then fades back into the dark bar. */
@Composable
private fun PressGlow(modifier: Modifier, onClick: () -> Unit, content: @Composable (lit: Float) -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    // Quick to light, slower to fade, so even a short tap is visible.
    val lit by animateFloatAsState(if (pressed) 1f else 0f, tween(if (pressed) 60 else 260), label = "press")
    Box(
        modifier
            .drawBehind { if (lit > 0f) drawRect(Color(0xFFD2D2D2), alpha = lit) }
            .clickable(src, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content(lit) }
}

/** White-filled track with a round glossy knob, like the original scrubber and volume bars. */
@Composable
fun GlossSlider(value: Float, onChange: (Float) -> Unit, onDone: (Float) -> Unit, modifier: Modifier = Modifier) {
    var width by remember { mutableFloatStateOf(1f) }
    var last by remember { mutableFloatStateOf(value) }
    val knob = with(LocalDensity.current) { 22.dp.toPx() }
    fun at(x: Float) = ((x - knob / 2) / (width - knob)).coerceIn(0f, 1f)
    Box(
        modifier
            .height(26.dp)
            .pointerInput(Unit) {
                width = size.width.toFloat()
                detectTapGestures { o -> at(o.x).also { last = it; onChange(it); onDone(it) } }
            }
            .pointerInput(Unit) {
                width = size.width.toFloat()
                detectHorizontalDragGestures(
                    onDragEnd = { onDone(last) },
                    onDragCancel = { onDone(last) },
                ) { change, _ -> at(change.position.x).also { last = it; onChange(it) } }
            }
            .drawBehind {
                width = size.width
                val h = 9.dp.toPx()
                val top = (size.height - h) / 2
                val x = knob / 2 + value.coerceIn(0f, 1f) * (size.width - knob)
                val r = CornerRadius(h / 2)
                drawRoundRect(Color(0xFF2A2A2A), Offset(0f, top), Size(size.width, h), r)
                drawRoundRect(Color(0xFFF2F2F2), Offset(0f, top), Size(x, h), r)
                drawRoundRect(Color.Black, Offset(0f, top), Size(size.width, h), r, style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
                drawCircle(Color(0xFF333333), knob / 2, Offset(x, size.height / 2))
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.White, Color(0xFFD4D4D4), Color(0xFF9A9A9A)),
                        center = Offset(x, size.height / 2 - knob * .2f), radius = knob * .6f,
                    ),
                    knob / 2 - 1, Offset(x, size.height / 2),
                )
            },
    )
}
