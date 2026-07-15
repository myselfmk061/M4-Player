package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.models.VideoItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

enum class VideoAspectRatio(val label: String, val mode: Int) {
    FIT("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Fill", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    STRETCH("Stretch", AspectRatioFrameLayout.RESIZE_MODE_FILL)
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    videoItem: VideoItem?,
    networkUrl: String?,
    onBack: (lastPosition: Long) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    
    // Auto-rotate landscape on player open
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        // Keep screen on
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val videoUri = remember {
        when {
            videoItem != null -> {
                if (videoItem.path.startsWith("content://") || videoItem.path.startsWith("file://")) {
                    Uri.parse(videoItem.path)
                } else {
                    Uri.fromFile(File(videoItem.path))
                }
            }
            !networkUrl.isNullOrEmpty() -> Uri.parse(networkUrl)
            else -> Uri.EMPTY
        }
    }

    if (videoUri == Uri.EMPTY) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("No playable video stream found.", color = Color.White)
        }
        return
    }

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.Builder()
                .setUri(videoUri)
                .build()
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }

    // Set initial position if any
    LaunchedEffect(videoItem) {
        videoItem?.let {
            if (it.lastPosition > 0) {
                exoPlayer.seekTo(it.lastPosition)
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                exoPlayer.pause()
            } else if (event == Lifecycle.Event.ON_DESTROY) {
                exoPlayer.release()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Custom UI Overlays and state managers
    PlayerContent(
        player = exoPlayer,
        title = videoItem?.title ?: networkUrl?.substringAfterLast('/') ?: "Streaming Video",
        onBack = {
            val lastPos = exoPlayer.currentPosition
            exoPlayer.release()
            onBack(lastPos)
        },
        activity = activity
    )

    // Hardware back press handler
    BackHandler {
        val lastPos = exoPlayer.currentPosition
        exoPlayer.release()
        onBack(lastPos)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerContent(
    player: ExoPlayer,
    title: String,
    onBack: () -> Unit,
    activity: Activity?
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isControlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }

    // Subtitles State
    var subtitlesEnabled by remember { mutableStateOf(true) }
    var subtitles by remember { mutableStateOf<List<SubtitleCue>>(getSampleSubtitles()) }
    var subtitleFileName by remember { mutableStateOf<String?>("Sample Subtitles") }

    // Subtitle file picker launcher
    val subtitlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val content = inputStream?.bufferedReader()?.use { reader -> reader.readText() }
                if (content != null) {
                    val parsed = parseSrt(content)
                    if (parsed.isNotEmpty()) {
                        subtitles = parsed
                        subtitlesEnabled = true
                        subtitleFileName = it.lastPathSegment?.substringAfterLast('/') ?: "custom.srt"
                        Toast.makeText(context, "Loaded ${parsed.size} subtitles successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Could not find valid SRT cues in the file", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error loading file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    // Playback state observing
    var isPlaying by remember { mutableStateOf(player.playWhenReady) }
    var currentPosition by remember { mutableStateOf(player.currentPosition) }
    var duration by remember { mutableStateOf(player.duration) }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var aspectRatio by remember { mutableStateOf(VideoAspectRatio.FIT) }

    // Gestures overlay states
    var brightnessOverlayValue by remember { mutableStateOf<Float?>(null) }
    var volumeOverlayValue by remember { mutableStateOf<Int?>(null) }
    var maxVolume by remember { mutableStateOf(15) }
    var seekProgressOverlayValue by remember { mutableStateOf<String?>(null) }

    // Audio / Subtitles Track Settings
    var showTrackSelectionDialog by remember { mutableStateOf(false) }

    // Sleep Timer
    var sleepTimerMinutesRemaining by remember { mutableStateOf<Int?>(null) }
    var sleepTimerJob by remember { mutableStateOf<Job?>(null) }

    // Get current position periodically (fast polling for accurate subtitles)
    LaunchedEffect(isPlaying) {
        while (true) {
            currentPosition = player.currentPosition
            duration = player.duration
            delay(100)
        }
    }

    // Monitor real player playState changes
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                duration = player.duration
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    // Controls Auto-Hide
    LaunchedEffect(isControlsVisible, isPlaying) {
        if (isControlsVisible && isPlaying) {
            delay(4000)
            isControlsVisible = false
        }
    }

    // AudioManager Setup for volume gesture
    val audioManager = remember {
        activity?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    LaunchedEffect(audioManager) {
        audioManager?.let {
            maxVolume = it.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("player_screen_root")
    ) {
        // Video View Layout
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false // Use our custom controls in compose
                    resizeMode = aspectRatio.mode
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    this.player = player
                }
            },
            update = { view ->
                view.resizeMode = aspectRatio.mode
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isLocked) {
                    if (isLocked) {
                        detectTapGestures(
                            onTap = { isControlsVisible = !isControlsVisible }
                        )
                    } else {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                val viewWidth = size.width
                                val tapX = offset.x
                                if (tapX < viewWidth * 0.35f) {
                                    // Double-tap left: Seek backward 10s
                                    val newPos = (player.currentPosition - 10000).coerceAtLeast(0)
                                    player.seekTo(newPos)
                                    seekProgressOverlayValue = "-10s"
                                    coroutineScope.launch {
                                        delay(800)
                                        seekProgressOverlayValue = null
                                    }
                                } else if (tapX > viewWidth * 0.65f) {
                                    // Double-tap right: Seek forward 10s
                                    val newPos = (player.currentPosition + 10000).coerceAtMost(player.duration)
                                    player.seekTo(newPos)
                                    seekProgressOverlayValue = "+10s"
                                    coroutineScope.launch {
                                        delay(800)
                                        seekProgressOverlayValue = null
                                    }
                                }
                            },
                            onTap = {
                                isControlsVisible = !isControlsVisible
                            },
                            onLongPress = {
                                // Speed 2x on long press
                                player.setPlaybackSpeed(2.0f)
                                playbackSpeed = 2.0f
                                seekProgressOverlayValue = "2.0x Speed"
                            },
                            onPress = {
                                tryAwaitRelease()
                                // Revert to normal speed on release
                                player.setPlaybackSpeed(1.0f)
                                playbackSpeed = 1.0f
                                if (seekProgressOverlayValue == "2.0x Speed") {
                                    seekProgressOverlayValue = null
                                }
                            }
                        )
                    }
                }
                .pointerInput(isLocked) {
                    if (!isLocked) {
                        detectDragGestures(
                            onDragStart = { },
                            onDragEnd = {
                                brightnessOverlayValue = null
                                volumeOverlayValue = null
                                seekProgressOverlayValue = null
                            },
                            onDragCancel = {
                                brightnessOverlayValue = null
                                volumeOverlayValue = null
                                seekProgressOverlayValue = null
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val viewWidth = size.width
                                val viewHeight = size.height
                                val currentX = change.position.x

                                if (abs(dragAmount.x) > abs(dragAmount.y) * 1.5f) {
                                    // Horizontal Swipe: Seek
                                    val factor = dragAmount.x / viewWidth.toFloat()
                                    val seekOffset = (factor * 90000L).toLong() // Seek scale
                                    val newPosition = (player.currentPosition + seekOffset).coerceIn(0, player.duration)
                                    player.seekTo(newPosition)
                                    seekProgressOverlayValue = formatTime(newPosition)
                                } else {
                                    // Vertical Swipe
                                    if (currentX < viewWidth / 2f) {
                                        // Left Side: Brightness
                                        activity?.let { act ->
                                            val lp = act.window.attributes
                                            var screenBrightness = lp.screenBrightness
                                            if (screenBrightness < 0) screenBrightness = 0.5f // Default fallback
                                            
                                            val newBrightness = (screenBrightness - (dragAmount.y / viewHeight.toFloat())).coerceIn(0.01f, 1.0f)
                                            lp.screenBrightness = newBrightness
                                            act.window.attributes = lp
                                            brightnessOverlayValue = newBrightness
                                        }
                                    } else {
                                        // Right Side: Volume
                                        audioManager?.let { am ->
                                            val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                                            val volumeDelta = -(dragAmount.y / viewHeight.toFloat() * maxVolume).toInt()
                                            val newVolume = (currentVol + volumeDelta).coerceIn(0, maxVolume)
                                            am.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                                            volumeOverlayValue = newVolume
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
        )

        // Custom Beautiful Controls
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(animationSpec = tween(250)),
            exit = fadeOut(animationSpec = tween(250))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            ) {
                if (isLocked) {
                    // Lock screen state: show only unlock button
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        IconButton(
                            onClick = {
                                isLocked = false
                                isControlsVisible = true
                            },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .size(56.dp)
                                .testTag("unlock_screen_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LockOpen,
                                contentDescription = "Unlock controls",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                } else {
                    // Full controls overlay
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Header Toolbar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("player_back_button")
                            ) {
                                Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )

                            // PiP Support Button (Android 8.0+)
                            IconButton(onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    activity?.enterPictureInPictureMode()
                                }
                            }) {
                                Icon(Icons.Outlined.PictureInPicture, "PiP", tint = Color.White)
                            }

                            // Sleep Timer Button
                            IconButton(onClick = {
                                val current = sleepTimerMinutesRemaining
                                if (current == null) {
                                    sleepTimerMinutesRemaining = 15
                                    sleepTimerJob = coroutineScope.launch {
                                        var rem = 15
                                        while (rem > 0) {
                                            delay(60000)
                                            rem--
                                            sleepTimerMinutesRemaining = rem
                                        }
                                        player.pause()
                                        sleepTimerMinutesRemaining = null
                                    }
                                } else if (current == 15) {
                                    sleepTimerMinutesRemaining = 30
                                    sleepTimerJob?.cancel()
                                    sleepTimerJob = coroutineScope.launch {
                                        var rem = 30
                                        while (rem > 0) {
                                            delay(60000)
                                            rem--
                                            sleepTimerMinutesRemaining = rem
                                        }
                                        player.pause()
                                        sleepTimerMinutesRemaining = null
                                    }
                                } else {
                                    sleepTimerJob?.cancel()
                                    sleepTimerMinutesRemaining = null
                                }
                            }) {
                                BadgedBox(
                                    badge = {
                                        sleepTimerMinutesRemaining?.let { min ->
                                            Badge { Text("${min}m", color = Color.White, fontSize = 9.sp) }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Outlined.Snooze, "Sleep Timer", tint = Color.White)
                                }
                            }

                            // Track selections (Audio & Subtitles)
                            IconButton(onClick = { showTrackSelectionDialog = true }) {
                                Icon(Icons.Outlined.Subtitles, "Audio/Subtitles", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Center row controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Lock button
                            IconButton(
                                onClick = {
                                    isLocked = true
                                    isControlsVisible = true
                                },
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                    .size(44.dp)
                            ) {
                                Icon(Icons.Default.Lock, "Lock Screen", tint = Color.White, modifier = Modifier.size(20.dp))
                            }

                            Spacer(modifier = Modifier.width(32.dp))

                            // Seek Backward
                            IconButton(
                                onClick = {
                                    val newPos = (player.currentPosition - 10000).coerceAtLeast(0)
                                    player.seekTo(newPos)
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Replay10, "Rewind 10s", tint = Color.White, modifier = Modifier.size(36.dp))
                            }

                            Spacer(modifier = Modifier.width(24.dp))

                            // Play Pause Pulsing Icon
                            Box(
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                                    .padding(8.dp)
                                    .clip(CircleShape)
                                    .background(Color.White, CircleShape)
                                    .clickable {
                                        if (isPlaying) player.pause() else player.play()
                                    }
                                    .size(56.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.Black,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(24.dp))

                            // Seek Forward
                            IconButton(
                                onClick = {
                                    val newPos = (player.currentPosition + 10000).coerceAtMost(player.duration)
                                    player.seekTo(newPos)
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Forward10, "Forward 10s", tint = Color.White, modifier = Modifier.size(36.dp))
                            }

                            Spacer(modifier = Modifier.width(32.dp))

                            // Aspect Ratio cycling
                            IconButton(
                                onClick = {
                                    aspectRatio = when (aspectRatio) {
                                        VideoAspectRatio.FIT -> VideoAspectRatio.FILL
                                        VideoAspectRatio.FILL -> VideoAspectRatio.STRETCH
                                        VideoAspectRatio.STRETCH -> VideoAspectRatio.FIT
                                    }
                                },
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                    .size(44.dp)
                            ) {
                                Icon(
                                    imageVector = when (aspectRatio) {
                                        VideoAspectRatio.FIT -> Icons.Outlined.AspectRatio
                                        VideoAspectRatio.FILL -> Icons.Default.CropFree
                                        VideoAspectRatio.STRETCH -> Icons.Default.FitScreen
                                    },
                                    contentDescription = "Aspect Ratio",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Bottom Toolbar Seekbar and Track Controls
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            // Slider / Timeline
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatTime(currentPosition),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )

                                Slider(
                                    value = if (duration > 0) currentPosition.toFloat() else 0f,
                                    onValueChange = { player.seekTo(it.toLong()) },
                                    valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp)
                                        .testTag("playback_seekbar"),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF38BDF8),
                                        activeTrackColor = Color(0xFF38BDF8),
                                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                    )
                                )

                                Text(
                                    text = formatTime(duration),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Playback Speed controls row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                        .clickable {
                                            val speed = when (playbackSpeed) {
                                                0.5f -> 1.0f
                                                1.0f -> 1.5f
                                                1.5f -> 2.0f
                                                2.0f -> 3.0f
                                                3.0f -> 0.5f
                                                else -> 1.0f
                                            }
                                            player.setPlaybackSpeed(speed)
                                            playbackSpeed = speed
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Speed,
                                        "Speed",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${playbackSpeed}x Speed",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Text(
                                    text = "Aspect: ${aspectRatio.label}",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Active Subtitles Overlay
        val currentSubtitle = remember(currentPosition, subtitles, subtitlesEnabled) {
            if (!subtitlesEnabled) null
            else subtitles.firstOrNull { currentPosition in it.startTimeMs..it.endTimeMs }
        }

        currentSubtitle?.let { cue ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (isControlsVisible) 135.dp else 45.dp)
                    .padding(horizontal = 48.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = cue.text,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )
                }
            }
        }

        // Gesture indicators overlay (Center overlays)
        // Brightness indicator
        brightnessOverlayValue?.let { value ->
            GestureIndicator(
                icon = Icons.Outlined.LightMode,
                label = "Brightness",
                percent = (value * 100).toInt()
            )
        }

        // Volume indicator
        volumeOverlayValue?.let { value ->
            GestureIndicator(
                icon = Icons.Outlined.VolumeUp,
                label = "Volume",
                percent = ((value.toFloat() / maxVolume) * 100).toInt()
            )
        }

        // Seek indicators
        seekProgressOverlayValue?.let { label ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    // Audio & Subtitle selection dialog
    if (showTrackSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showTrackSelectionDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Subtitles,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Subtitles & Playback Options")
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Customize your subtitles and audio tracks. Load any local .srt file or use the built-in demo captions.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Toggle visibility
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Subtitles Enabled", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Toggle overlay display on player", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        Switch(
                            checked = subtitlesEnabled,
                            onCheckedChange = { subtitlesEnabled = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Current status
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text("ACTIVE TRACK:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = if (subtitles.isEmpty()) "None Loaded" else subtitleFileName ?: "Loaded Subtitles",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (subtitles.isNotEmpty()) {
                                Text(
                                    text = "${subtitles.size} cues parsed",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Buttons
                    Button(
                        onClick = { subtitlePickerLauncher.launch("text/*") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Load Custom .SRT File")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                subtitles = getSampleSubtitles()
                                subtitleFileName = "Sample Subtitles"
                                subtitlesEnabled = true
                                Toast.makeText(context, "Loaded sample subtitles!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Load Demo")
                        }

                        OutlinedButton(
                            onClick = {
                                subtitles = emptyList()
                                subtitleFileName = null
                                subtitlesEnabled = false
                                Toast.makeText(context, "Cleared subtitles", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Clear")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrackSelectionDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
private fun GestureIndicator(
    icon: ImageVector,
    label: String,
    percent: Int
) {
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
                .padding(24.dp)
                .width(100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "$percent%",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSecs = ms / 1000
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    val seconds = totalSecs % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

// Subtitle parser, models and built-in demo subtitles
data class SubtitleCue(
    val index: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String
)

private fun parseSrtTime(timeStr: String): Long {
    val clean = timeStr.trim().replace(',', '.')
    val parts = clean.split(":")
    if (parts.size < 3) return 0L
    val hours = parts[0].toLongOrNull() ?: 0L
    val minutes = parts[1].toLongOrNull() ?: 0L
    val secondsParts = parts[2].split(".")
    val seconds = secondsParts[0].toLongOrNull() ?: 0L
    val ms = if (secondsParts.size > 1) {
        val rawMs = secondsParts[1]
        val padded = rawMs.padEnd(3, '0').take(3)
        padded.toLongOrNull() ?: 0L
    } else {
        0L
    }
    return hours * 3600000L + minutes * 60000L + seconds * 1000L + ms
}

private fun parseSrt(content: String): List<SubtitleCue> {
    val cues = mutableListOf<SubtitleCue>()
    val normalized = content.replace("\r\n", "\n").replace("\r", "\n")
    val sections = normalized.split(Regex("\n\\s*\n"))
    for (section in sections) {
        val lines = section.trim().lines().filter { it.isNotBlank() }
        if (lines.size >= 2) {
            val index = lines[0].toIntOrNull()
            val timeLine = lines[1]
            if (timeLine.contains("-->")) {
                val timeParts = timeLine.split("-->")
                if (timeParts.size == 2) {
                    val startMs = parseSrtTime(timeParts[0])
                    val endMs = parseSrtTime(timeParts[1])
                    val text = lines.drop(2).joinToString("\n")
                    if (index != null) {
                        cues.add(SubtitleCue(index, startMs, endMs, text))
                    } else {
                        cues.add(SubtitleCue(cues.size + 1, startMs, endMs, text))
                    }
                }
            }
        }
    }
    return cues.sortedBy { it.startTimeMs }
}

private fun getSampleSubtitles(): List<SubtitleCue> {
    return listOf(
        SubtitleCue(1, 1000L, 5000L, "Welcome to MPlayer's Elegant Subtitle Engine!"),
        SubtitleCue(2, 6000L, 11000L, "This .srt subtitle was loaded and parsed instantly on-the-fly."),
        SubtitleCue(3, 12000L, 17000L, "Our parser tracks playback state with 100ms high-precision sync."),
        SubtitleCue(4, 18000L, 23000L, "Double-tap left or right to seek 10s and watch subtitle synchronization."),
        SubtitleCue(5, 24000L, 29000L, "Swipe on the left side to adjust brightness, and right side for volume."),
        SubtitleCue(6, 30000L, 35000L, "You can load custom .srt files from your phone's storage anytime!"),
        SubtitleCue(7, 36000L, 41000L, "We hope you enjoy this seamless, cinematic media experience.")
    )
}
