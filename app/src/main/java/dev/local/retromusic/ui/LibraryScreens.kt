package dev.local.retromusic.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.local.retromusic.Album
import dev.local.retromusic.ArtLoader
import dev.local.retromusic.Library
import dev.local.retromusic.PlaybackState
import dev.local.retromusic.PlayerViewModel
import dev.local.retromusic.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/* ---------- helpers ---------- */

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

fun songsWord(n: Int): String {
    val m10 = n % 10
    val m100 = n % 100
    val w = when {
        m10 == 1 && m100 != 11 -> "песня"
        m10 in 2..4 && (m100 < 12 || m100 > 14) -> "песни"
        else -> "песен"
    }
    return "$n $w"
}

private fun sectionKey(s: String): String {
    val c = s.trim().firstOrNull()?.uppercaseChar() ?: return "#"
    return if (c.isLetter()) c.toString() else "#"
}

/** Album cover, loaded off the main thread. Shows a grey ♪ tile while missing. */
@Composable
fun Art(track: Track, modifier: Modifier, contentScale: ContentScale = ContentScale.Crop, noteSize: Int = 22) {
    val ctx = LocalContext.current
    // State is keyed by the track so a recycled slot never keeps the previous cover.
    val bmp = remember(track.id) { mutableStateOf(ArtLoader.cached(track)) }
    LaunchedEffect(track.id) {
        if (bmp.value == null) bmp.value = withContext(Dispatchers.IO) { ArtLoader.load(ctx, track) }
    }
    val b = bmp.value
    if (b != null) {
        Image(b.asImageBitmap(), null, modifier, contentScale = contentScale)
    } else {
        Box(
            modifier.background(Brush.verticalGradient(listOf(Color(0xFF2A2A2A), Color(0xFF111111)))),
            contentAlignment = Alignment.Center,
        ) { Text("♪", color = Color(0xFF444444), fontSize = noteSize.sp) }
    }
}

/* ---------- screens ---------- */

sealed interface Screen {
    val title: String
}

data class SongsScreen(
    override val title: String,
    val songs: List<Track>,
    val index: Boolean = false,
    val shuffleRow: Boolean = false,
    val numbers: Boolean = false,
    val albumOrder: Boolean = false,
    val durations: Boolean = false,
    val album: Album? = null,
    val sub: (Track) -> String? = { listOf(it.artist, it.album).filter(String::isNotBlank).joinToString(" — ") },
) : Screen

data object FoldersScreen : Screen { override val title = "Папки" }
data object ArtistsScreen : Screen { override val title = "Исполнители" }
data class ArtistScreen(override val title: String, val songs: List<Track>) : Screen
data object AlbumsScreen : Screen { override val title = "Альбомы" }
data object GenresScreen : Screen { override val title = "Жанры" }
data object MoreScreen : Screen { override val title = "Ещё" }
data object HiddenScreen : Screen { override val title = "Скрытые" }

private data class Confirm(val title: String, val message: String, val button: String, val action: () -> Unit)

private val tabs = listOf(
    "Папки" to Icons.Folders,
    "Исполнители" to Icons.Artists,
    "Альбомы" to Icons.Albums,
    "Песни" to Icons.Songs,
    "Ещё" to Icons.More,
)

private fun rootFor(tab: Int, tracks: List<Track>): Screen = when (tab) {
    0 -> FoldersScreen
    1 -> ArtistsScreen
    2 -> AlbumsScreen
    3 -> SongsScreen("Песни", tracks, index = true, shuffleRow = true)
    else -> MoreScreen
}

fun albumScreen(a: Album): SongsScreen {
    val many = a.songs.map { it.artist }.distinct().size > 1
    return SongsScreen(
        a.name, a.songs, numbers = true, albumOrder = true, durations = true, album = a,
        sub = { if (many) it.artist else null },
    )
}

