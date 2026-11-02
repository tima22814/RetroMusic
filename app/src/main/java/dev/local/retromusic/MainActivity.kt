package dev.local.retromusic

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.local.retromusic.ui.C
import dev.local.retromusic.ui.DarkPalette
import dev.local.retromusic.ui.LightPalette
import dev.local.retromusic.ui.LocalPalette
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import dev.local.retromusic.ui.CoverFlow
import dev.local.retromusic.ui.GlossButton
import dev.local.retromusic.ui.LibraryUi
import dev.local.retromusic.ui.NowPlaying

class MainActivity : ComponentActivity() {
    private val vm: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val light by vm.lightTheme.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalPalette provides if (light) LightPalette else DarkPalette) {
                Box(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing)) {
                    Box(Modifier.fillMaxSize().background(C.Bg)) { App(vm) }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Pick up music added while the app was in the background.
        if (hasAudioPermission()) vm.refresh()
    }

    fun hasAudioPermission() = ContextCompat.checkSelfPermission(this, audioPermission) == PackageManager.PERMISSION_GRANTED
}

val audioPermission: String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
private fun App(vm: PlayerViewModel) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    var granted by remember { mutableStateOf<Boolean?>(null) }

    val permissions = buildList {
        add(audioPermission)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        granted = res[audioPermission] == true
        if (granted == true) vm.refresh()
    }
    LaunchedEffect(Unit) { launcher.launch(permissions) }

    if (granted == false && !loaded) {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Плееру нужен доступ к музыке на телефоне, чтобы показать твои песни.",
                color = C.Fg2, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 120.dp, bottom = 24.dp),
            )
            GlossButton("Разрешить", { launcher.launch(permissions) }, fontSize = 14)
        }
        return
    }

    var nowPlaying by rememberSaveable { mutableStateOf(false) }
    var manualCoverFlow by rememberSaveable { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Closed by hand while sideways: stay in the library until the phone is turned upright again.
    var sidewaysClosed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(landscape) { if (!landscape) sidewaysClosed = false }

    // Like the original: turning the phone sideways shows Cover Flow.
    if (landscape && !sidewaysClosed && tracks.isNotEmpty()) {
        CoverFlow(vm, tracks, state) { sidewaysClosed = true; manualCoverFlow = false }
        return
    }

    Box(Modifier.fillMaxSize()) {
        LibraryUi(
            vm, tracks, state,
            onNowPlaying = { nowPlaying = true },
            onCoverFlow = { manualCoverFlow = true },
        )
        AnimatedVisibility(
            nowPlaying && state.current != null,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
        ) {
            NowPlaying(vm, state) { nowPlaying = false }
        }
        if (manualCoverFlow) CoverFlow(vm, tracks, state) { manualCoverFlow = false }
    }
}
