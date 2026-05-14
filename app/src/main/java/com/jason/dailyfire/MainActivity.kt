package com.jason.dailyfire

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.LayoutInflater
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

private const val NOTIFICATION_CHANNEL_ID = "daily_fire_channel"
private const val DAILY_FIRE_ALARM_ACTION = "com.jason.dailyfire.DAILY_FIRE_NOTIFICATION"
private const val DAILY_FIRE_NOTIFICATION_ID = 7420

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createDailyFireNotificationChannel(this)
        scheduleDailyFireNotification(this)
        setContent { DailyFireApp() }
    }
}

class DailyFireNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != DAILY_FIRE_ALARM_ACTION) return

        createDailyFireNotificationChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val openPendingIntent = PendingIntent.getActivity(
            context,
            2001,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Daily Fire")
            .setContentText("Your Daily Fire is ready 🔥")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .build()

        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(DAILY_FIRE_NOTIFICATION_ID, notification)
        }
    }
}

enum class Tab { Home, Gallery, Quotes }

data class FireMedia(val uri: Uri, val isVideo: Boolean)

data class SavedReading(
    val title: String,
    val source: String,
    val dateSaved: String,
    val text: String
)

@Composable
fun DailyFireApp() {
    val context = LocalContext.current

    var media by remember { mutableStateOf(loadMedia(context)) }
    var favoriteMediaUris by remember { mutableStateOf(loadFavoriteMediaUris(context)) }
    var favoritesOnly by remember { mutableStateOf(loadFavoritesOnly(context)) }

    var savedReadings by remember { mutableStateOf(loadSavedReadings(context)) }
    var favoriteReadingKeys by remember { mutableStateOf(loadFavoriteReadingKeys(context)) }
    var readingFavoritesOnly by remember { mutableStateOf(false) }

    var selectedTab by remember { mutableStateOf(Tab.Home) }
    var webUrl by remember { mutableStateOf<String?>(null) }

    val activeMedia = remember(media, favoriteMediaUris, favoritesOnly) {
        if (favoritesOnly) media.filter { it.uri.toString() in favoriteMediaUris } else media
    }

    var current by remember { mutableStateOf(activeMedia.randomOrNull()) }
    var history by remember { mutableStateOf<List<String>>(emptyList()) }

    var remainingShuffleQueue by remember {
        mutableStateOf(
            activeMedia
                .filter { it.uri != current?.uri }
                .shuffled()
                .map { it.uri.toString() }
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(activeMedia.map { it.uri.toString() }, favoritesOnly) {
        if (activeMedia.isEmpty()) {
            current = null
            remainingShuffleQueue = emptyList()
            history = emptyList()
        } else if (current == null || activeMedia.none { it.uri == current?.uri }) {
            current = activeMedia.random()
            remainingShuffleQueue = activeMedia
                .filter { it.uri != current?.uri }
                .shuffled()
                .map { it.uri.toString() }
            history = emptyList()
        } else {
            val validUris = activeMedia.map { it.uri.toString() }.toSet()
            val currentUri = current?.uri?.toString()

            remainingShuffleQueue = remainingShuffleQueue
                .filter { it in validUris && it != currentUri }

            history = history.filter { it in validUris && it != currentUri }

            val queued = remainingShuffleQueue.toSet()
            val newUnqueuedItems = activeMedia
                .filter { it.uri.toString() !in queued && it.uri.toString() != currentUri }
                .map { it.uri.toString() }
                .shuffled()

            remainingShuffleQueue = remainingShuffleQueue + newUnqueuedItems
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Some providers do not allow persistable permissions.
            }
        }

        val added = uris.map { FireMedia(it, isVideoUri(context, it)) }
        media = (media + added).distinctBy { it.uri.toString() }
        saveMedia(context, media)

        if (current == null && media.isNotEmpty()) {
            current = media.random()
        }
    }

    fun goNext() {
        val currentUri = current?.uri?.toString()
        val next = pickNextShuffleBag(activeMedia, current, remainingShuffleQueue)

        if (next.first != null && currentUri != null) {
            history = history + currentUri
        }

        current = next.first
        remainingShuffleQueue = next.second
    }

    fun goPrevious() {
        if (history.isEmpty()) {
            goNext()
            return
        }

        val previousUri = history.last()
        val previousMedia = activeMedia.firstOrNull { it.uri.toString() == previousUri }

        if (previousMedia != null) {
            current?.let { now ->
                remainingShuffleQueue = listOf(now.uri.toString()) + remainingShuffleQueue
            }
            current = previousMedia
            history = history.dropLast(1)
        } else {
            history = history.dropLast(1)
            goNext()
        }
    }

    MaterialTheme {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                when {
                    webUrl != null -> WebReadingScreen(
                        url = webUrl!!,
                        onBack = { webUrl = null },
                        onSaveReading = { title, text ->
                            val source = when {
                                webUrl!!.contains("aa.org", ignoreCase = true) -> "AA Daily Reflections"
                                webUrl!!.contains("jftna", ignoreCase = true) -> "NA Just For Today"
                                else -> "Daily Reading"
                            }

                            val cleanTitle = title.ifBlank { source }

                            val cleanText = if (source == "AA Daily Reflections") {
                                cleanAaDailyReflection(text)
                            } else {
                                text
                                    .replace(Regex("\\n{3,}"), "\n\n")
                                    .trim()
                            }

                            if (cleanText.isNotBlank()) {
                                val reading = SavedReading(
                                    title = cleanTitle,
                                    source = source,
                                    dateSaved = todayLabel(),
                                    text = cleanText
                                )

                                savedReadings = (listOf(reading) + savedReadings)
                                    .distinctBy { it.source + "|" + it.title + "|" + it.text.take(250) }

                                saveSavedReadings(context, savedReadings)
                            }
                        }
                    )

                    selectedTab == Tab.Home -> HomeScreen(
                        media = current,
                        isFavorite = current?.uri?.toString() in favoriteMediaUris,
                        favoritesOnly = favoritesOnly,
                        onSwipeNext = { goNext() },
                        onSwipePrevious = { goPrevious() },
                        onAdd = { picker.launch(arrayOf("image/*", "video/*")) },
                        onToggleFavorite = {
                            current?.let { item ->
                                val uri = item.uri.toString()
                                favoriteMediaUris = if (uri in favoriteMediaUris) {
                                    favoriteMediaUris - uri
                                } else {
                                    favoriteMediaUris + uri
                                }
                                saveFavoriteMediaUris(context, favoriteMediaUris)
                            }
                        },
                        onToggleFavoritesOnly = {
                            favoritesOnly = !favoritesOnly
                            saveFavoritesOnly(context, favoritesOnly)
                        }
                    )

                    selectedTab == Tab.Gallery -> GalleryScreen(
                        media = if (favoritesOnly) media.filter { it.uri.toString() in favoriteMediaUris } else media,
                        allMediaCount = media.size,
                        favoritesOnly = favoritesOnly,
                        favoriteMediaUris = favoriteMediaUris,
                        onAdd = { picker.launch(arrayOf("image/*", "video/*")) },
                        onSelect = {
                            current = it
                            remainingShuffleQueue = remainingShuffleQueue.filter { uri -> uri != it.uri.toString() }
                            selectedTab = Tab.Home
                        },
                        onDelete = { item ->
                            media = media.filterNot { it.uri == item.uri }
                            favoriteMediaUris = favoriteMediaUris - item.uri.toString()
                            remainingShuffleQueue = remainingShuffleQueue.filter { it != item.uri.toString() }
                            saveMedia(context, media)
                            saveFavoriteMediaUris(context, favoriteMediaUris)

                            if (current?.uri == item.uri) {
                                val updatedActive = if (favoritesOnly) {
                                    media.filter { it.uri.toString() in favoriteMediaUris }
                                } else {
                                    media
                                }

                                current = updatedActive.randomOrNull()
                                remainingShuffleQueue = updatedActive
                                    .filter { it.uri != current?.uri }
                                    .shuffled()
                                    .map { it.uri.toString() }
                            }
                        },
                        onToggleFavorite = { item ->
                            val uri = item.uri.toString()
                            favoriteMediaUris = if (uri in favoriteMediaUris) {
                                favoriteMediaUris - uri
                            } else {
                                favoriteMediaUris + uri
                            }
                            saveFavoriteMediaUris(context, favoriteMediaUris)
                        },
                        onToggleFavoritesOnly = {
                            favoritesOnly = !favoritesOnly
                            saveFavoritesOnly(context, favoritesOnly)
                        }
                    )

                    selectedTab == Tab.Quotes -> QuotesScreen(
                        savedReadings = savedReadings,
                        favoriteReadingKeys = favoriteReadingKeys,
                        readingFavoritesOnly = readingFavoritesOnly,
                        onOpenAa = { webUrl = "https://www.aa.org/daily-reflections" },
                        onOpenNa = { webUrl = "https://www.jftna.org/" },
                        onDeleteReading = { reading ->
                            val key = reading.favoriteKey()
                            savedReadings = savedReadings.filterNot {
                                it.title == reading.title &&
                                    it.source == reading.source &&
                                    it.dateSaved == reading.dateSaved &&
                                    it.text == reading.text
                            }
                            favoriteReadingKeys = favoriteReadingKeys - key
                            saveSavedReadings(context, savedReadings)
                            saveFavoriteReadingKeys(context, favoriteReadingKeys)
                        },
                        onAddReadingToGallery = { reading ->
                            val imageUri = createReadingImage(context, reading)
                            val imageItem = FireMedia(imageUri, isVideo = false)

                            media = (media + imageItem).distinctBy { it.uri.toString() }
                            remainingShuffleQueue = remainingShuffleQueue + imageItem.uri.toString()
                            saveMedia(context, media)

                            if (current == null) {
                                current = imageItem
                            }

                            Toast.makeText(
                                context,
                                "Added to gallery successfully",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onToggleReadingFavorite = { reading ->
                            val key = reading.favoriteKey()
                            favoriteReadingKeys = if (key in favoriteReadingKeys) {
                                favoriteReadingKeys - key
                            } else {
                                favoriteReadingKeys + key
                            }
                            saveFavoriteReadingKeys(context, favoriteReadingKeys)
                        },
                        onToggleReadingFavoritesOnly = {
                            readingFavoritesOnly = !readingFavoritesOnly
                        }
                    )
                }

                if (webUrl == null) {
                    DailyFireNav(
                        selectedTab = selectedTab,
                        onTab = { selectedTab = it },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    media: FireMedia?,
    isFavorite: Boolean,
    favoritesOnly: Boolean,
    onSwipeNext: () -> Unit,
    onSwipePrevious: () -> Unit,
    onAdd: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleFavoritesOnly: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (media == null) {
            EmptyHome(
                onAdd = onAdd,
                favoritesOnly = favoritesOnly,
                onToggleFavoritesOnly = onToggleFavoritesOnly
            )
        } else {
            val mediaModifier = Modifier
                .fillMaxSize()
                .padding(bottom = 92.dp)

            key(media.uri.toString()) {
                if (media.isVideo) {
                    VideoPlayer(uri = media.uri, modifier = mediaModifier)
                } else {
                    AsyncImage(
                        model = media.uri,
                        contentDescription = "Daily Fire image",
                        contentScale = ContentScale.Fit,
                        modifier = mediaModifier
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 92.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.45f)
                            )
                        )
                    )
                    .pointerInput(media.uri.toString()) {
                        var totalDrag = 0f
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dragAmount ->
                                totalDrag += dragAmount
                            },
                            onDragEnd = {
                                when {
                                    totalDrag < -80f -> onSwipeNext()
                                    totalDrag > 80f -> onSwipePrevious()
                                    else -> onSwipeNext() // tap-like small movement fallback
                                }
                                totalDrag = 0f
                            },
                            onDragCancel = {
                                totalDrag = 0f
                            }
                        )
                    }
                    .pointerInput(media.uri.toString() + "_tap") {
                        detectTapGestures(onTap = { onSwipeNext() })
                    }
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 42.dp, end = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SmallPillButton(
                    text = if (favoritesOnly) "★ Only" else "All",
                    onClick = onToggleFavoritesOnly
                )
                SmallPillButton(
                    text = if (isFavorite) "★" else "☆",
                    onClick = onToggleFavorite
                )
            }
        }
    }
}

@Composable
fun EmptyHome(
    onAdd: () -> Unit,
    favoritesOnly: Boolean,
    onToggleFavoritesOnly: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FlameMark()
        Spacer(Modifier.height(24.dp))
        Text("Daily Fire", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text(
            if (favoritesOnly) {
                "No favorite media yet. Turn off favorites-only or star some media."
            } else {
                "Add your first photo or video to start your personal motivation feed."
            },
            color = Color.White.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
            fontSize = 16.sp
        )
        Spacer(Modifier.height(28.dp))
        FireButton("Add Media", onAdd)
        Spacer(Modifier.height(12.dp))
        FireButton(if (favoritesOnly) "Show All" else "Favorites Only", onToggleFavoritesOnly)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    media: List<FireMedia>,
    allMediaCount: Int,
    favoritesOnly: Boolean,
    favoriteMediaUris: Set<String>,
    onAdd: () -> Unit,
    onSelect: (FireMedia) -> Unit,
    onDelete: (FireMedia) -> Unit,
    onToggleFavorite: (FireMedia) -> Unit,
    onToggleFavoritesOnly: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 92.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Gallery", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                Text(
                    if (favoritesOnly) "${media.size} favorite fire drops" else "$allMediaCount saved fire drops",
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            FireButton(if (favoritesOnly) "All" else "★ Only", onToggleFavoritesOnly)
            Spacer(Modifier.width(8.dp))
            FireButton("Add", onAdd)
        }

        Spacer(Modifier.height(18.dp))

        if (media.isEmpty()) {
            EmptyGallery(onAdd)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(media) { item ->
                    Box(
                        modifier = Modifier
                            .height(132.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.DarkGray)
                            .clickable { onSelect(item) }
                    ) {
                        AsyncImage(
                            model = item.uri,
                            contentDescription = "Gallery item",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (item.isVideo) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("▶", color = Color.White)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.72f))
                                .clickable { onToggleFavorite(item) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (item.uri.toString() in favoriteMediaUris) "★" else "☆",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.72f))
                                .clickable { onDelete(item) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("×", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyGallery(onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No media here", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("Upload photos and videos that hit hard.", color = Color.White.copy(alpha = 0.65f))
        Spacer(Modifier.height(24.dp))
        FireButton("Add Media", onAdd)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuotesScreen(
    savedReadings: List<SavedReading>,
    favoriteReadingKeys: Set<String>,
    readingFavoritesOnly: Boolean,
    onOpenAa: () -> Unit,
    onOpenNa: () -> Unit,
    onDeleteReading: (SavedReading) -> Unit,
    onAddReadingToGallery: (SavedReading) -> Unit,
    onToggleReadingFavorite: (SavedReading) -> Unit,
    onToggleReadingFavoritesOnly: () -> Unit
) {
    val scrollState = rememberScrollState()
    var status by remember { mutableStateOf("") }

    val visibleReadings = if (readingFavoritesOnly) {
        savedReadings.filter { it.favoriteKey() in favoriteReadingKeys }
    } else {
        savedReadings
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(scrollState)
            .padding(start = 22.dp, end = 22.dp, top = 34.dp, bottom = 112.dp)
    ) {
        Text("Readings", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text(
            "Open daily readings, then hit Save to keep a personal copy here.",
            color = Color.White.copy(alpha = 0.68f),
            fontSize = 16.sp
        )

        Spacer(Modifier.height(24.dp))

        Text(
            "Daily Readings",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(12.dp))

        ReadingCard("AA Daily Reflections", "Open today's AA reading", onOpenAa)
        Spacer(Modifier.height(14.dp))
        ReadingCard("NA Just For Today", "Open today's NA reading", onOpenNa)

        Spacer(Modifier.height(32.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Saved Quotes & Readings",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tap to expand. Long-press to add as image.",
                    color = Color.White.copy(alpha = 0.52f),
                    fontSize = 14.sp
                )
            }
            FireButton(if (readingFavoritesOnly) "All" else "★ Only", onToggleReadingFavoritesOnly)
        }

        if (status.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(status, color = Color(0xFFFF7A1A), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(14.dp))

        if (visibleReadings.isEmpty()) {
            Text(
                if (readingFavoritesOnly) {
                    "No favorite readings yet."
                } else {
                    "Nothing saved yet. Open a reading and press Save."
                },
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 15.sp
            )
        } else {
            visibleReadings.forEach { reading ->
                SavedReadingCard(
                    reading = reading,
                    isFavorite = reading.favoriteKey() in favoriteReadingKeys,
                    onAddToGallery = {
                        onAddReadingToGallery(reading)
                        status = "Added to gallery successfully"
                    },
                    onDelete = { onDeleteReading(reading) },
                    onToggleFavorite = { onToggleReadingFavorite(reading) }
                )
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
fun ReadingCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FlameMark(Modifier.size(42.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = Color.White.copy(alpha = 0.58f))
            }
            Text("›", color = Color(0xFFFF7A1A), fontSize = 34.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedReadingCard(
    reading: SavedReading,
    isFavorite: Boolean,
    onAddToGallery: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { expanded = !expanded },
                onLongClick = { onAddToGallery() }
            )
    ) {
        Box {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    reading.source.uppercase(),
                    color = Color(0xFFFF7A1A),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    reading.title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(end = 74.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    reading.dateSaved,
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (expanded) reading.text else reading.text.take(420).trim() + if (reading.text.length > 420) "..." else "",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 15.sp,
                    lineHeight = 21.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (expanded) "Tap to collapse • Long-press to add to gallery" else "Tap to expand • Long-press to add to gallery",
                    color = Color.White.copy(alpha = 0.38f),
                    fontSize = 12.sp
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.72f))
                        .clickable { onToggleFavorite() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (isFavorite) "★" else "☆",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.72f))
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("×", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun WebReadingScreen(
    url: String,
    onBack: () -> Unit,
    onSaveReading: (title: String, text: String) -> Unit
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var saveStatus by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF101010))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FireButton("Back", onBack)
            Spacer(Modifier.width(10.dp))
            FireButton("Save", onClick = {
                val view = webView
                if (view == null) {
                    saveStatus = "Page not ready"
                } else {
                    val js = """
                        (function() {
                            const titleElement = document.querySelector('h1') || document.querySelector('h2');
                            const title = titleElement ? titleElement.innerText : document.title;
                            const bodyText = document.body ? document.body.innerText : '';
                            return JSON.stringify({ title: title || 'Saved Reading', text: bodyText || '' });
                        })();
                    """.trimIndent()

                    view.evaluateJavascript(js) { result ->
                        try {
                            val decoded = JSONTokener(result).nextValue() as? String ?: result
                            val obj = JSONObject(decoded)
                            val title = obj.optString("title", "Saved Reading")
                            val text = obj.optString("text", "")
                            onSaveReading(title, text)
                            saveStatus = "Saved"
                        } catch (_: Exception) {
                            saveStatus = "Could not save"
                        }
                    }
                }
            })
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Daily Reading", color = Color.White, fontWeight = FontWeight.Bold)
                if (saveStatus.isNotBlank()) {
                    Text(saveStatus, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
        }

        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    webViewClient = WebViewClient()
                    loadUrl(url)
                    webView = this
                }
            },
            update = { view ->
                webView = view
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun VideoPlayer(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 1f
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> {
                    player.playWhenReady = false
                    player.pause()
                }

                Lifecycle.Event.ON_RESUME -> {
                    player.playWhenReady = true
                    player.play()
                }

                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.playWhenReady = false
            player.stop()
            player.clearVideoSurface()
            player.release()
        }
    }

    AndroidView(
        factory = { viewContext ->
            val view = LayoutInflater.from(viewContext)
                .inflate(R.layout.player_view_texture, null) as PlayerView

            view.setShutterBackgroundColor(AndroidColor.TRANSPARENT)
            view.player = player
            view
        },
        update = { view ->
            view.player = player
        },
        modifier = modifier
    )
}

@Composable
fun DailyFireNav(selectedTab: Tab, onTab: (Tab) -> Unit, modifier: Modifier = Modifier) {
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = Color.Black.copy(alpha = 0.92f),
        contentColor = Color.White
    ) {
        NavigationBarItem(
            selected = selectedTab == Tab.Home,
            onClick = { onTab(Tab.Home) },
            icon = { Text("🔥") },
            label = { Text("Home") }
        )
        NavigationBarItem(
            selected = selectedTab == Tab.Gallery,
            onClick = { onTab(Tab.Gallery) },
            icon = { Text("▦") },
            label = { Text("Gallery") }
        )
        NavigationBarItem(
            selected = selectedTab == Tab.Quotes,
            onClick = { onTab(Tab.Quotes) },
            icon = { Text("✦") },
            label = { Text("Quotes") }
        )
    }
}

@Composable
fun FireButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFFF6A00),
            contentColor = Color.Black
        ),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SmallPillButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Black.copy(alpha = 0.72f),
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(999.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun FlameMark(modifier: Modifier = Modifier.size(68.dp)) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        Color(0xFFFFA040),
                        Color(0xFFFF5A00),
                        Color(0xFF241000)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text("🔥", fontSize = 30.sp)
    }
}

fun loadMedia(context: Context): List<FireMedia> {
    val prefs = context.getSharedPreferences("daily_fire", Context.MODE_PRIVATE)
    return prefs.getStringSet("media", emptySet()).orEmpty().mapNotNull { saved ->
        val parts = saved.split("|", limit = 2)
        if (parts.size == 2) FireMedia(Uri.parse(parts[1]), parts[0] == "video") else null
    }
}

fun saveMedia(context: Context, media: List<FireMedia>) {
    val encoded = media.map {
        if (it.isVideo) "video|${it.uri}" else "image|${it.uri}"
    }.toSet()

    context.getSharedPreferences("daily_fire", Context.MODE_PRIVATE)
        .edit()
        .putStringSet("media", encoded)
        .apply()
}

fun isVideoUri(context: Context, uri: Uri): Boolean {
    val type = context.contentResolver.getType(uri).orEmpty()
    return type.startsWith("video/")
}

fun pickNextShuffleBag(
    media: List<FireMedia>,
    current: FireMedia?,
    queue: List<String>
): Pair<FireMedia?, List<String>> {
    if (media.isEmpty()) return null to emptyList()
    if (media.size == 1) return media.first() to emptyList()

    val validUris = media.map { it.uri.toString() }.toSet()
    val currentUri = current?.uri?.toString()

    var cleanQueue = queue.filter { it in validUris && it != currentUri }

    if (cleanQueue.isEmpty()) {
        cleanQueue = media
            .filter { it.uri.toString() != currentUri }
            .shuffled()
            .map { it.uri.toString() }
    }

    val nextUri = cleanQueue.first()
    val nextMedia = media.firstOrNull { it.uri.toString() == nextUri } ?: media.random()
    val updatedQueue = cleanQueue.drop(1)

    return nextMedia to updatedQueue
}

fun todayLabel(): String {
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())
}

fun loadSavedReadings(context: Context): List<SavedReading> {
    val prefs = context.getSharedPreferences("daily_fire", Context.MODE_PRIVATE)
    val raw = prefs.getString("saved_readings", "[]") ?: "[]"

    return try {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    SavedReading(
                        title = obj.optString("title", "Saved Reading"),
                        source = obj.optString("source", "Daily Reading"),
                        dateSaved = obj.optString("dateSaved", ""),
                        text = obj.optString("text", "")
                    )
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun saveSavedReadings(context: Context, readings: List<SavedReading>) {
    val array = JSONArray()

    readings.forEach { reading ->
        array.put(
            JSONObject().apply {
                put("title", reading.title)
                put("source", reading.source)
                put("dateSaved", reading.dateSaved)
                put("text", reading.text)
            }
        )
    }

    context.getSharedPreferences("daily_fire", Context.MODE_PRIVATE)
        .edit()
        .putString("saved_readings", array.toString())
        .apply()
}

fun loadFavoriteMediaUris(context: Context): Set<String> {
    val prefs = context.getSharedPreferences("daily_fire", Context.MODE_PRIVATE)
    return prefs.getStringSet("favorite_media_uris", emptySet()).orEmpty()
}

fun saveFavoriteMediaUris(context: Context, favorites: Set<String>) {
    context.getSharedPreferences("daily_fire", Context.MODE_PRIVATE)
        .edit()
        .putStringSet("favorite_media_uris", favorites)
        .apply()
}

fun loadFavoritesOnly(context: Context): Boolean {
    val prefs = context.getSharedPreferences("daily_fire", Context.MODE_PRIVATE)
    return prefs.getBoolean("favorites_only", false)
}

fun saveFavoritesOnly(context: Context, favoritesOnly: Boolean) {
    context.getSharedPreferences("daily_fire", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("favorites_only", favoritesOnly)
        .apply()
}

fun loadFavoriteReadingKeys(context: Context): Set<String> {
    val prefs = context.getSharedPreferences("daily_fire", Context.MODE_PRIVATE)
    return prefs.getStringSet("favorite_reading_keys", emptySet()).orEmpty()
}

fun saveFavoriteReadingKeys(context: Context, favorites: Set<String>) {
    context.getSharedPreferences("daily_fire", Context.MODE_PRIVATE)
        .edit()
        .putStringSet("favorite_reading_keys", favorites)
        .apply()
}

fun SavedReading.favoriteKey(): String {
    return "$source|$title|$dateSaved|${text.take(80)}"
}

fun cleanAaDailyReflection(raw: String): String {
    val lines = raw
        .replace("\r", "")
        .split("\n")
        .map { it.trim() }
        .filter { it.isNotBlank() }

    if (lines.isEmpty()) return ""

    val shareIndex = lines.indexOfFirst { it.equals("Share", ignoreCase = true) }
    val beforeShare = if (shareIndex >= 0) lines.take(shareIndex) else lines

    val startIndex = beforeShare.indexOfFirst { line ->
        val upper = line.uppercase(Locale.getDefault())
        upper == line &&
            upper.length >= 6 &&
            !upper.contains("DAILY REFLECTION") &&
            !upper.contains("ALCOHOLICS ANONYMOUS") &&
            !upper.contains("SEARCH") &&
            !upper.contains("MENU")
    }

    val cleaned = if (startIndex >= 0) {
        beforeShare.drop(startIndex)
    } else {
        beforeShare
    }

    return cleaned.joinToString("\n\n").trim()
}

fun createReadingImage(context: Context, reading: SavedReading): Uri {
    val width = 1080
    val horizontalPadding = 80
    val contentWidth = width - (horizontalPadding * 2)

    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = 58f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }

    val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(255, 122, 26)
        textSize = 30f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }

    val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(235, 235, 235)
        textSize = 36f
    }

    val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(160, 160, 160)
        textSize = 26f
    }

    val titleLayout = makeStaticLayout(reading.title, titlePaint, contentWidth)
    val metaLayout = makeStaticLayout("${reading.source} • ${reading.dateSaved}", metaPaint, contentWidth)
    val bodyLayout = makeStaticLayout(reading.text, bodyPaint, contentWidth)
    val footerLayout = makeStaticLayout("DAILY FIRE", footerPaint, contentWidth)

    val height = max(
        1920,
        120 + titleLayout.height + 32 + metaLayout.height + 60 + bodyLayout.height + 80 + footerLayout.height + 120
    )

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(5, 5, 5)
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(52, 255, 106, 0)
    }
    canvas.drawOval(RectF(-260f, -220f, 520f, 520f), glowPaint)

    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(17, 17, 17)
    }
    canvas.drawRoundRect(
        RectF(38f, 38f, width - 38f, height - 38f),
        44f,
        44f,
        cardPaint
    )

    var y = 120f

    canvas.save()
    canvas.translate(horizontalPadding.toFloat(), y)
    titleLayout.draw(canvas)
    canvas.restore()
    y += titleLayout.height + 32

    canvas.save()
    canvas.translate(horizontalPadding.toFloat(), y)
    metaLayout.draw(canvas)
    canvas.restore()
    y += metaLayout.height + 60

    canvas.save()
    canvas.translate(horizontalPadding.toFloat(), y)
    bodyLayout.draw(canvas)
    canvas.restore()
    y += bodyLayout.height + 80

    canvas.save()
    canvas.translate(horizontalPadding.toFloat(), y)
    footerLayout.draw(canvas)
    canvas.restore()

    val directory = File(context.filesDir, "reading_images")
    if (!directory.exists()) directory.mkdirs()

    val filename = "daily_fire_${System.currentTimeMillis()}_${safeFileName(reading.title)}.png"
    val file = File(directory, filename)

    FileOutputStream(file).use { output ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
    }

    bitmap.recycle()

    return Uri.fromFile(file)
}

fun makeStaticLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
    return StaticLayout.Builder
        .obtain(text, 0, text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(10f, 1.0f)
        .setIncludePad(true)
        .build()
}

fun safeFileName(input: String): String {
    val cleaned = input
        .lowercase(Locale.getDefault())
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')

    return cleaned.take(40).ifBlank { "reading" }
}

fun createDailyFireNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Daily Fire",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Daily Fire reminders"
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}

fun scheduleDailyFireNotification(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val intent = Intent(context, DailyFireNotificationReceiver::class.java).apply {
        action = DAILY_FIRE_ALARM_ACTION
    }

    val pendingIntent = PendingIntent.getBroadcast(
        context,
        1001,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val calendar = Calendar.getInstance().apply {
        timeInMillis = System.currentTimeMillis()
        set(Calendar.HOUR_OF_DAY, 8)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)

        if (timeInMillis <= System.currentTimeMillis()) {
            add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    alarmManager.setInexactRepeating(
        AlarmManager.RTC_WAKEUP,
        calendar.timeInMillis,
        AlarmManager.INTERVAL_DAY,
        pendingIntent
    )
}
