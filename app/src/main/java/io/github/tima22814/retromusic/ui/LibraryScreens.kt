package io.github.tima22814.retromusic.ui

import android.app.Activity
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
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tima22814.retromusic.Album
import io.github.tima22814.retromusic.AppLanguage
import io.github.tima22814.retromusic.ArtLoader
import io.github.tima22814.retromusic.Library
import io.github.tima22814.retromusic.PlaybackState
import io.github.tima22814.retromusic.PlayerViewModel
import io.github.tima22814.retromusic.R
import io.github.tima22814.retromusic.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/* ---------- helpers ---------- */

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun songsWord(n: Int): String = pluralStringResource(R.plurals.songs_count, n, n)

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
    /** Folder / artist / album name; empty for the fixed screens, which name a [titleRes]. */
    val title: String get() = ""
    @get:StringRes val titleRes: Int get() = 0
}

@Composable
fun Screen.label(): String = if (titleRes != 0) stringResource(titleRes) else title

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
    override val titleRes: Int = 0,
) : Screen

data object FoldersScreen : Screen { override val titleRes = R.string.folders }
data object ArtistsScreen : Screen { override val titleRes = R.string.artists }
data class ArtistScreen(override val title: String, val songs: List<Track>) : Screen
data object AlbumsScreen : Screen { override val titleRes = R.string.albums }
data object GenresScreen : Screen { override val titleRes = R.string.genres }
data object MoreScreen : Screen { override val titleRes = R.string.more }
data object HiddenScreen : Screen { override val titleRes = R.string.hidden }
data object LanguageScreen : Screen { override val titleRes = R.string.language }

/** [message] takes the album / folder [name] as its argument. */
private data class Confirm(
    @StringRes val title: Int,
    @StringRes val message: Int,
    val name: String,
    @StringRes val button: Int,
    val action: () -> Unit,
)

private val tabs = listOf(
    R.string.folders to Icons.Folders,
    R.string.artists to Icons.Artists,
    R.string.albums to Icons.Albums,
    R.string.songs to Icons.Songs,
    R.string.more to Icons.More,
)

