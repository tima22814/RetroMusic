package dev.local.retromusic

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlaybackState(
    val current: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    /** Queue in the order it will actually play (accounts for shuffle). */
    val queue: List<Track> = emptyList(),
    val queuePos: Int = -1,
)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state

    private var controller: MediaController? = null
    private var byId: Map<String, Track> = emptyMap()

    init {
        viewModelScope.launch {
            val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
            val c = MediaController.Builder(app, token).buildAsync().await()
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = sync()
            })
            sync()
        }
        viewModelScope.launch {
            while (isActive) {
                controller?.let { c ->
                    if (c.isPlaying) _state.value = _state.value.copy(positionMs = c.currentPosition, durationMs = duration(c))
                }
                delay(400)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { Library.load(getApplication()) }
            byId = list.associateBy { it.id.toString() }
            allTracks = list
            applyHidden()
            _loaded.value = true
            sync()
        }
    }

    /* Hidden albums / folders: only taken out of the library, files stay on the phone. */

    private val prefs = app.getSharedPreferences("library", android.content.Context.MODE_PRIVATE)
    private var allTracks: List<Track> = emptyList()
    private val _hiddenAlbums = MutableStateFlow(prefs.getStringSet("hiddenAlbums", null).orEmpty())
    val hiddenAlbums: StateFlow<Set<String>> = _hiddenAlbums
    private val _hiddenFolders = MutableStateFlow(prefs.getStringSet("hiddenFolders", null).orEmpty())
    val hiddenFolders: StateFlow<Set<String>> = _hiddenFolders

    private val _lightTheme = MutableStateFlow(prefs.getBoolean("lightTheme", false))
    val lightTheme: StateFlow<Boolean> = _lightTheme

    fun setLightTheme(on: Boolean) {
        _lightTheme.value = on
        prefs.edit().putBoolean("lightTheme", on).apply()
    }

    fun setAlbumHidden(album: String, hidden: Boolean) {
        _hiddenAlbums.value = if (hidden) _hiddenAlbums.value + album else _hiddenAlbums.value - album
        prefs.edit().putStringSet("hiddenAlbums", _hiddenAlbums.value).apply()
        applyHidden()
    }

    fun setFolderHidden(folder: String, hidden: Boolean) {
        _hiddenFolders.value = if (hidden) _hiddenFolders.value + folder else _hiddenFolders.value - folder
        prefs.edit().putStringSet("hiddenFolders", _hiddenFolders.value).apply()
        applyHidden()
    }

    private fun applyHidden() {
        val albums = _hiddenAlbums.value
        val folders = _hiddenFolders.value
        _tracks.value = allTracks.filter { it.album !in albums && it.folder !in folders }
    }

    fun playList(list: List<Track>, index: Int, shuffle: Boolean = false) {
        val c = controller ?: return
        c.shuffleModeEnabled = shuffle
        c.setMediaItems(list.map(::toItem), index, 0)
        c.prepare()
        c.play()
    }

    /** Jump to a position in the play-order queue shown on the back of the cover. */
    fun playQueueAt(pos: Int) {
        val c = controller ?: return
        playOrder(c).getOrNull(pos)?.let { c.seekTo(it, 0) }
        c.play()
    }

    fun toggle() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            val all = _tracks.value.sortedWith(Library.byTitle)
            if (all.isNotEmpty()) playList(all, 0)
        } else if (c.isPlaying) c.pause() else c.play()
    }

    fun next() {
        val c = controller ?: return
        if (c.hasNextMediaItem()) c.seekToNextMediaItem() else if (c.mediaItemCount > 0) c.seekTo(playOrder(c).first(), 0)
    }

    fun prev() {
        val c = controller ?: return
        if (c.currentPosition > 3000 || !c.hasPreviousMediaItem()) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        _state.value = _state.value.copy(positionMs = ms)
    }

    fun setShuffle(on: Boolean) {
        controller?.shuffleModeEnabled = on
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun sync() {
        val c = controller ?: return
        val order = playOrder(c)
        val queue = order.mapNotNull { byId[c.getMediaItemAt(it).mediaId] }
        _state.value = PlaybackState(
            current = c.currentMediaItem?.mediaId?.let { byId[it] },
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition,
            durationMs = duration(c),
            shuffle = c.shuffleModeEnabled,
            repeat = c.repeatMode,
            queue = queue,
            queuePos = order.indexOf(c.currentMediaItemIndex),
        )
    }

    private fun duration(c: Player): Long {
        val d = c.duration
        return if (d == C.TIME_UNSET) byId[c.currentMediaItem?.mediaId]?.durationMs ?: 0 else d
    }

    private fun playOrder(c: Player): List<Int> {
        val tl = c.currentTimeline
        if (tl.isEmpty) return emptyList()
        val out = ArrayList<Int>(tl.windowCount)
        var i = tl.getFirstWindowIndex(c.shuffleModeEnabled)
        while (i != C.INDEX_UNSET && out.size < tl.windowCount) {
            out += i
            i = tl.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
        }
        return out
    }

    private fun toItem(t: Track) = MediaItem.Builder()
        .setMediaId(t.id.toString())
        .setUri(t.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(t.title)
                .setArtist(t.artist)
                .setAlbumTitle(t.album)
                // Cover for the notification and lock screen.
                .setArtworkUri(android.net.Uri.parse("content://media/external/audio/albumart/${t.albumId}"))
                .build(),
        )
        .build()

    override fun onCleared() {
        controller?.release()
        super.onCleared()
    }
}
