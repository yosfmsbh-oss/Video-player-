package com.videoplayer.app

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast

/** نتيجة عملية على ملف فيديو (حذف / إعادة تسمية). */
sealed interface MediaOp {
    data object Success : MediaOp

    /** يحتاج موافقة المستخدم عبر نافذة النظام. */
    data class Confirm(val sender: IntentSender) : MediaOp

    data object Failure : MediaOp
}

/** عملية بانتظار موافقة النظام. */
sealed interface PendingOp {
    val item: VideoItem

    data class Delete(override val item: VideoItem) : PendingOp
    data class Rename(override val item: VideoItem, val newTitle: String) : PendingOp
}

fun shareVideo(context: Context, item: VideoItem) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "video/*"
        putExtra(Intent.EXTRA_STREAM, item.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share)))
    }.onFailure {
        Toast.makeText(context, R.string.toast_failed, Toast.LENGTH_SHORT).show()
    }
}

/** أندرويد 11+: نافذة حذف من النظام. أندرويد 10: حذف مباشر أو طلب إذن. */
fun requestDelete(context: Context, uri: Uri): MediaOp {
    val resolver = context.contentResolver
    if (Build.VERSION.SDK_INT >= 30) {
        return runCatching {
            MediaOp.Confirm(MediaStore.createDeleteRequest(resolver, listOf(uri)).intentSender)
        }.getOrDefault(MediaOp.Failure)
    }
    return try {
        if (resolver.delete(uri, null, null) > 0) MediaOp.Success else MediaOp.Failure
    } catch (e: SecurityException) {
        val rse = e as? RecoverableSecurityException
        if (rse != null) MediaOp.Confirm(rse.userAction.actionIntent.intentSender) else MediaOp.Failure
    } catch (e: Exception) {
        MediaOp.Failure
    }
}

/** يزيل الأحرف غير المسموحة في أسماء الملفات. */
fun sanitizeFileName(name: String): String =
    name.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_").trim().trim('.')

/** إعادة التسمية مع الحفاظ على الامتداد. */
fun requestRename(context: Context, item: VideoItem, newTitle: String): MediaOp {
    val clean = sanitizeFileName(newTitle)
    if (clean.isEmpty()) return MediaOp.Failure
    val ext = queryFullName(context, item.uri).substringAfterLast('.', "")
    val fileName = if (ext.isNotEmpty()) "$clean.$ext" else clean
    val values = ContentValues().apply { put(MediaStore.Video.Media.DISPLAY_NAME, fileName) }
    val resolver = context.contentResolver
    return try {
        if (resolver.update(item.uri, values, null, null) > 0) MediaOp.Success else MediaOp.Failure
    } catch (e: SecurityException) {
        when {
            Build.VERSION.SDK_INT >= 30 -> runCatching {
                MediaOp.Confirm(MediaStore.createWriteRequest(resolver, listOf(item.uri)).intentSender)
            }.getOrDefault(MediaOp.Failure)
            e is RecoverableSecurityException -> MediaOp.Confirm(e.userAction.actionIntent.intentSender)
            else -> MediaOp.Failure
        }
    } catch (e: Exception) {
        MediaOp.Failure
    }
}
