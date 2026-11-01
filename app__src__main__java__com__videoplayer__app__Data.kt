package com.videoplayer.app

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.compose.runtime.saveable.Saver
import androidx.core.content.ContextCompat
import java.util.Locale

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val durationMs: Long,
    val size: Long,
    val dateAdded: Long,
    val album: String,
    val height: Int
)

data class PlayRequest(val uri: Uri, val title: String)

/** يحفظ طلب التشغيل الحالي ليبقى بعد إعادة إنشاء النشاط. */
val PlayRequestSaver: Saver<PlayRequest?, List<String>> = Saver(
    save = { r -> r?.let { listOf(it.uri.toString(), it.title) } },
    restore = { l -> PlayRequest(Uri.parse(l[0]), l[1]) }
)

// ---------- الأذونات ----------

fun accessPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun isGranted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/** وصول كامل لكل الفيديوهات. */
fun hasFullAccess(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 33) isGranted(context, Manifest.permission.READ_MEDIA_VIDEO)
    else isGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)

/** وصول كامل أو جزئي (أندرويد 14: "الصور المحددة فقط"). */
fun hasAccess(context: Context): Boolean =
    hasFullAccess(context) ||
        (Build.VERSION.SDK_INT >= 34 &&
            isGranted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))

tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// ---------- قراءة الفيديوهات ----------

fun loadVideos(context: Context): List<VideoItem> {
    val result = mutableListOf<VideoItem>()
    val base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val other = context.getString(R.string.album_other)
    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.DATE_ADDED,
        MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Video.Media.HEIGHT
    )
    runCatching {
        context.contentResolver.query(
            base, projection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val bucketCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val heightCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                result += VideoItem(
                    id = id,
                    uri = ContentUris.withAppendedId(base, id),
                    title = (c.getString(nameCol) ?: "").substringBeforeLast('.'),
                    durationMs = c.getLong(durCol),
                    size = c.getLong(sizeCol),
                    dateAdded = c.getLong(dateCol),
                    album = c.getString(bucketCol) ?: other,
                    height = c.getInt(heightCol)
                )
            }
        }
    }
    return result
}

/** اسم الملف كاملاً مع الامتداد (أو نص فارغ). */
fun queryFullName(context: Context, uri: Uri): String =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty()

fun queryName(context: Context, uri: Uri): String {
    val full = queryFullName(context, uri)
    return if (full.isBlank()) context.getString(R.string.video_default)
    else full.substringBeforeLast('.')
}

// ---------- التنسيق (أرقام لاتينية متسقة في كل الواجهة) ----------

fun formatTime(ms: Long): String {
    val s = ms.coerceAtLeast(0L) / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.US, "%02d:%02d", m, sec)
}

fun formatSize(context: Context, bytes: Long): String {
    val mb = 1024L * 1024L
    val gb = mb * 1024L
    return when {
        bytes >= gb -> context.getString(
            R.string.size_gb, String.format(Locale.US, "%.1f", bytes / gb.toDouble())
        )
        bytes >= mb -> context.getString(R.string.size_mb, (bytes / mb).toString())
        else -> context.getString(R.string.size_kb, (bytes / 1024L).toString())
    }
}

fun videoCountText(context: Context, n: Int): String =
    context.resources.getQuantityString(R.plurals.video_count, n, n.toString())

// ---------- التفضيلات ----------

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    fun favorites(): Set<Long> =
        (sp.getStringSet("fav", emptySet()) ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()

    fun saveFavorites(ids: Set<Long>) {
        sp.edit().putStringSet("fav", ids.map { it.toString() }.toSet()).apply()
    }

    var autoplay: Boolean
        get() = sp.getBoolean("autoplay", true)
        set(value) {
            sp.edit().putBoolean("autoplay", value).apply()
        }

    var autoRotate: Boolean
        get() = sp.getBoolean("autorotate", true)
        set(value) {
            sp.edit().putBoolean("autorotate", value).apply()
        }

    /** آخر موضع تشغيل للفيديو (0 = لا يوجد). */
    fun position(key: String): Long = sp.getLong("pos_$key", 0L)

    fun savePosition(key: String, ms: Long) {
        sp.edit().apply { if (ms > 0L) putLong("pos_$key", ms) else remove("pos_$key") }.apply()
    }
}
