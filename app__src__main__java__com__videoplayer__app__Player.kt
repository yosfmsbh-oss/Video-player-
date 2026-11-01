package com.videoplayer.app

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

fun Modifier.glass(shape: Shape, alpha: Float = 0.18f): Modifier =
    this.clip(shape)
        .background(Color.White.copy(alpha = alpha))
        .border(0.5.dp, Color.White.copy(alpha = 0.3f), shape)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassButton(
    icon: ImageVector,
    description: String,
    size: Dp,
    iconSize: Dp,
    alpha: Float = 0.18f,
    tint: Color = Color.White,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Box(
        Modifier.size(size).glass(CircleShape, alpha)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun ActionItem(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    showLabel: Boolean = true,
    onClick: () -> Unit
) {
    val tint = if (active) Color(0xFF64D2FF) else Color.White
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        if (showLabel) Text(label, fontSize = 11.sp, color = tint)
    }
}

private class AudioOption(val label: String, val group: Tracks.Group)

private enum class Axis { Horizontal, Vertical }

private fun speedLabel(s: Float): String =
    if (s == s.toInt().toFloat()) "${s.toInt()}x" else "${s}x"

private const val PIP_ACTION = "com.videoplayer.app.PIP_CONTROL"
private const val PIP_REWIND = 1
private const val PIP_TOGGLE = 2
private const val PIP_FORWARD = 3

/** أزرار التحكم التي تظهر داخل النافذة المصغّرة: رجوع 10 ث، تشغيل/إيقاف، تقديم 10 ث. */
private fun pipActions(context: Context, playing: Boolean): List<RemoteAction> {
    fun action(icon: Int, title: String, code: Int): RemoteAction {
        val pending = PendingIntent.getBroadcast(
            context, code,
            Intent(PIP_ACTION).setPackage(context.packageName).putExtra("code", code),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return RemoteAction(
            android.graphics.drawable.Icon.createWithResource(context, icon), title, title, pending
        )
    }
    return listOf(
        action(android.R.drawable.ic_media_rew, context.getString(R.string.back10), PIP_REWIND),
        action(
            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            context.getString(if (playing) R.string.pause else R.string.play),
            PIP_TOGGLE
        ),
        action(android.R.drawable.ic_media_ff, context.getString(R.string.fwd10), PIP_FORWARD)
    )
}

private fun pipParams(context: Context, exo: ExoPlayer, playing: Boolean): PictureInPictureParams {
    val size = exo.videoSize
    val builder = PictureInPictureParams.Builder().setActions(pipActions(context, playing))
    if (size.width > 0 && size.height > 0) {
        val ratio = size.width.toFloat() / size.height
        if (ratio in 0.45f..2.3f) builder.setAspectRatio(Rational(size.width, size.height))
    }
    return builder.build()
}

private fun enterPip(activity: Activity, exo: ExoPlayer): Boolean =
    runCatching {
        activity.enterPictureInPictureMode(pipParams(activity, exo, exo.isPlaying))
    }.getOrDefault(false)

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    req: PlayRequest,
    prefs: Prefs,
    playlist: List<PlayRequest>,
    onChange: (PlayRequest) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember { context.findActivity() }
    val haptic = LocalHapticFeedback.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val key = req.uri.toString()
    val autoplay = remember { prefs.autoplay }
    // معرّف الفيديو في المكتبة (غير متاح للملفات المفتوحة من خارج المكتبة)
    val mediaId = remember(req.uri) {
        runCatching {
            if (req.uri.authority == MediaStore.AUTHORITY) ContentUris.parseId(req.uri) else null
        }.getOrNull()
    }
    val isFav = mediaId != null && mediaId in prefs.favs

    val exo = remember(key) {
        ExoPlayer.Builder(context)
            // تركيز الصوت: يخفض/يوقف تطبيقات الموسيقى الأخرى ويتوقف عند المكالمات
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            // يتوقف عند فصل السماعات
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                setMediaItem(MediaItem.fromUri(req.uri), prefs.position(key))
                prepare()
                playWhenReady = autoplay
            }
    }

    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    )
    val resizeNames = listOf(
        stringResource(R.string.resize_fit),
        stringResource(R.string.resize_stretch),
        stringResource(R.string.resize_zoom)
    )

    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var speedIndex by remember { mutableIntStateOf(2) }
    var resizeIndex by remember { mutableIntStateOf(0) }
    var captions by remember { mutableStateOf(false) }
    var audioOptions by remember { mutableStateOf(emptyList<AudioOption>()) }
    var locked by remember { mutableStateOf(false) }
    var lockHint by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }
    var touch by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var bgPlay by remember { mutableStateOf(prefs.backgroundPlay) }
    var vOffset by remember { mutableFloatStateOf(0f) }
    var vDragging by remember { mutableStateOf(false) }
    val vAnim by animateFloatAsState(
        targetValue = vOffset,
        animationSpec = if (vDragging) snap() else spring(),
        label = "videoOffset"
    )
    var openMenu by remember { mutableIntStateOf(0) } // 0 لا شيء، 1 سرعة، 2 ترجمة/صوت، 3 حجم العرض
    var inPip by remember { mutableStateOf(activity?.isInPictureInPictureMode == true) }
    var pipRequested by remember { mutableStateOf(false) }
    var hud by remember { mutableStateOf<String?>(null) }
    var hudAlign by remember { mutableStateOf(Alignment.Center) }
    var hudTick by remember { mutableIntStateOf(0) }
    var brightness by remember { mutableFloatStateOf(0.5f) }
    var volume by remember { mutableFloatStateOf(0.5f) }

    val pipSupported = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    fun showHud(text: String, align: Alignment) {
        hud = text
        hudAlign = align
        hudTick++
    }

    fun toggleBackground() {
        bgPlay = !bgPlay
        prefs.backgroundPlay = bgPlay
        if (bgPlay && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        showHud(context.getString(if (bgPlay) R.string.bg_on else R.string.bg_off), Alignment.Center)
    }

    fun savePosition() {
        val d = exo.duration
        val p = exo.currentPosition
        prefs.savePosition(key, if (d > 0 && p > 5_000 && p < d - 5_000) p else 0L)
    }

    fun seekBy(ms: Long) {
        val d = exo.duration
        val target = exo.currentPosition + ms
        exo.seekTo(if (d > 0) target.coerceIn(0L, d) else target.coerceAtLeast(0L))
        touch++
    }

    /** تغيير الفيديو: +1 التالي، -1 السابق (حسب قائمة المكتبة الحالية). */
    fun goTo(delta: Int) {
        val idx = playlist.indexOfFirst { it.uri == req.uri }
        if (idx < 0) return
        val target = idx + delta
        if (target !in playlist.indices) {
            showHud(
                context.getString(if (delta > 0) R.string.hud_last else R.string.hud_first),
                Alignment.Center
            )
            return
        }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        exo.pause()
        onChange(playlist[target])
        showHud(playlist[target].title, Alignment.Center)
    }

    fun currentBrightness(): Float {
        val w = activity?.window?.attributes?.screenBrightness ?: -1f
        if (w >= 0f) return w
        return runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        }.getOrDefault(0.5f)
    }

    fun setTextEnabled(enabled: Boolean) {
        captions = enabled
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
            .build()
    }

    val subtitleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = queryFullName(context, uri).lowercase()
            val mime = when {
                name.endsWith(".vtt") -> MimeTypes.TEXT_VTT
                name.endsWith(".ass") || name.endsWith(".ssa") -> MimeTypes.TEXT_SSA
                else -> MimeTypes.APPLICATION_SUBRIP
            }
            val sub = MediaItem.SubtitleConfiguration.Builder(uri)
                .setMimeType(mime)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            val item = MediaItem.Builder().setUri(req.uri).setSubtitleConfigurations(listOf(sub)).build()
            val pos = exo.currentPosition
            val play = exo.playWhenReady
            exo.setMediaItem(item, pos)
            exo.prepare()
            exo.playWhenReady = play
            setTextEnabled(true)
        }
    }

    // عند تغيير الفيديو: نعيد ضبط الحالة المرتبطة بالمشغّل القديم
    LaunchedEffect(exo) {
        error = null
        buffering = true
        position = 0L
        duration = 0L
        captions = false
        exo.setPlaybackSpeed(speeds[speedIndex])
    }

    // ---- أزرار التحكم في النافذة المصغّرة ----
    DisposableEffect(exo) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.getIntExtra("code", 0)) {
                    PIP_REWIND -> exo.seekTo((exo.currentPosition - 10_000L).coerceAtLeast(0L))
                    PIP_TOGGLE -> if (exo.isPlaying) exo.pause() else Util.handlePlayButtonAction(exo)
                    PIP_FORWARD -> {
                        val d = exo.duration
                        val t = exo.currentPosition + 10_000L
                        exo.seekTo(if (d > 0) t.coerceAtMost(d) else t)
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(PIP_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    // ---- دورة حياة المشغّل ----
    DisposableEffect(exo) {
        onDispose {
            savePosition()
            PlaybackService.release(context)
            exo.release()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        savePosition()
        if (!inPip && !pipRequested) {
            // التشغيل في الخلفية: نُبقي الصوت يعمل عبر خدمة أمامية بدل الإيقاف
            if (bgPlay && exo.isPlaying) PlaybackService.start(context, exo, req.title) else exo.pause()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isInPictureInPictureMode != true) {
            if (bgPlay && exo.isPlaying) PlaybackService.start(context, exo, req.title) else exo.pause()
        }
    }
    // عند الرجوع للتطبيق تُزال خدمة الخلفية ويكمل التشغيل داخل الواجهة
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        PlaybackService.stop(context)
    }

    // ---- صورة داخل صورة ----
    DisposableEffect(activity, exo) {
        val a = activity ?: return@DisposableEffect onDispose { }
        val pipListener = Consumer<PictureInPictureModeChangedInfo> {
            inPip = it.isInPictureInPictureMode
            if (!it.isInPictureInPictureMode) pipRequested = false
            if (it.isInPictureInPictureMode) controls = false
        }
        val leaveListener = Runnable {
            if (exo.isPlaying && !locked) pipRequested = enterPip(a, exo)
        }
        a.addOnPictureInPictureModeChangedListener(pipListener)
        a.addOnUserLeaveHintListener(leaveListener)
        onDispose {
            a.removeOnPictureInPictureModeChangedListener(pipListener)
            a.removeOnUserLeaveHintListener(leaveListener)
        }
    }

    // ---- النافذة: إخفاء أشرطة النظام، التعامل مع الحاجز (notch)، واستعادة كل شيء ----
    DisposableEffect(Unit) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        var oldCutout = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        if (window != null) {
            val lp = window.attributes
            oldCutout = lp.layoutInDisplayCutoutMode
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (window != null) {
                val lp = window.attributes
                lp.layoutInDisplayCutoutMode = oldCutout
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = lp
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // ---- مستمع المشغّل ----
    DisposableEffect(exo) {
        var rotated = false
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                // تحديث زر التشغيل/الإيقاف داخل النافذة المصغّرة
                activity?.let { a ->
                    if (a.isInPictureInPictureMode) {
                        runCatching { a.setPictureInPictureParams(pipParams(context, exo, playing)) }
                    }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                duration = exo.duration.coerceAtLeast(0L)
                if (state == Player.STATE_ENDED) controls = true
            }

            override fun onPlayerError(e: PlaybackException) {
                buffering = false
                error = context.getString(R.string.error_playback) + "\n" + e.errorCodeName
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (!rotated && videoSize.width > videoSize.height && prefs.autoRotate) {
                    rotated = true
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                audioOptions = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
                    .mapIndexed { i, g ->
                        val f = g.getTrackFormat(0)
                        val lang = f.language?.takeIf { it != "und" }
                            ?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.forLanguageTag("ar")) }
                            ?.takeIf { it.isNotBlank() }
                        AudioOption(f.label ?: lang ?: context.getString(R.string.audio_track_n, i + 1), g)
                    }
            }
        }
        exo.addListener(listener)
        onDispose { exo.removeListener(listener) }
    }

    // يحدّث الموضع فقط عندما تكون عناصر التحكم ظاهرة (توفير للبطارية)
    LaunchedEffect(exo, controls, inPip) {
        position = exo.currentPosition
        duration = exo.duration.coerceAtLeast(0L)
        while (controls && !inPip) {
            delay(250)
            position = exo.currentPosition
            duration = exo.duration.coerceAtLeast(0L)
        }
    }
    LaunchedEffect(controls, isPlaying, touch, locked, openMenu) {
        if (controls && isPlaying && !locked && openMenu == 0) {
            delay(3500)
            controls = false
        }
    }
    LaunchedEffect(lockHint, touch) {
        if (lockHint) {
            delay(2500)
            lockHint = false
        }
    }
    LaunchedEffect(hudTick) {
        if (hud != null) {
            delay(700)
            hud = null
        }
    }

    BackHandler(locked) { lockHint = true }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    keepScreenOn = true
                }
            },
            update = {
                it.player = exo
                it.resizeMode = resizeModes[resizeIndex]
            },
            modifier = Modifier.fillMaxSize().graphicsLayer { translationY = vAnim }
        )

        // ---- طبقة الإيماءات ----
        Box(
            Modifier.fillMaxSize()
                .pointerInput(locked, inPip, exo) {
                    detectTapGestures(
                        onTap = {
                            if (!inPip) {
                                if (locked) lockHint = true else controls = !controls
                            }
                        },
                        onDoubleTap = { o ->
                            if (!locked && !inPip) {
                                if (o.x < size.width / 2) {
                                    seekBy(-10_000L)
                                    showHud(context.getString(R.string.hud_back), Alignment.CenterStart)
                                } else {
                                    seekBy(10_000L)
                                    showHud(context.getString(R.string.hud_fwd), Alignment.CenterEnd)
                                }
                            }
                        }
                    )
                }
                .pointerInput(locked, inPip, exo, playlist) {
                    if (!locked && !inPip) {
                        var axis: Axis? = null
                        var startLeft = true
                        var startCenter = false
                        var totalY = 0f
                        var seekTarget = 0L
                        detectDragGestures(
                            onDragStart = { o ->
                                axis = null
                                startLeft = o.x < size.width / 2f
                                // الثلث الأوسط: السحب العمودي يغيّر الفيديو (الأطراف: سطوع/صوت)
                                startCenter = o.x >= size.width * 0.3f && o.x <= size.width * 0.7f
                                totalY = 0f
                                seekTarget = exo.currentPosition
                                brightness = currentBrightness()
                                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                if (axis == null) {
                                    axis = if (abs(drag.x) > abs(drag.y)) Axis.Horizontal else Axis.Vertical
                                }
                                if (axis == Axis.Horizontal) {
                                    val d = exo.duration
                                    if (d > 0) {
                                        seekTarget = (seekTarget + (drag.x / size.width * 120_000f).toLong())
                                            .coerceIn(0L, d)
                                        showHud("${formatTime(seekTarget)} / ${formatTime(d)}", Alignment.Center)
                                    }
                                } else if (startCenter) {
                                    totalY += drag.y
                                    vDragging = true
                                    val limit = size.height * 0.25f
                                    vOffset = (totalY * 0.5f).coerceIn(-limit, limit)
                                } else {
                                    val delta = -drag.y / size.height * 1.2f
                                    if (startLeft) {
                                        brightness = (brightness + delta).coerceIn(0.02f, 1f)
                                        activity?.window?.let { w ->
                                            val lp = w.attributes
                                            lp.screenBrightness = brightness
                                            w.attributes = lp
                                        }
                                        showHud(
                                            context.getString(R.string.hud_brightness, (brightness * 100).roundToInt()),
                                            Alignment.CenterStart
                                        )
                                    } else {
                                        volume = (volume + delta).coerceIn(0f, 1f)
                                        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        audioManager.setStreamVolume(
                                            AudioManager.STREAM_MUSIC, (volume * max).roundToInt(), 0
                                        )
                                        showHud(
                                            context.getString(R.string.hud_volume, (volume * 100).roundToInt()),
                                            Alignment.CenterEnd
                                        )
                                    }
                                }
                            },
                            onDragEnd = {
                                if (axis == Axis.Horizontal) exo.seekTo(seekTarget)
                                if (axis == Axis.Vertical && startCenter) {
                                    vDragging = false
                                    vOffset = 0f
                                    val threshold = size.height * 0.12f
                                    if (totalY < -threshold) goTo(1) else if (totalY > threshold) goTo(-1)
                                }
                                axis = null
                            },
                            onDragCancel = {
                                vDragging = false
                                vOffset = 0f
                                axis = null
                            }
                        )
                    }
                }
        )

        // ---- مؤشر التحميل ----
        if (buffering && error == null && !inPip && !(controls && !locked)) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
        }

        // ---- لوحة الأخطاء ----
        error?.let { message ->
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(message, color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.size(16.dp))
                Button(onClick = {
                    error = null
                    buffering = true
                    exo.prepare()
                    exo.play()
                }) { Text(stringResource(R.string.retry)) }
            }
            Box(Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.displayCutout).padding(16.dp)) {
                GlassButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), 48.dp, 22.dp, onClick = onBack)
            }
        }

        // ---- عناصر التحكم ----
        AnimatedVisibility(
            visible = controls && !locked && !inPip && error == null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                Modifier.fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.65f),
                                Color.Black.copy(alpha = 0.15f),
                                Color.Black.copy(alpha = 0.15f),
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
                    .windowInsetsPadding(WindowInsets.displayCutout)
            ) {
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), 48.dp, 22.dp, onClick = onBack)
                    Text(
                        req.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassButton(
                            Icons.Rounded.Headphones,
                            stringResource(R.string.bg_play),
                            44.dp, 20.dp,
                            tint = if (bgPlay) Color(0xFF64D2FF) else Color.White
                        ) {
                            toggleBackground()
                            touch++
                        }
                        if (pipSupported) {
                            GlassButton(Icons.Rounded.PictureInPictureAlt, stringResource(R.string.pip), 44.dp, 20.dp) {
                                activity?.let { pipRequested = enterPip(it, exo) }
                            }
                        }
                        if (mediaId != null) {
                            GlassButton(
                                if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                stringResource(if (isFav) R.string.fav_remove else R.string.fav_add),
                                44.dp, 20.dp,
                                tint = if (isFav) Color(0xFFFF3B30) else Color.White
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                prefs.toggleFavorite(mediaId)
                                touch++
                            }
                        }
                    }
                }

                // أزرار التشغيل واتجاه الخط الزمني دائماً من اليسار لليمين (متسق مع النقر المزدوج)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        Modifier.align(Alignment.Center).padding(bottom = if (landscape) 40.dp else 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassButton(Icons.Rounded.Replay10, stringResource(R.string.back10), 52.dp, 26.dp) { seekBy(-10_000L) }
                        GlassButton(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            stringResource(if (isPlaying) R.string.pause else R.string.play),
                            72.dp, 36.dp, alpha = 0.28f
                        ) {
                            if (exo.isPlaying) exo.pause() else Util.handlePlayButtonAction(exo)
                            touch++
                        }
                        GlassButton(Icons.Rounded.Forward10, stringResource(R.string.fwd10), 52.dp, 26.dp) { seekBy(10_000L) }
                    }
                }

                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .navigationBarsPadding().padding(14.dp)
                        .glass(RoundedCornerShape(28.dp), 0.16f)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        val progress = if (dragging) dragValue
                        else if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                formatTime(if (dragging) (dragValue * duration).toLong() else position),
                                color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp
                            )
                            Slider(
                                value = progress,
                                onValueChange = {
                                    dragging = true
                                    dragValue = it
                                    touch++
                                },
                                onValueChangeFinished = {
                                    exo.seekTo((dragValue * duration).toLong())
                                    dragging = false
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            Text(formatTime(duration), color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        // السرعة
                        Box {
                            ActionItem(Icons.Rounded.Speed, speedLabel(speeds[speedIndex])) { openMenu = 1 }
                            DropdownMenu(expanded = openMenu == 1, onDismissRequest = { openMenu = 0 }) {
                                speeds.forEachIndexed { i, s ->
                                    DropdownMenuItem(
                                        text = { Text(speedLabel(s)) },
                                        trailingIcon = { if (i == speedIndex) Icon(Icons.Rounded.Check, null) },
                                        onClick = {
                                            speedIndex = i
                                            exo.setPlaybackSpeed(s)
                                            openMenu = 0
                                            touch++
                                        }
                                    )
                                }
                            }
                        }
                        // الترجمة ومسار الصوت
                        Box {
                            ActionItem(Icons.Rounded.ClosedCaption, stringResource(R.string.captions), active = captions, showLabel = !landscape) {
                                openMenu = 2
                            }
                            DropdownMenu(expanded = openMenu == 2, onDismissRequest = { openMenu = 0 }) {
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(if (captions) R.string.captions_off else R.string.captions_on))
                                    },
                                    onClick = {
                                        setTextEnabled(!captions)
                                        openMenu = 0
                                        touch++
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.captions_load)) },
                                    onClick = {
                                        openMenu = 0
                                        subtitleLauncher.launch(arrayOf("*/*"))
                                    }
                                )
                                if (audioOptions.size > 1) {
                                    HorizontalDivider()
                                    Text(
                                        stringResource(R.string.audio_header),
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                    audioOptions.forEach { opt ->
                                        DropdownMenuItem(
                                            text = { Text(opt.label) },
                                            trailingIcon = { if (opt.group.isSelected) Icon(Icons.Rounded.Check, null) },
                                            onClick = {
                                                exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                                                    .setOverrideForType(TrackSelectionOverride(opt.group.mediaTrackGroup, 0))
                                                    .build()
                                                openMenu = 0
                                                touch++
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        // حجم العرض
                        Box {
                            ActionItem(Icons.Rounded.AspectRatio, resizeNames[resizeIndex], showLabel = !landscape) { openMenu = 3 }
                            DropdownMenu(expanded = openMenu == 3, onDismissRequest = { openMenu = 0 }) {
                                resizeNames.forEachIndexed { i, name ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        trailingIcon = { if (i == resizeIndex) Icon(Icons.Rounded.Check, null) },
                                        onClick = {
                                            resizeIndex = i
                                            openMenu = 0
                                            touch++
                                        }
                                    )
                                }
                            }
                        }
                        // التدوير
                        ActionItem(Icons.Rounded.ScreenRotation, stringResource(R.string.rotate), showLabel = !landscape) {
                            activity?.requestedOrientation =
                                if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            touch++
                        }
                        // القفل
                        ActionItem(Icons.Rounded.Lock, stringResource(R.string.lock), showLabel = !landscape) {
                            locked = true
                            controls = false
                            lockHint = true
                        }
                    }
                }
            }
        }

        // ---- مؤشرات الإيماءات (سطوع/صوت/تقديم) ----
        hud?.let { text ->
            Box(Modifier.fillMaxSize().padding(horizontal = 48.dp), contentAlignment = hudAlign) {
                Text(
                    text,
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        // ---- القفل: الزر يظهر عند اللمس ويُفتح بالضغط المطول ----
        if (locked && lockHint && !inPip) {
            Row(
                Modifier.align(Alignment.BottomStart).navigationBarsPadding()
                    .windowInsetsPadding(WindowInsets.displayCutout).padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassButton(
                    Icons.Rounded.LockOpen, stringResource(R.string.unlock), 56.dp, 26.dp, alpha = 0.3f,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        locked = false
                        lockHint = false
                        controls = true
                    }
                ) { touch++ }
                Spacer(Modifier.size(12.dp))
                Text(
                    stringResource(R.string.unlock_hint),
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
