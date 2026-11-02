package io.github.tima22814.retromusic

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import java.io.File

data class Track(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val folder: String,
    val track: Int,
    val durationMs: Long,
)

data class Album(val name: String, val artist: String, val songs: List<Track>)

private val leadingNumber = Regex("""^\s*(\d{1,3})[.)_\s-]+\s*(?=\D)""")
private val artistTitle = Regex("""^\s*(.+?)\s+[-–—]\s+(.+?)\s*$""")
private const val UNKNOWN_ARTIST = "Неизвестный исполнитель"

object Library {
    fun load(context: Context): List<Track> {
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
            @Suppress("DEPRECATION") MediaStore.Audio.Media.DATA,
        )
        val out = ArrayList<Track>()
        context.contentResolver.query(
            collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null,
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val iTrack = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            @Suppress("DEPRECATION")
            val iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val path = c.getString(iData).orEmpty()
                val folder = File(path).parentFile?.name ?: "Музыка"
                var stem = c.getString(iName).orEmpty().substringBeforeLast('.')
                var trackNo = c.getInt(iTrack) % 1000

                // Filenames are usually "[NN. ]Artist - Title" and cleaner than the tags.
                leadingNumber.find(stem)?.let {
                    stem = stem.substring(it.range.last + 1)
                    if (trackNo == 0) trackNo = it.groupValues[1].toInt()
                }
                val tagArtist = c.getString(iArtist).orEmpty().takeUnless { it.isBlank() || it == "<unknown>" }
                val (artist, title) = artistTitle.matchEntire(stem)?.let { it.groupValues[1] to it.groupValues[2] }
                    ?: ((tagArtist ?: UNKNOWN_ARTIST) to (c.getString(iTitle).orEmpty().ifBlank { stem }))

                out += Track(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    title = title,
                    artist = artist,
                    album = c.getString(iAlbum).orEmpty().takeUnless { it.isBlank() || it == "<unknown>" } ?: folder,
                    albumId = c.getLong(iAlbumId),
                    folder = folder,
                    track = trackNo,
                    durationMs = c.getLong(iDur),
                )
            }
        }
        return out
    }

    fun albums(tracks: List<Track>): List<Album> =
        tracks.groupBy { it.album }.map { (name, songs) ->
            Album(name, artistLabel(songs), songs.sortedWith(albumOrder))
        }.sortedWith(compareBy(collator) { it.name })

    fun artistLabel(songs: List<Track>): String {
        val artists = songs.map { it.artist }.distinct()
        return if (artists.size > 2) "Разные исполнители" else artists.joinToString(", ")
    }

    val collator: java.text.Collator = java.text.Collator.getInstance(java.util.Locale("ru")).apply {
        strength = java.text.Collator.PRIMARY
    }
    val byTitle: Comparator<Track> = compareBy(collator) { it.title }
    val albumOrder: Comparator<Track> = compareBy<Track> { if (it.track == 0) 999 else it.track }.then(byTitle)
}

/** Album art from the file's embedded picture, cached in memory. */
object ArtLoader {
    private val cache = object : LruCache<Long, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap) = value.byteCount
    }
    private val missing = HashSet<Long>()

    fun cached(track: Track): Bitmap? = cache.get(track.id)

    fun load(context: Context, track: Track, px: Int = 600): Bitmap? {
        cache.get(track.id)?.let { return it }
        synchronized(missing) { if (track.id in missing) return null }
        val bmp = try {
            if (Build.VERSION.SDK_INT >= 29) {
                context.contentResolver.loadThumbnail(track.uri, Size(px, px), null)
            } else {
                MediaMetadataRetriever().run {
                    try {
                        setDataSource(context, track.uri)
                        embeddedPicture?.let { decodeScaled(it, px) }
                    } finally {
                        release()
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
        if (bmp != null) cache.put(track.id, bmp) else synchronized(missing) { missing += track.id }
        return bmp
    }

    private fun decodeScaled(bytes: ByteArray, px: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= px) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
