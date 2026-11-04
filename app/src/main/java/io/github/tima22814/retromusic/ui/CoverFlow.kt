package io.github.tima22814.retromusic.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.tima22814.retromusic.Library
import io.github.tima22814.retromusic.PlaybackState
import io.github.tima22814.retromusic.PlayerViewModel
import io.github.tima22814.retromusic.Track
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

private const val RING_STEP = 24f

/**
 * Album carousel shown when the phone is turned sideways (or opened from the Albums tab).
 * Swipe to browse, tap a side cover to bring it forward, tap the centre one (or "i") to see its songs.
 */
@Composable
fun CoverFlow(
    vm: PlayerViewModel,
    tracks: List<Track>,
    state: PlaybackState,
    onClose: (() -> Unit)?,
) = AlwaysDark { CoverFlowContent(vm, tracks, state, onClose) }

@Composable
private fun CoverFlowContent(
    vm: PlayerViewModel,
    tracks: List<Track>,
    state: PlaybackState,
    onClose: (() -> Unit)?,
) {
    val albums = remember(tracks) { Library.albums(tracks) }
    if (albums.isEmpty()) return EmptyText("Альбомов пока нет.")
    val n = albums.size
    // pos runs unbounded; albums repeat around it in a loop.
    val start = remember(albums) { albums.indexOfFirst { it.name == state.current?.album }.coerceAtLeast(0) }
    val pos = remember(albums) { Animatable(start.toFloat()) }
    val scope = rememberCoroutineScope()
    var flipped by remember(albums) { mutableIntStateOf(-1) }
    fun albumAt(i: Int) = Math.floorMod(i, n)
    val centerSlot = pos.value.roundToInt()
    val center = albumAt(centerSlot)
    // The album shown in the big panel; kept while the panel flips back closed.
    var panelAlbum by remember(albums) { mutableIntStateOf(-1) }
    if (flipped >= 0) panelAlbum = flipped
    val panelFlip by animateFloatAsState(if (flipped >= 0) 180f else 0f, tween(600), label = "panel")

    // Set when a touch caught the carousel mid-spin; its speed then feeds a follow-up flick.
    var caught by remember { mutableStateOf(false) }
    var carried by remember { mutableFloatStateOf(0f) }

    fun goTo(slot: Int) {
        flipped = -1
        scope.launch { pos.animateTo(slot.toFloat(), spring(stiffness = 300f)) }
    }

    /** Lets a swipe keep spinning; flicking again while it still spins adds to the speed. */
    fun fling(fingerVelocity: Float, swipeShare: Float, carried: Float) {
        // A slow release just settles on the nearest cover, so picking one doesn't drift off.
        if (abs(fingerVelocity) < 2.5f) return goTo(pos.value.roundToInt())
        // Speed and length together: a short quick flick nudges, a long quick one throws far.
        val length = (swipeShare * 2f).coerceIn(0.2f, 1.6f)
        var v = fingerVelocity * (0.8f + abs(fingerVelocity) / 8f) * length
        if (carried != 0f && sign(carried) == sign(v)) v += carried
        v = v.coerceIn(-160f, 160f)
        val decay = exponentialDecay<Float>(frictionMultiplier = 0.38f)
        val target = decay.calculateTargetValue(pos.value, v).roundToInt().toFloat()
        scope.launch {
            // Aim the decay at a whole cover so it settles without a visible correction.
            pos.animateDecay((target - pos.value) * 4.2f * 0.38f, decay)
            pos.animateTo(target, spring(stiffness = 300f))
        }
    }

    BackHandler(enabled = flipped >= 0 || onClose != null) { if (flipped >= 0) flipped = -1 else onClose?.invoke() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF151515), Color.Black))),
    ) {
        val density = LocalDensity.current
        val portrait = maxHeight > maxWidth
        val cover = if (portrait) minOf(maxWidth * 0.46f, maxHeight * 0.3f) else minOf((maxHeight - 80.dp) * 0.56f, maxWidth * 0.3f)
        val stageH = if (portrait) cover * 1.5f + 64.dp else maxHeight - 80.dp
        val coverPx = with(density) { cover.toPx() }
        val widthPx = with(density) { maxWidth.toPx() }
        if (portrait && flipped >= 0) flipped = -1

        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(stageH)
                    .padding(top = if (portrait) 52.dp else 0.dp)
                    .clipToBounds()
                    .pointerInput(albums) {
                        // Touching a spinning carousel stops it on the spot, like catching a wheel.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            caught = pos.isRunning
                            carried = pos.velocity
                            if (caught) scope.launch { pos.stop() }
                            do {
                                val e = awaitPointerEvent(PointerEventPass.Initial)
                            } while (e.changes.any { it.pressed })
                            if (!pos.isRunning) goTo(pos.value.roundToInt())
                        }
                    }
                    .pointerInput(albums) {
                        val tracker = VelocityTracker()
                        var travelled = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { tracker.resetTracking(); travelled = 0f },
                            // Swipe length is measured as a share of the screen width.
                            onDragEnd = { fling(-tracker.calculateVelocity().x / (coverPx * 0.6f), abs(travelled) / size.width, carried) },
                            onDragCancel = { goTo(pos.value.roundToInt()) },
                        ) { change, dx ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            travelled += dx
                            flipped = -1
                            scope.launch { pos.snapTo(pos.value - dx / (coverPx * 0.6f)) }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                // Each album appears once, even in a short loop.
                val reach = minOf(5, (n - 1) / 2)
                // Sideways the ring spans most of the screen instead of bunching up in the middle.
                val ringR = if (portrait) coverPx * 1.3f else maxOf(coverPx * 1.3f, widthPx * 0.34f)
                for (i in (centerSlot - reach..centerSlot + reach).filter { abs((it - pos.value) * RING_STEP) <= 100f }) {
                    key(i) {
                        val idx = albumAt(i)
                        val a = albums[idx]
                        val d = i - pos.value
                        val ad = abs(d)
                        // Covers stand around a ring: each slot turns it further round and away.
                        val theta = d * RING_STEP
                        val rad = Math.toRadians(theta.toDouble())
                        val x = ringR * kotlin.math.sin(rad).toFloat()
                        val depth = 1f - kotlin.math.cos(rad).toFloat()
                        val shade = (depth * 0.75f).coerceIn(0f, 0.8f)
                        val flip = if (idx == panelAlbum && i == centerSlot) panelFlip else 0f

                        Box(
                            Modifier
                                .zIndex(100f - depth * 50f)
                                .size(cover, cover * 1.5f)
                                .graphicsLayer {
                                    translationX = x
                                    // Sideways, centre the cover itself (not cover + reflection) between the top and the caption.
                                    if (!portrait) translationY = coverPx * 0.22f
                                    rotationY = -theta * 1.15f - flip
                                    val sc = 1f / (1f + depth * 0.55f)
                                    scaleX = sc
                                    scaleY = sc
                                    // Depth grows with the cover, or big windows get a fish-eye look.
                                    cameraDistance = 9 * this.density * (size.width / (300 * this.density)).coerceAtLeast(1f)
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f / 3f)
                                }
                                .clickable(remember { MutableInteractionSource() }, null) {
                                    if (caught) {
                                        // This tap only stopped the spin; it shouldn't also jump to a cover.
                                    } else if (i == centerSlot && ad < 0.1f) {
                                        if (!portrait) flipped = if (flipped == idx) -1 else idx
                                    } else goTo(i)
                                },
                        ) {
                            // Past 90° the cover has turned edge-on and the big panel takes over.
                            if (flip <= 90f) {
                                Column(Modifier.drawWithContent {
                                    drawContent()
                                    if (shade > 0f) drawRect(Color.Black.copy(alpha = shade))
                                }) {
                                    Art(a.songs.first(), Modifier.size(cover), noteSize = 60)
                                    // Mirror image of the cover's bottom half, fading into the floor.
                                    Box(
                                        Modifier
                                            .padding(top = 2.dp)
                                            .size(cover, cover * 0.5f - 2.dp)
                                            .clipToBounds()
                                            .drawWithContent {
                                                drawContent()
                                                drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = .62f), 0.6f to Color.Black.copy(alpha = .94f), 1f to Color.Black))
                                            },
                                    ) {
                                        Art(
                                            a.songs.first(),
                                            Modifier
                                                .wrapContentHeight(Alignment.Top, unbounded = true)
                                                .size(cover)
                                                .graphicsLayer { scaleY = -1f },
                                            noteSize = 60,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (portrait) {
                // Upright: the centred album's songs sit under the covers and scroll on their own.
                val a = albums[center]
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    RoundButton(Modifier.align(Alignment.CenterStart), { vm.playList(a.songs, 0) }) {
                        Ico(Icons.Play, 12.dp, C.Fg, Modifier.padding(start = 2.dp))
                    }
                    Column(Modifier.align(Alignment.Center).fillMaxWidth(0.72f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(a.name, color = C.Fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        Text(a.artist, color = C.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val minutes = a.songs.sumOf { it.durationMs } / 60000
                        Text("${songsWord(a.songs.size)}, $minutes мин.", color = C.Dim, fontSize = 11.sp, maxLines = 1)
                    }
                    RoundButton(Modifier.align(Alignment.CenterEnd), { vm.playList(a.songs, a.songs.indices.random(), shuffle = true) }) {
                        Ico(Icons.Shuffle, 16.dp, C.Fg)
                    }
                }
                val shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .background(Brush.verticalGradient(listOf(Color(0xF52C2C2C), Color(0xF50E0E0E))), shape)
                        .border(1.dp, Color(0xFF3A3A3A), shape)
                        .clip(shape)
                        .padding(horizontal = 10.dp),
                ) {
                    key(a.name, a.artist) { AlbumSongs(vm, a, state.current, Modifier.fillMaxSize()) }
                }
            }
        }

        if (!portrait) {
            val a = albums[center]
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 64.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(a.name, color = C.Fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Text(a.artist, color = C.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            RoundButton(Modifier.align(Alignment.BottomStart).offset(14.dp, (-18).dp), { vm.playList(a.songs, 0) }) {
                Ico(Icons.Play, 12.dp, C.Fg, Modifier.padding(start = 2.dp))
            }
            RoundButton(Modifier.align(Alignment.BottomEnd).offset((-14).dp, (-18).dp), { flipped = if (flipped == center) -1 else center }) {
                Text("i", color = C.Fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
            }
        }
        if (panelFlip > 90f && panelAlbum >= 0) {
            val panelW = maxWidth * 0.76f
            val panelH = maxHeight - 16.dp
            val p = ((panelFlip - 90f) / 90f).coerceIn(0f, 1f)
            Box(
                Modifier
                    .align(Alignment.Center)
                    .zIndex(200f)
                    .size(panelW, panelH)
                    .graphicsLayer {
                        // Continue the cover's turn and grow from cover size to the full panel.
                        rotationY = 180f - panelFlip
                        cameraDistance = 12 * this.density * (size.width / (700 * this.density)).coerceAtLeast(1f)
                        scaleX = cover / panelW + (1f - cover / panelW) * p
                        scaleY = cover / panelH + (1f - cover / panelH) * p
                    },
            ) {
                AlbumPanel(vm, albums[panelAlbum], state.current) { flipped = -1 }
            }
        }
        if (onClose != null && flipped < 0) {
            Box(Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 10.dp).zIndex(300f)) {
                BackButton("Медиатека", onClose)
            }
        }
    }
}

@Composable
private fun RoundButton(modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier
            .size(30.dp)
            .background(Brush.radialGradient(listOf(Color(0xFF3A3A3A), Color(0xFF111111))), CircleShape)
            .border(1.dp, Color(0xFF444444), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Back of a flipped cover: the album's songs on a dark glass panel. */
@Composable
private fun AlbumPanel(vm: PlayerViewModel, album: io.github.tima22814.retromusic.Album, current: Track?, onDone: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xF52C2C2C), Color(0xF50E0E0E))), shape)
            .border(1.5.dp, Color(0xFF5A5A5A), shape)
            .clip(shape)
            .clickable(remember { MutableInteractionSource() }, null) {},
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x08FFFFFF))))
                .drawBehind { drawLine(Color.Black, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f) },
        ) {
            Box(Modifier.align(Alignment.CenterStart).padding(start = 10.dp)) { GlossButton("Готово", onDone) }
            Column(Modifier.align(Alignment.Center).fillMaxWidth(0.6f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(album.name, color = C.Fg, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(album.artist, color = C.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val minutes = album.songs.sumOf { it.durationMs } / 60000
                Text("${songsWord(album.songs.size)}, $minutes мин.", color = C.Dim, fontSize = 11.sp, maxLines = 1)
            }
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp)) {
                GlossButton("", { vm.playList(album.songs, album.songs.indices.random(), shuffle = true) }, padStart = 0.dp, padEnd = 0.dp) {
                    Ico(Icons.Shuffle, 22.dp, Color(0xFF111111))
                }
            }
        }
        Row(Modifier.weight(1f).padding(12.dp)) {
            Art(
                album.songs.first(),
                Modifier.fillMaxHeight().aspectRatio(1f).border(1.dp, Color(0xFF333333)),
                noteSize = 60,
            )
            AlbumSongs(vm, album, current, Modifier.weight(1f).padding(start = 12.dp))
        }
    }
}

@Composable
private fun AlbumSongs(vm: PlayerViewModel, album: io.github.tima22814.retromusic.Album, current: Track?, modifier: Modifier) {
    LazyColumn(modifier) {
        itemsIndexed(album.songs, key = { _, t -> t.id }) { i, t ->
            val playing = current?.id == t.id
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clickable { if (playing) vm.toggle() else vm.playList(album.songs, i) }
                    .drawBehind { drawLine(Color(0xFF2A2A2A), Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f) }
                    .padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    (if (t.track > 0) t.track else i + 1).toString(), color = C.Muted, fontSize = 14.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp),
                )
                Text(
                    t.title, color = C.Fg, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (playing) {
                    PlayingBadge(Modifier.padding(horizontal = 8.dp))
                }
                Text(fmt(t.durationMs), color = C.Fg2, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
