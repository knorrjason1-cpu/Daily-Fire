package com.jason.dailyfire

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.LayoutInflater
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DailyFireApp() }
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
    var savedReadings by remember { mutableStateOf(loadSavedReadings(context)) }
    var selectedTab by remember { mutableStateOf(Tab.Home) }
    var webUrl by remember { mutableStateOf<String?>(null) }
    var current by remember { mutableStateOf(media.randomOrNull()) }

    LaunchedEffect(media.size) {
        if (current == null && media.isNotEmpty()) {
            current = media.random()
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
                            val cleanText = text
                                .replace(Regex("\\n{3,}"), "\n\n")
                                .trim()

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
                        onShuffle = {
                            if (media.isNotEmpty()) {
                                current = randomDifferent(media, current)
                            }
                        },
                        onAdd = { picker.launch(arrayOf("image/*", "video/*")) }
                    )

                    selectedTab == Tab.Gallery -> GalleryScreen(
                        media = media,
                        onAdd = { picker.launch(arrayOf("image/*", "video/*")) },
                        onSelect = {
                            current = it
                            selectedTab = Tab.Home
                        },
                        onDelete = { item ->
                            media = media.filterNot { it.uri == item.uri }
                            saveMedia(context, media)

                            if (current?.uri == item.uri) {
                                current = media.randomOrNull()
                            }
                        }
                    )

                    selectedTab == Tab.Quotes -> QuotesScreen(
                        savedReadings = savedReadings,
                        onOpenAa = { webUrl = "https://www.aa.org/daily-reflections" },
                        onOpenNa = { webUrl = "https://www.jftna.org/" },
                        onDeleteReading = { reading ->
                            savedReadings = savedReadings.filterNot {
                                it.title == reading.title &&
                                    it.source == reading.source &&
                                    it.dateSaved == reading.dateSaved &&
                                    it.text == reading.text
                            }
                            saveSavedReadings(context, savedReadings)
                        },
                        onAddReadingToGallery = { reading ->
                            val imageUri = createReadingImage(context, reading)
                            val imageItem = FireMedia(imageUri, isVideo = false)

                            media = (media + imageItem).distinctBy { it.uri.toString() }
                            saveMedia(context, media)

                            if (current == null) {
                                current = imageItem
                            }
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
fun HomeScreen(media: FireMedia?, onShuffle: () -> Unit, onAdd: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (media == null) {
            EmptyHome(onAdd)
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
                        detectTapGestures(onTap = { onShuffle() })
                    }
            )
        }
    }
}

@Composable
fun EmptyHome(onAdd: () -> Unit) {
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
            "Add your first photo or video to start your personal motivation feed.",
            color = Color.White.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
            fontSize = 16.sp
        )
        Spacer(Modifier.height(28.dp))
        FireButton("Add Media", onAdd)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    media: List<FireMedia>,
    onAdd: () -> Unit,
    onSelect: (FireMedia) -> Unit,
    onDelete: (FireMedia) -> Unit
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
                Text("${media.size} saved fire drops", color = Color.White.copy(alpha = 0.6f))
            }
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
        Text("No media yet", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
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
    onOpenAa: () -> Unit,
    onOpenNa: () -> Unit,
    onDeleteReading: (SavedReading) -> Unit,
    onAddReadingToGallery: (SavedReading) -> Unit
) {
    val scrollState = rememberScrollState()
    var status by remember { mutableStateOf("") }

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

        Text(
            "Saved Quotes & Readings",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Tap to expand. Long-press to add the full reading as an image to your gallery.",
            color = Color.White.copy(alpha = 0.52f),
            fontSize = 14.sp
        )

        if (status.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(status, color = Color(0xFFFF7A1A), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(14.dp))

        if (savedReadings.isEmpty()) {
            Text(
                "Nothing saved yet. Open a reading and press Save.",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 15.sp
            )
        } else {
            savedReadings.forEach { reading ->
                SavedReadingCard(
                    reading = reading,
                    onAddToGallery = {
                        onAddReadingToGallery(reading)
                        status = "Added to gallery: ${reading.title.take(32)}"
                    },
                    onDelete = { onDeleteReading(reading) }
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
    onAddToGallery: () -> Unit,
    onDelete: () -> Unit
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
                    modifier = Modifier.padding(end = 34.dp)
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

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
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

            view.setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
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

fun randomDifferent(media: List<FireMedia>, current: FireMedia?): FireMedia {
    if (media.size <= 1) return media.first()
    return media.filter { it.uri != current?.uri }.random()
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
