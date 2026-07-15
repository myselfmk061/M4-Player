package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.database.MPlayerDatabase
import com.example.database.PlaylistEntity
import com.example.models.FolderItem
import com.example.models.VideoItem
import com.example.repository.VideoRepository
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodels.MPlayerViewModel
import com.example.viewmodels.MPlayerViewModelFactory

enum class NavigationTab(val label: String, val activeIcon: ImageVector, val inactiveIcon: ImageVector) {
    LOCAL("Local", Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary),
    PLAYLISTS("Playlists", Icons.Filled.FeaturedPlayList, Icons.Outlined.FeaturedPlayList),
    STREAM("Stream", Icons.Filled.RssFeed, Icons.Outlined.RssFeed),
    VAULT("Vault", Icons.Filled.Lock, Icons.Outlined.Lock),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize SQLite & Repository
        val database = MPlayerDatabase.getDatabase(applicationContext)
        val repository = VideoRepository(applicationContext, database.dao())
        
        // Initialize principal VM
        val viewModel: MPlayerViewModel by viewModels {
            MPlayerViewModelFactory(application, repository)
        }

        setContent {
            val accent by viewModel.selectedAccent.collectAsState()
            MyApplicationTheme(accent = accent, dynamicColor = false) {
                MainAppContent(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(viewModel: MPlayerViewModel) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(NavigationTab.LOCAL) }
    
    // Video view drilldown state
    var selectedFolderForVideos by remember { mutableStateOf<FolderItem?>(null) }
    var selectedPlaylistForVideos by remember { mutableStateOf<PlaylistEntity?>(null) }

    // Full-screen player triggers
    var activeVideoItem by remember { mutableStateOf<VideoItem?>(null) }
    var activeNetworkUrl by remember { mutableStateOf<String?>(null) }

    // Permissions State
    val requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasMediaPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, requiredPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMediaPermission = isGranted
        if (isGranted) {
            viewModel.triggerScan()
        }
    }

    // Trigger local library scan once permission is verified
    LaunchedEffect(hasMediaPermission) {
        if (hasMediaPermission) {
            viewModel.triggerScan()
        }
    }

    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(2200)
        showSplash = false
    }