@Composable
fun LibraryUi(
    vm: PlayerViewModel,
    tracks: List<Track>,
    state: PlaybackState,
    onNowPlaying: () -> Unit,
    onCoverFlow: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(3) }
    val stacks = remember(tracks) { mutableStateMapOf<Int, List<Screen>>() }
    val stack = stacks[tab] ?: listOf(rootFor(tab, tracks))
    val top = stack.last()
    fun push(s: Screen) { stacks[tab] = stack + s }
    fun pop() { if (stack.size > 1) stacks[tab] = stack.dropLast(1) }

    BackHandler(enabled = stack.size > 1) { pop() }

    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val light = C.light
    val hiddenAlbums by vm.hiddenAlbums.collectAsStateWithLifecycle()
    val hiddenFolders by vm.hiddenFolders.collectAsStateWithLifecycle()
    val hideAlbum: (String) -> Unit = { name ->
        confirm = Confirm("Скрыть альбом?", "«$name» пропадёт из медиатеки. Файлы останутся на телефоне, вернуть можно в «Ещё → Скрытые».", "Скрыть") {
            vm.setAlbumHidden(name, true)
        }
    }
    val hideFolder: (String) -> Unit = { name ->
        confirm = Confirm("Скрыть папку?", "Все песни из папки «$name» пропадут из медиатеки. Файлы останутся на телефоне, вернуть можно в «Ещё → Скрытые».", "Скрыть") {
            vm.setFolderHidden(name, true)
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(C.Bg)) {
        NavBar(
            title = top.title,
            left = when {
                stack.size > 1 -> {
                    { val prev = stack[stack.size - 2].title; BackButton(if (prev.length > 11) "Назад" else prev) { pop() } }
                }
                top is AlbumsScreen -> {
                    { GlossButton("Cover Flow", onCoverFlow, fontSize = 11) }
                }
                else -> null
            },
            right = if (state.current != null) ({ NowPlayingButton(onNowPlaying) }) else null,
        )
        Box(Modifier.weight(1f)) {
            // Tapping the track that is already on pauses / resumes it instead of restarting.
            val play: (List<Track>, Int) -> Unit = { list, i ->
                if (state.current?.id == list[i].id) vm.toggle() else { vm.playList(list, i); onNowPlaying() }
            }
            val shuffle: (List<Track>) -> Unit = { list ->
                if (list.isNotEmpty()) { vm.playList(list, list.indices.random(), shuffle = true); onNowPlaying() }
            }
            when (top) {
                is SongsScreen -> SongsList(top, state.current, play, shuffle, hideAlbum)
                FoldersScreen -> GroupList(tracks, { it.folder }, Icons.Folders, hideFolder) { name, s ->
                    push(SongsScreen(name, s, shuffleRow = true, durations = true))
                }
                GenresScreen -> GroupList(tracks, { it.genreOrFolder() }, Icons.Genre, hideFolder) { name, s ->
                    push(SongsScreen(name, s, shuffleRow = true, durations = true))
                }
                ArtistsScreen -> ArtistsList(tracks) { name, s -> push(artistScreen(name, s)) }
                is ArtistScreen -> ArtistView(top, { push(it) }, hideAlbum)
                AlbumsScreen -> AlbumsList(tracks, hideAlbum) { push(albumScreen(it)) }
                MoreScreen -> MoreList(
                    count = tracks.size,
                    hiddenCount = hiddenAlbums.size + hiddenFolders.size,
                    onGenres = { push(GenresScreen) },
                    onShuffleAll = { shuffle(tracks) },
                    onRefresh = { vm.refresh() },
                    onHidden = { push(HiddenScreen) },
                    light = light,
                    onTheme = { vm.setLightTheme(!light) },
                )
                HiddenScreen -> HiddenList(
                    hiddenAlbums, hiddenFolders,
                    onRestoreAlbum = { name ->
                        confirm = Confirm("Вернуть альбом?", "«$name» снова появится в медиатеке.", "Вернуть") { vm.setAlbumHidden(name, false) }
                    },
                    onRestoreFolder = { name ->
                        confirm = Confirm("Вернуть папку?", "Песни из «$name» снова появятся в медиатеке.", "Вернуть") { vm.setFolderHidden(name, false) }
                    },
                )
            }
        }
        TabBar(tabs, tab) { i ->
            if (i == tab) stacks.remove(tab) else tab = i
        }
    }
    confirm?.let { c -> ConfirmDialog(c.title, c.message, c.button, c.action) { confirm = null } }
    }
}

// MediaStore has no reliable genre column across versions; folders double as genres.
private fun Track.genreOrFolder() = folder

