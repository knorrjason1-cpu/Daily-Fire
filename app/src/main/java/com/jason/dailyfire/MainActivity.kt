package com.jason.dailyfire

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DailyFireApp() }
    }
}

enum class Tab { Home, Gallery, Quotes }

data class FireMedia(val uri: Uri, val isVideo: Boolean)

@Composable
fun DailyFireApp() {
    val context = LocalContext.current
    var media by remember { mutableStateOf(loadMedia(context)) }
    var selectedTab by remember { mutableStateOf(Tab.Home) }
    var webUrl by remember { mutableStateOf<String?>(null) }
    var current by remember { mutableStateOf(media.randomOrNull()) }

    LaunchedEffect(media.size) {
        if (current == null && media.isNotEmpty()) current = media.random()
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
            } catch (_: Exception) {}
        }
        val added = uris.map { FireMedia(it, isVideoUri(context, it)) }
        media = (media + added).distinctBy { it.uri.toString() }
        saveMedia(context, media)
        if (current == null && media.isNotEmpty()) current = media.random()
    }

    MaterialTheme {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                when {
                    webUrl != null -> WebReadingScreen(
                        url = webUrl!!,
                        onBack = { webUrl = null }
                    )
                    selectedTab == Tab.Home -> HomeScreen(
                        media = current,
                        onShuffle = {
                            if (media.isNotEmpty()) current = media.random()
                        },
                        onAdd = { picker.launch(arrayOf("image/*", "video/*")) }
                    )
                    selectedTab == Tab.Gallery -> GalleryScreen(
                        media = media,
                        onAdd = { picker.launch(arrayOf("image/*", "video/*")) },
                        onSelect = {
                            current = it
                            selectedTab = Tab.Home
                        }
                    )
                    selectedTab == Tab.Quotes -> QuotesScreen(
                        onOpenAa = { webUrl = "https://www.aa.org/daily-reflections" },
                        onOpenNa = { webUrl = "https://www.jftna.org/" }
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
            .clickable { onShuffle() }
    ) {
        if (media == null) {
            EmptyHome(onAdd)
        } else {
            if (media.isVideo) {
                VideoPlayer(uri = media.uri, modifier = Modifier.fillMaxSize())
            } else {
                AsyncImage(
                    model = media.uri,
                    contentDescription = "Daily Fire image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 24.dp, end = 24.dp, bottom = 104.dp)
            ) {
                Text("DAILY FIRE", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Tap anywhere to shuffle", color = Color.White.copy(alpha = 0.86f), fontSize = 26.sp, fontWeight = FontWeight.Black)
            }
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
fun GalleryScreen(media: List<FireMedia>, onAdd: () -> Unit, onSelect: (FireMedia) -> Unit) {
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
                            ) { Text("▶", color = Color.White) }
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

@Composable
fun QuotesScreen(onOpenAa: () -> Unit, onOpenNa: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(start = 22.dp, end = 22.dp, top = 34.dp, bottom = 100.dp)
    ) {
        Text("Readings", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text(
            "Open daily recovery readings inside Daily Fire.",
            color = Color.White.copy(alpha = 0.68f),
            fontSize = 16.sp
        )
        Spacer(Modifier.height(28.dp))
        ReadingCard("AA Daily Reflections", "Open today's AA reading", onOpenAa)
        Spacer(Modifier.height(14.dp))
        ReadingCard("NA Just For Today", "Open today's NA reading", onOpenNa)
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

@Composable
fun WebReadingScreen(url: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF101010))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FireButton("Back", onBack)
            Spacer(Modifier.width(12.dp))
            Text("Daily Reading", color = Color.White, fontWeight = FontWeight.Bold)
        }
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    webViewClient = WebViewClient()
                    loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun VideoPlayer(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = {
            PlayerView(it).apply {
                useController = false
                this.player = player
            }
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
        NavigationBarItem(selected = selectedTab == Tab.Home, onClick = { onTab(Tab.Home) }, icon = { Text("🔥") }, label = { Text("Home") })
        NavigationBarItem(selected = selectedTab == Tab.Gallery, onClick = { onTab(Tab.Gallery) }, icon = { Text("▦") }, label = { Text("Gallery") })
        NavigationBarItem(selected = selectedTab == Tab.Quotes, onClick = { onTab(Tab.Quotes) }, icon = { Text("✦") }, label = { Text("Quotes") })
    }
}

@Composable
fun FireButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6A00), contentColor = Color.Black),
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
            .background(Brush.radialGradient(listOf(Color(0xFFFFA040), Color(0xFFFF5A00), Color(0xFF241000)))),
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
    val encoded = media.map { if (it.isVideo) "video|${it.uri}" else "image|${it.uri}" }.toSet()
    context.getSharedPreferences("daily_fire", Context.MODE_PRIVATE)
        .edit()
        .putStringSet("media", encoded)
        .apply()
}

fun isVideoUri(context: Context, uri: Uri): Boolean {
    val type = context.contentResolver.getType(uri).orEmpty()
    return type.startsWith("video/")
}
