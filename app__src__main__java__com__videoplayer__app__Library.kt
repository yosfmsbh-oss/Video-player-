package com.videoplayer.app

import android.app.Activity
import android.graphics.Bitmap
import android.os.Build
import android.util.LruCache
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AlbumInfo(val name: String, val count: Int, val latest: Long, val size: Long, val cover: VideoItem)

@Composable
fun LibraryScreen(
    videos: List<VideoItem>,
    loading: Boolean,
    prefs: Prefs,
    granted: Boolean,
    blocked: Boolean,
    onGrant: () -> Unit,
    onPlay: (PlayRequest, List<PlayRequest>) -> Unit,
    onOpenFile: () -> Unit,
    onRefresh: () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var filter by rememberSaveable { mutableStateOf(0) }
    var sort by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var album by rememberSaveable { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    val favs = prefs.favs
    val context = LocalContext.current
    var actionItem by remember { mutableStateOf<VideoItem?>(null) }
    var renameItem by remember { mutableStateOf<VideoItem?>(null) }
    var deleteItem by remember { mutableStateOf<VideoItem?>(null) }
    var pending by remember { mutableStateOf<PendingOp?>(null) }
    var approvedOp by remember { mutableStateOf<PendingOp?>(null) }
    var autoplay by remember { mutableStateOf(prefs.autoplay) }
    var autoRotate by remember { mutableStateOf(prefs.autoRotate) }
    var bgPlay by remember { mutableStateOf(prefs.backgroundPlay) }
    val focus = LocalFocusManager.current
    val sortNames = listOf(
        stringResource(R.string.sort_date),
        stringResource(R.string.sort_name),
        stringResource(R.string.sort_size)
    )

    val opLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val op = pending
        pending = null
        if (op != null && result.resultCode == Activity.RESULT_OK) approvedOp = op
    }

    fun execute(op: PendingOp, afterGrant: Boolean) {
        val result = when (op) {
            is PendingOp.Delete ->
                // أندرويد 11+: النظام نفّذ الحذف بنفسه بعد الموافقة
                if (afterGrant && Build.VERSION.SDK_INT >= 30) MediaOp.Success
                else requestDelete(context, op.item.uri)
            is PendingOp.Rename -> requestRename(context, op.item, op.newTitle)
        }
        when (result) {
            MediaOp.Success -> {
                if (op is PendingOp.Delete) {
                    prefs.saveFavorites(prefs.favs - op.item.id)
                    prefs.savePosition(op.item.uri.toString(), 0L)
                }
                val msg = if (op is PendingOp.Delete) R.string.toast_deleted else R.string.toast_renamed
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                onRefresh()
            }
            is MediaOp.Confirm ->
                if (afterGrant) {
                    Toast.makeText(context, R.string.toast_failed, Toast.LENGTH_SHORT).show()
                } else {
                    pending = op
                    opLauncher.launch(IntentSenderRequest.Builder(result.sender).build())
                }
            MediaOp.Failure ->
                Toast.makeText(context, R.string.toast_failed, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(approvedOp) {
        approvedOp?.let {
            approvedOp = null
            execute(it, afterGrant = true)
        }
    }

    BackHandler(album != null) { album = null }

    val visible = remember(videos, tab, album, filter, sort, query, favs) {
        val weekAgo = System.currentTimeMillis() / 1000 - 7 * 86400
        var list = if (tab == 1 && album != null) videos.filter { it.album == album } else videos
        if (tab == 0) {
            list = when (filter) {
                1 -> list.filter { it.dateAdded >= weekAgo }
                2 -> list.filter { it.id in favs }
                else -> list
            }
        }
        if (query.isNotBlank()) list = list.filter { it.title.contains(query, ignoreCase = true) }
        when (sort) {
            1 -> list.sortedBy { it.title.lowercase() }
            2 -> list.sortedByDescending { it.size }
            else -> list.sortedByDescending { it.dateAdded }
        }
    }
    val albums = remember(videos, query, sort) {
        videos.groupBy { it.album }
            .filter { query.isBlank() || it.key.contains(query, ignoreCase = true) }
            .map { (name, list) ->
                val cover = list.maxByOrNull { v -> v.dateAdded } ?: list.first()
                AlbumInfo(name, list.size, cover.dateAdded, list.sumOf { v -> v.size }, cover)
            }
            .let { list ->
                when (sort) {
                    1 -> list.sortedBy { it.name.lowercase() }
                    2 -> list.sortedByDescending { it.size }
                    else -> list.sortedByDescending { it.latest }
                }
            }
    }

    val titleText = when {
        tab == 0 -> stringResource(R.string.title_home)
        album != null -> album.orEmpty()
        else -> stringResource(R.string.title_albums)
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    titleText,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { menu = true },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.MoreHoriz, contentDescription = stringResource(R.string.menu_more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_open)) },
                            trailingIcon = { Icon(Icons.Rounded.FolderOpen, null) },
                            onClick = { menu = false; onOpenFile() }
                        )
                        HorizontalDivider()
                        Text(
                            stringResource(R.string.menu_sort),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                        sortNames.forEachIndexed { i, name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                trailingIcon = { if (sort == i) Icon(Icons.Rounded.Check, null) },
                                onClick = { sort = i; menu = false }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_refresh)) },
                            trailingIcon = { Icon(Icons.Rounded.Refresh, null) },
                            onClick = { menu = false; onRefresh() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_settings)) },
                            trailingIcon = { Icon(Icons.Rounded.Settings, null) },
                            onClick = { menu = false; settings = true }
                        )
                    }
                }
            }

            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.clear))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            if (tab == 0) {
                Segmented(
                    listOf(
                        stringResource(R.string.filter_all),
                        stringResource(R.string.filter_recent),
                        stringResource(R.string.filter_favs)
                    ),
                    filter
                ) { filter = it }
            } else {
                Spacer(Modifier.size(8.dp))
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    !granted -> PermissionPrompt(blocked, onGrant)
                    loading && videos.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    tab == 1 && album == null -> AlbumList(albums) { album = it }
                    else -> VideoList(
                        items = visible,
                        favs = favs,
                        onPlay = { v ->
                            onPlay(
                                PlayRequest(v.uri, v.title),
                                visible.map { PlayRequest(it.uri, it.title) }
                            )
                        },
                        onFav = { v -> prefs.toggleFavorite(v.id) },
                        onLongPress = { actionItem = it }
                    )
                }
            }
        }

        Row(
            Modifier.align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                .border(0.5.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(32.dp))
                .padding(6.dp)
        ) {
            TabItem(tab == 0, Icons.Rounded.Home, stringResource(R.string.tab_home)) {
                tab = 0; album = null; query = ""
            }
            TabItem(tab == 1, Icons.Rounded.PhotoLibrary, stringResource(R.string.tab_albums)) {
                tab = 1; album = null; query = ""
            }
        }
    }

    actionItem?.let { v ->
        AlertDialog(
            onDismissRequest = { actionItem = null },
            confirmButton = {
                TextButton(onClick = { actionItem = null }) { Text(stringResource(R.string.cancel)) }
            },
            title = { Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    ActionRow(Icons.Rounded.Share, stringResource(R.string.share)) {
                        actionItem = null
                        shareVideo(context, v)
                    }
                    ActionRow(Icons.Rounded.Edit, stringResource(R.string.rename)) {
                        actionItem = null
                        renameItem = v
                    }
                    ActionRow(
                        Icons.Rounded.Delete,
                        stringResource(R.string.delete),
                        MaterialTheme.colorScheme.error
                    ) {
                        actionItem = null
                        // أندرويد 11+ يعرض نافذة تأكيد من النظام، فلا نكرر السؤال
                        if (Build.VERSION.SDK_INT >= 30) execute(PendingOp.Delete(v), afterGrant = false)
                        else deleteItem = v
                    }
                }
            }
        )
    }

    renameItem?.let { v ->
        var text by remember(v.id) { mutableStateOf(v.title) }
        AlertDialog(
            onDismissRequest = { renameItem = null },
            title = { Text(stringResource(R.string.rename)) },
            text = {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.rename_hint)) }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = sanitizeFileName(text).isNotEmpty(),
                    onClick = {
                        renameItem = null
                        val clean = sanitizeFileName(text)
                        if (clean != v.title) execute(PendingOp.Rename(v, clean), afterGrant = false)
                    }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameItem = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    deleteItem?.let { v ->
        AlertDialog(
            onDismissRequest = { deleteItem = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_message, v.title)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteItem = null
                    execute(PendingOp.Delete(v), afterGrant = false)
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteItem = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (settings) {
        AlertDialog(
            onDismissRequest = { settings = false },
            confirmButton = { TextButton(onClick = { settings = false }) { Text(stringResource(R.string.done)) } },
            title = { Text(stringResource(R.string.menu_settings)) },
            text = {
                Column {
                    SwitchRow(stringResource(R.string.settings_autoplay), autoplay) {
                        autoplay = it
                        prefs.autoplay = it
                    }
                    SwitchRow(stringResource(R.string.settings_autorotate), autoRotate) {
                        autoRotate = it
                        prefs.autoRotate = it
                    }
                    SwitchRow(stringResource(R.string.settings_background), bgPlay) {
                        bgPlay = it
                        prefs.backgroundPlay = it
                    }
                }
            }
        )
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    color: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    val tint = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Text(label, color = tint, fontSize = 16.sp)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PermissionPrompt(blocked: Boolean, onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(if (blocked) R.string.perm_blocked else R.string.perm_needed),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.size(12.dp))
        Button(onClick = onGrant) {
            Text(stringResource(if (blocked) R.string.perm_open_settings else R.string.perm_allow))
        }
    }
}

@Composable
fun Segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(2.dp)
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TabItem(selected: Boolean, icon: ImageVector, label: String, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.width(100.dp).clip(RoundedCornerShape(26.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Text(label, fontSize = 11.sp, color = color)
    }
}

@Composable
private fun listBottomPadding(): androidx.compose.ui.unit.Dp =
    96.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoList(
    items: List<VideoItem>,
    favs: Set<Long>,
    onPlay: (VideoItem) -> Unit,
    onFav: (VideoItem) -> Unit,
    onLongPress: (VideoItem) -> Unit
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.empty_videos), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = listBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.id }) { v ->
            val fav = v.id in favs
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .combinedClickable(
                        onClick = { onPlay(v) },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress(v)
                        }
                    )
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(96.dp, 56.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Thumb(v)
                    Text(
                        formatTime(v.durationMs),
                        fontSize = 11.sp,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(v.title, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val res = if (v.height > 0) "${v.height}p · " else ""
                    Text(
                        res + formatSize(context, v.size),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onFav(v) }) {
                    Icon(
                        if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = stringResource(if (fav) R.string.fav_remove else R.string.fav_add),
                        tint = if (fav) Color(0xFFFF3B30) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** كاش للمصغرات حتى لا تُعاد قراءتها عند كل تمرير. */
object ThumbCache {
    private val cache = object : LruCache<Long, Bitmap>(
        ((Runtime.getRuntime().maxMemory() / 1024) / 8).toInt()
    ) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount / 1024
    }

    fun get(id: Long): Bitmap? = cache.get(id)
    fun put(id: Long, bitmap: Bitmap) {
        cache.put(id, bitmap)
    }
}

@Composable
fun Thumb(item: VideoItem, large: Boolean = false) {
    val context = LocalContext.current
    val cacheKey = if (large) -item.id - 1 else item.id
    var bmp by remember(cacheKey) { mutableStateOf<ImageBitmap?>(ThumbCache.get(cacheKey)?.asImageBitmap()) }
    LaunchedEffect(cacheKey) {
        if (bmp == null) {
            val req = if (large) Size(640, 400) else Size(320, 180)
            val b = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.loadThumbnail(item.uri, req, null) }.getOrNull()
            }
            if (b != null) {
                ThumbCache.put(cacheKey, b)
                bmp = b.asImageBitmap()
            }
        }
    }
    bmp?.let {
        Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun AlbumList(albums: List<AlbumInfo>, onOpen: (String) -> Unit) {
    if (albums.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.empty_albums), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val context = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = listBottomPadding()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(albums, key = { it.name }) { a ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onOpen(a.name) }
            ) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1.35f)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Folder, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                    Thumb(a.cover, large = true)
                }
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        a.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        videoCountText(context, a.count),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
