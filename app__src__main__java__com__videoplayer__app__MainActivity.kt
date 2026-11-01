package com.videoplayer.app

import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initial = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data
            ?.let { PlayRequest(it, queryName(this, it)) }
        setContent {
            AppTheme {
                // Surface يضبط لون المحتوى (النص والأيقونات) تلقائياً حسب الوضع الفاتح/الداكن
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        App(initial)
                    }
                }
            }
        }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = Color(0xFF0A84FF),
            background = Color.Black,
            onBackground = Color.White,
            surface = Color(0xFF1C1C1E),
            onSurface = Color.White,
            surfaceVariant = Color(0xFF2C2C2E),
            onSurfaceVariant = Color(0xFF98989F),
            surfaceContainer = Color(0xFF2C2C2E),
            surfaceContainerHigh = Color(0xFF2C2C2E)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF007AFF),
            background = Color(0xFFF2F2F7),
            onBackground = Color.Black,
            surface = Color.White,
            onSurface = Color.Black,
            surfaceVariant = Color(0xFFE9E9EE),
            onSurfaceVariant = Color(0xFF6C6C70),
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color.White
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun App(initial: PlayRequest?) {
    val context = LocalContext.current
    val activity = remember { context.findActivity() }
    val prefs = remember { Prefs(context) }

    var granted by remember { mutableStateOf(hasAccess(context)) }
    var denials by remember { mutableIntStateOf(0) }
    var videos by remember { mutableStateOf(emptyList<VideoItem>()) }
    var loading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var playing by rememberSaveable(stateSaver = PlayRequestSaver) { mutableStateOf(initial) }
    // قائمة الفيديوهات الحالية للتنقل بالسحب العمودي
    var playlist by remember { mutableStateOf(emptyList<PlayRequest>()) }

    // بعد الرفض المتكرر لا يعرض النظام نافذة الإذن، فنوجّه المستخدم للإعدادات
    val blocked = denials > 0 && !granted && activity != null &&
        accessPermissions().none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        granted = hasAccess(context)
        if (!granted) denials++
    }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            playlist = emptyList()
            playing = PlayRequest(uri, queryName(context, uri))
        }
    }

    // يعيد فحص الإذن عند الرجوع من إعدادات النظام
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = hasAccess(context) }

    LaunchedEffect(Unit) {
        if (!granted) permLauncher.launch(accessPermissions())
    }

    // تحديث تلقائي عند إضافة/حذف فيديو
    DisposableEffect(granted) {
        if (!granted) return@DisposableEffect onDispose { }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                refreshKey++
            }
        }
        context.contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    LaunchedEffect(granted, refreshKey) {
        if (!granted) {
            loading = false
            return@LaunchedEffect
        }
        if (refreshKey > 0) delay(400) // تجميع الإشعارات المتتالية
        val list = withContext(Dispatchers.IO) { loadVideos(context) }
        videos = list
        loading = false
        // تنظيف مفضلات الفيديوهات المحذوفة (فقط عند الوصول الكامل)
        if (list.isNotEmpty() && hasFullAccess(context)) {
            val ids = list.map { it.id }.toSet()
            val favs = prefs.favorites()
            if (!ids.containsAll(favs)) prefs.saveFavorites(favs intersect ids)
        }
    }

    // المكتبة تبقى مركّبة تحت المشغّل حتى تُحفظ حالة التمرير والتبويب والفلتر عند الرجوع
    Box(Modifier.fillMaxSize()) {
        LibraryScreen(
            videos = videos,
            loading = loading,
            prefs = prefs,
            granted = granted,
            blocked = blocked,
            onGrant = {
                val canAsk = denials == 0 || activity == null || !blocked
                if (canAsk) {
                    permLauncher.launch(accessPermissions())
                } else {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                    )
                }
            },
            onPlay = { request, list ->
                playlist = list
                playing = request
            },
            onOpenFile = { openLauncher.launch(arrayOf("video/*")) },
            onRefresh = { refreshKey++ }
        )
        val current = playing
        if (current != null) {
            BackHandler { playing = null }
            // يمنع وصول اللمس إلى المكتبة التي تحته
            Box(Modifier.fillMaxSize().pointerInput(Unit) { }) {
                PlayerScreen(
                    req = current,
                    prefs = prefs,
                    playlist = playlist,
                    onChange = { playing = it },
                    onBack = { playing = null }
                )
            }
        }
    }
}