private fun rootFor(tab: Int, tracks: List<Track>): Screen = when (tab) {
    0 -> FoldersScreen
    1 -> ArtistsScreen
    2 -> AlbumsScreen
    3 -> SongsScreen("", tracks, index = true, shuffleRow = true, titleRes = R.string.songs)
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
        confirm = Confirm(R.string.hide_album_q, R.string.hide_album_msg, name, R.string.hide) {
            vm.setAlbumHidden(name, true)
        }
    }
    val hideFolder: (String) -> Unit = { name ->
        confirm = Confirm(R.string.hide_folder_q, R.string.hide_folder_msg, name, R.string.hide) {
            vm.setFolderHidden(name, true)
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(C.Bg)) {
        NavBar(
            title = top.label(),
            left = when {
                stack.size > 1 -> {
                    { val prev = stack[stack.size - 2].label(); BackButton(if (prev.length > 11) stringResource(R.string.back) else prev) { pop() } }
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
                    onLanguage = { push(LanguageScreen) },
                )
                LanguageScreen -> LanguageList()
                HiddenScreen -> HiddenList(
                    hiddenAlbums, hiddenFolders,
                    onRestoreAlbum = { name ->
                        confirm = Confirm(R.string.restore_album_q, R.string.restore_album_msg, name, R.string.restore) { vm.setAlbumHidden(name, false) }
                    },
                    onRestoreFolder = { name ->
                        confirm = Confirm(R.string.restore_folder_q, R.string.restore_folder_msg, name, R.string.restore) { vm.setFolderHidden(name, false) }
                    },
                )
            }
        }
        TabBar(tabs.map { (res, icon) -> stringResource(res) to icon }, tab) { i ->
            if (i == tab) stacks.remove(tab) else tab = i
        }
    }
    confirm?.let { c ->
        ConfirmDialog(stringResource(c.title), stringResource(c.message, c.name), stringResource(c.button), c.action) { confirm = null }
    }
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
    if (songs.isEmpty()) return EmptyText(stringResource(R.string.no_songs))
    val indexOf = remember(songs) { songs.withIndex().associate { it.value.id to it.index } }

    val header: LazyListScope.() -> Unit = {
        s.album?.let { a -> item { AlbumHeader(a) { hideAlbum(a.name) } } }
        if (s.shuffleRow) item { ShuffleRow(stringResource(R.string.shuffle), null) { shuffle(songs) } }
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
            Text(stringResource(R.string.songs_minutes, songsWord(a.songs.size), minutes), color = C.Dim, fontSize = 12.sp)
            Box(Modifier.padding(top = 8.dp)) { GlossButton(stringResource(R.string.hide_album), onHide, fontSize = 11) }
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
    if (groups.isEmpty()) return EmptyText(stringResource(R.string.empty_library))
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
                TwoLines(stringResource(R.string.all_songs), songsWord(s.songs.size), Modifier.weight(1f))
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
    onLanguage: () -> Unit,
) {
    val ctx = LocalContext.current
    val language = remember { AppLanguage.current(ctx) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ListRow(onGenres, chevron = true) {
                Box(Modifier.width(40.dp)) { Ico(Icons.Genre, 24.dp, C.Fg2) }
                TwoLines(stringResource(R.string.genres), null, Modifier.weight(1f))
            }
        }
        item { ShuffleRow(stringResource(R.string.shuffle_all), songsWord(count), onShuffleAll) }
        item {
            ListRow(onRefresh) {
                Box(Modifier.width(40.dp)) { Ico(Icons.Refresh, 24.dp, C.Fg2) }
                TwoLines(stringResource(R.string.refresh), stringResource(R.string.refresh_sub), Modifier.weight(1f))
            }
        }
        item {
            ListRow(onTheme) {
                Box(Modifier.width(40.dp)) { Ico(if (light) Icons.Sun else Icons.Moon, 24.dp, C.Fg2) }
                TwoLines(
                    stringResource(R.string.appearance),
                    stringResource(if (light) R.string.theme_light else R.string.theme_dark),
                    Modifier.weight(1f),
                )
            }
        }
        item {
            ListRow(onLanguage, chevron = true) {
                Box(Modifier.width(40.dp)) { Ico(Icons.Globe, 24.dp, C.Fg2) }
                TwoLines(
                    stringResource(R.string.language),
                    AppLanguage.choices.firstOrNull { it.first == language }?.second ?: stringResource(R.string.system_language),
                    Modifier.weight(1f),
                )
            }
        }
        item {
            ListRow(onHidden, chevron = true) {
                Box(Modifier.width(40.dp)) { Ico(Icons.Albums, 24.dp, C.Fg2) }
                TwoLines(stringResource(R.string.hidden), stringResource(R.string.hidden_sub), Modifier.weight(1f))
                SideText(hiddenCount.toString())
            }
        }
    }
}

@Composable
private fun LanguageList() {
    val activity = LocalContext.current as Activity
    val current = remember { AppLanguage.current(activity) }
    val rows = listOf("" to stringResource(R.string.system_language)) + AppLanguage.choices
    LazyColumn(Modifier.fillMaxSize()) {
        items(rows) { (tag, name) ->
            ListRow({ if (tag != current) AppLanguage.set(activity, tag) }) {
                TwoLines(name, null, Modifier.weight(1f))
                if (tag == current) Text("✓", color = C.Fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
        return EmptyText(stringResource(R.string.nothing_hidden))
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (folders.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.folders)) }
            items(folders.sortedWith(Library.collator)) { name ->
                ListRow({ onRestoreFolder(name) }) {
                    Box(Modifier.width(40.dp)) { Ico(Icons.Folders, 26.dp, C.Dim) }
                    TwoLines(name, stringResource(R.string.tap_to_restore), Modifier.weight(1f))
                }
            }
        }
        if (albums.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.albums)) }
            items(albums.sortedWith(Library.collator)) { name ->
                ListRow({ onRestoreAlbum(name) }) {
                    Box(Modifier.width(40.dp)) { Ico(Icons.Albums, 24.dp, C.Dim) }
                    TwoLines(name, stringResource(R.string.tap_to_restore), Modifier.weight(1f))
                }
            }
        }
    }
}