/* ---------- lists ---------- */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun <T> SectionedList(
    items: List<T>,
    label: (T) -> String,
    withIndex: Boolean,
    header: (LazyListScope.() -> Unit)? = null,
    row: @Composable (T) -> Unit,
) {
    val groups = remember(items) {
        items.groupBy { sectionKey(label(it)) }.toList()
            .sortedWith { a, b ->
                when {
                    a.first == "#" && b.first != "#" -> 1
                    b.first == "#" && a.first != "#" -> -1
                    else -> Library.collator.compare(a.first, b.first)
                }
            }
    }
    val headerCount = if (header != null) 1 else 0
    val positions = remember(groups, headerCount) {
        var p = headerCount
        groups.associate { (k, list) -> k to p.also { p += list.size + 1 } }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            header?.invoke(this)
            for ((k, list) in groups) {
                stickyHeader(key = "h:$k") { SectionHeader(k) }
                items(list) { row(it) }
            }
        }
        if (withIndex && groups.size > 4) {
            var last by remember { mutableStateOf("") }
            AlphabetIndex(groups.map { it.first }, { k ->
                if (k != last) {
                    last = k
                    positions[k]?.let { scope.launch { listState.scrollToItem(it) } }
                }
            }, Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
private fun SongsList(
    s: SongsScreen,
    current: Track?,
    play: (List<Track>, Int) -> Unit,
    shuffle: (List<Track>) -> Unit,
    hideAlbum: (String) -> Unit,
) {
    val songs = remember(s) { s.songs.sortedWith(if (s.albumOrder) Library.albumOrder else Library.byTitle) }
    if (songs.isEmpty()) return EmptyText("Песен пока нет.\nЗакинь музыку на телефон и нажми «Ещё → Обновить медиатеку».")
    val indexOf = remember(songs) { songs.withIndex().associate { it.value.id to it.index } }

    val header: LazyListScope.() -> Unit = {
        s.album?.let { a -> item { AlbumHeader(a) { hideAlbum(a.name) } } }
        if (s.shuffleRow) item { ShuffleRow("Перемешать", null) { shuffle(songs) } }
    }
    val row: @Composable (Track) -> Unit = { t ->
        val i = indexOf.getValue(t.id)
        ListRow({ play(songs, i) }) {
            if (s.numbers) Text(
                (if (t.track > 0) t.track else i + 1).toString(), color = C.Muted, fontSize = 15.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp),
            )
            TwoLines(t.title, s.sub(t), Modifier.weight(1f), playing = current?.id == t.id)
            if (s.durations) SideText(fmt(t.durationMs))
        }
    }

    if (s.index) {
        SectionedList(songs, { it.title }, withIndex = true, header = header, row = row)
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            header()
            items(songs, key = { it.id }) { row(it) }
        }
    }
}

@Composable
private fun ShuffleRow(title: String, sub: String?, onClick: () -> Unit) {
    ListRow(onClick) {
        Box(Modifier.width(40.dp)) { Ico(Icons.ShuffleRow, 24.dp, C.Fg2) }
        TwoLines(title, sub, Modifier.weight(1f))
    }
}

@Composable
private fun AlbumHeader(a: Album, onHide: () -> Unit) {
    val sep = C.Sep
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(C.HeaderTop, C.Row)))
            .drawBehind { drawLine(sep, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Art(a.songs.first(), Modifier.size(110.dp), noteSize = 40)
        Column(Modifier.padding(start = 12.dp)) {
            Text(a.artist, color = C.Muted, fontSize = 13.sp, maxLines = 1)
            Text(a.name, color = C.Fg, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            val minutes = a.songs.sumOf { it.durationMs } / 60000
            Text("${songsWord(a.songs.size)}, $minutes мин.", color = C.Dim, fontSize = 12.sp)
            Box(Modifier.padding(top = 8.dp)) { GlossButton("Скрыть альбом", onHide, fontSize = 11) }
        }
    }
}

@Composable
private fun GroupList(
    tracks: List<Track>,
    key: (Track) -> String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onHide: (String) -> Unit,
    onOpen: (String, List<Track>) -> Unit,
) {
    val groups = remember(tracks) { tracks.groupBy(key).toList().sortedWith(compareBy(Library.collator) { it.first }) }
    if (groups.isEmpty()) return EmptyText("Медиатека пуста.")
    LazyColumn(Modifier.fillMaxSize()) {
        items(groups, key = { it.first }) { (name, songs) ->
            ListRow({ onOpen(name, songs) }, chevron = true, onLongClick = { onHide(name) }) {
                Box(Modifier.width(40.dp)) { Ico(icon, 26.dp, C.Fg2) }
                TwoLines(name, songsWord(songs.size), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ArtistsList(tracks: List<Track>, onOpen: (String, List<Track>) -> Unit) {
    val groups = remember(tracks) { tracks.groupBy { it.artist } }
    SectionedList(groups.keys.toList().sortedWith(Library.collator), { it }, withIndex = true) { name ->
        ListRow({ onOpen(name, groups.getValue(name)) }, chevron = true) {
            TwoLines(name, null, Modifier.weight(1f))
            SideText(groups.getValue(name).size.toString())
        }
    }
}

private fun artistSongs(name: String, songs: List<Track>) =
    SongsScreen(name, songs, durations = true, shuffleRow = songs.size > 2, sub = { it.album })

/** Artists with one album go straight to their songs, others get an album list first. */
fun artistScreen(name: String, songs: List<Track>): Screen =
    if (Library.albums(songs).size < 2) artistSongs(name, songs) else ArtistScreen(name, songs)

@Composable
private fun ArtistView(s: ArtistScreen, push: (Screen) -> Unit, hideAlbum: (String) -> Unit) {
    val albums = remember(s) { Library.albums(s.songs) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ListRow({ push(artistSongs(s.title, s.songs)) }, chevron = true) {
                TwoLines("Все песни", songsWord(s.songs.size), Modifier.weight(1f))
            }
        }
        items(albums, key = { it.name }) { a ->
            AlbumRow(a, songsWord(a.songs.size), { hideAlbum(a.name) }) { push(albumScreen(a)) }
        }
    }
}

@Composable
private fun AlbumRow(a: Album, sub: String, onLongClick: () -> Unit, onClick: () -> Unit) {
    ListRow(onClick, chevron = true, minHeight = 57.dp, onLongClick = onLongClick) {
        Art(a.songs.first(), Modifier.padding(end = 10.dp).size(56.dp))
        TwoLines(a.name, sub, Modifier.weight(1f))
    }
}

@Composable
private fun AlbumsList(tracks: List<Track>, hideAlbum: (String) -> Unit, onOpen: (Album) -> Unit) {
    val albums = remember(tracks) { Library.albums(tracks) }
    SectionedList(albums, { it.name }, withIndex = true) { a -> AlbumRow(a, a.artist, { hideAlbum(a.name) }) { onOpen(a) } }
}

@Composable
private fun MoreList(
    count: Int,
    hiddenCount: Int,
    onGenres: () -> Unit,
    onShuffleAll: () -> Unit,
    onRefresh: () -> Unit,
    onHidden: () -> Unit,
    light: Boolean,
    onTheme: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ListRow(onGenres, chevron = true) {
                Box(Modifier.width(40.dp)) { Ico(Icons.Genre, 24.dp, C.Fg2) }
                TwoLines("Жанры", null, Modifier.weight(1f))
            }
        }
        item { ShuffleRow("Перемешать всё", songsWord(count), onShuffleAll) }
        item {
            ListRow(onRefresh) {
                Box(Modifier.width(40.dp)) { Ico(Icons.Refresh, 24.dp, C.Fg2) }
                TwoLines("Обновить медиатеку", "Перечитать музыку на телефоне", Modifier.weight(1f))
            }
        }
        item {
            ListRow(onTheme) {
                Box(Modifier.width(40.dp)) { Ico(if (light) Icons.Sun else Icons.Moon, 24.dp, C.Fg2) }
                TwoLines("Оформление", if (light) "Светлое — нажми для тёмного" else "Тёмное — нажми для светлого", Modifier.weight(1f))
            }
        }
        item {
            ListRow(onHidden, chevron = true) {
                Box(Modifier.width(40.dp)) { Ico(Icons.Albums, 24.dp, C.Fg2) }
                TwoLines("Скрытые", "Альбомы и папки, убранные из медиатеки", Modifier.weight(1f))
                SideText(hiddenCount.toString())
            }
        }
    }
}

@Composable
private fun HiddenList(
    albums: Set<String>,
    folders: Set<String>,
    onRestoreAlbum: (String) -> Unit,
    onRestoreFolder: (String) -> Unit,
) {
    if (albums.isEmpty() && folders.isEmpty()) {
        return EmptyText("Ничего не скрыто.\nЧтобы скрыть альбом или папку, подержи палец на ней в списке.")
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (folders.isNotEmpty()) {
            item { SectionHeader("Папки") }
            items(folders.sortedWith(Library.collator)) { name ->
                ListRow({ onRestoreFolder(name) }) {
                    Box(Modifier.width(40.dp)) { Ico(Icons.Folders, 26.dp, C.Dim) }
                    TwoLines(name, "Нажми, чтобы вернуть", Modifier.weight(1f))
                }
            }
        }
        if (albums.isNotEmpty()) {
            item { SectionHeader("Альбомы") }
            items(albums.sortedWith(Library.collator)) { name ->
                ListRow({ onRestoreAlbum(name) }) {
                    Box(Modifier.width(40.dp)) { Ico(Icons.Albums, 24.dp, C.Dim) }
                    TwoLines(name, "Нажми, чтобы вернуть", Modifier.weight(1f))
                }
            }
        }
    }
}