    Crossfade(targetState = showSplash, label = "SplashTransition", animationSpec = tween(700)) { isSplash ->
        if (isSplash) {
            SplashScreen()
        } else {
            if (activeVideoItem != null || !activeNetworkUrl.isNullOrEmpty()) {
        // Render Full-Screen Immersive Playback Mode (No Scaffold or Navigation showing)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            PlayerScreen(
                videoItem = activeVideoItem,
                networkUrl = activeNetworkUrl,
                viewModel = viewModel,
                onBack = { lastPosition ->
                    // Save playback progress back to History Room table
                    activeVideoItem?.let { video ->
                        viewModel.addToHistory(video, lastPosition)
                    }
                    // Dismiss full-screen player overlay
                    activeVideoItem = null
                    activeNetworkUrl = null
                    viewModel.triggerScan()
                }
            )
        }
    } else {
        // Standard Library Mode with bottom navigation bar
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_logo),
                                contentDescription = "M4 Player Logo",
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "M4 Player",
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.testTag("mplayer_bottom_navigation")
                ) {
                    NavigationTab.values().forEach { tab ->
                        val selected = currentTab == tab
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                currentTab = tab
                                // Reset stack drill-down on tab switch
                                selectedFolderForVideos = null
                                selectedPlaylistForVideos = null
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.activeIcon else tab.inactiveIcon,
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag("main_library_scaffold")
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (!hasMediaPermission) {
                    // Show beautiful permissions onboarding view
                    PermissionOnboardingView(
                        permission = requiredPermission,
                        onGrantRequest = { permissionLauncher.launch(requiredPermission) }
                    )
                } else {
                    // Render Active Tab Content
                    when (currentTab) {
                        NavigationTab.LOCAL -> {
                            if (selectedFolderForVideos != null) {
                                // Folder drilldown videos list
                                Box(modifier = Modifier.fillMaxSize()) {
                                    VideosScreen(
                                        viewModel = viewModel,
                                        folder = selectedFolderForVideos,
                                        onVideoClick = { video -> activeVideoItem = video }
                                    )
                                    // Custom Back navigation button floating
                                    IconButton(
                                        onClick = { selectedFolderForVideos = null },
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(24.dp)
                                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                            .size(48.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowBack, "Back to Folders")
                                    }
                                }
                            } else {
                                // Folder select list grid
                                FoldersScreen(
                                    viewModel = viewModel,
                                    onFolderClick = { folder -> selectedFolderForVideos = folder }
                                )
                            }
                        }

                        NavigationTab.PLAYLISTS -> {
                            if (selectedPlaylistForVideos != null) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    PlaylistVideosScreen(
                                        viewModel = viewModel,
                                        playlist = selectedPlaylistForVideos!!,
                                        onVideoClick = { video -> activeVideoItem = video }
                                    )
                                    IconButton(
                                        onClick = { selectedPlaylistForVideos = null },
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(24.dp)
                                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                            .size(48.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowBack, "Back to Playlists")
                                    }
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // Top Tab selections inside Playlists (Playlists / History / Favorites)
                                    var tabSelection by remember { mutableStateOf(0) }
                                    TabRow(selectedTabIndex = tabSelection) {
                                        Tab(selected = tabSelection == 0, onClick = { tabSelection = 0 }) {
                                            Text("Playlists", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                        Tab(selected = tabSelection == 1, onClick = { tabSelection = 1 }) {
                                            Text("History", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                        Tab(selected = tabSelection == 2, onClick = { tabSelection = 2 }) {
                                            Text("Favorites", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                    }

                                    when (tabSelection) {
                                        0 -> PlaylistsScreen(
                                            viewModel = viewModel,
                                            onPlaylistClick = { playlist -> selectedPlaylistForVideos = playlist }
                                        )
                                        1 -> HistoryScreen(
                                            viewModel = viewModel,
                                            onVideoClick = { video -> activeVideoItem = video }
                                        )
                                        2 -> FavoritesScreen(
                                            viewModel = viewModel,
                                            onVideoClick = { video -> activeVideoItem = video }
                                        )
                                    }
                                }
                            }
                        }

                        NavigationTab.STREAM -> {
                            NetworkScreen(
                                viewModel = viewModel,
                                onStreamClick = { url -> activeNetworkUrl = url }
                            )
                        }

                        NavigationTab.VAULT -> {
                            VaultScreen(
                                viewModel = viewModel,
                                onVideoClick = { video -> activeVideoItem = video }
                            )
                        }

                        NavigationTab.SETTINGS -> {
                            SettingsScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
        }
    }
}

@Composable
fun PermissionOnboardingView(
    permission: String,
    onGrantRequest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.VideoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Storage Access Required",
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "MPlayer requires media storage permissions to automatically scan your directories, compile folder lists, fetch video thumbnails, and support seamless resume playback.",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onGrantRequest,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(52.dp)
                .testTag("grant_permission_button"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Security, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Grant Media Permission")
        }
    }
}

@Composable
fun SplashScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "SplashTransition")
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.93f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringRotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0C0A0F),
                        Color(0xFF181524),
                        Color(0xFF0F0E17)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.Center)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF8B5CF6).copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = ringRotation
                        }
                        .border(
                            width = 3.dp,
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color(0xFF8B5CF6),
                                    Color(0xFFEC4899),
                                    Color(0xFF06B6D4),
                                    Color(0xFF8B5CF6)
                                )
                            ),
                            shape = CircleShape
                        )
                )

                Image(
                    painter = painterResource(id = R.drawable.ic_logo),
                    contentDescription = "M4 Player Logo",
                    modifier = Modifier
                        .size(105.dp)
                        .clip(RoundedCornerShape(24.dp))
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text = "M4 PLAYER",
                fontWeight = FontWeight.Black,
                fontSize = 28.sp,
                letterSpacing = 6.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "PREMIUM MEDIA EXPERIENCE",
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 2.sp,
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(48.dp))

            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = Color(0xFF8B5CF6),
                strokeWidth = 2.5.dp
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "POWERED BY JETPACK MEDIA3",
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.sp,
                letterSpacing = 1.5.sp,
                color = Color.White.copy(alpha = 0.4f)
            )
        }
    }
}
