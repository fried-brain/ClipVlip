package com.friedbrain.clipvlip

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppScreen { HOME, CREATE, LIBRARY, MODELS, UPDATES, SETTINGS }

private val AppBackground = Color(0xFF050509)
private val Panel = Color(0xFF151927)
private val Muted = Color(0xFFABB3C5)
private val Accent = Color(0xFF9B5CFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Accent,
                secondary = Color(0xFF45D5E8),
                background = AppBackground,
                surface = Panel
            )) {
                var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
                BackHandler(enabled = screen != AppScreen.HOME) { screen = AppScreen.HOME }
                Surface(
                    Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                    color = AppBackground
                ) {
                    when (screen) {
                        AppScreen.HOME -> HomeScreen(
                            onCreate = { screen = AppScreen.CREATE },
                            onLibrary = { screen = AppScreen.LIBRARY },
                            onModels = { screen = AppScreen.MODELS },
                            onUpdates = { screen = AppScreen.UPDATES },
                            onSettings = { screen = AppScreen.SETTINGS }
                        )
                        AppScreen.CREATE -> CreateProjectScreen(workerApi = remember { WorkerApi(this@MainActivity) }, onBack = { screen = AppScreen.HOME })
                        AppScreen.LIBRARY -> WorkerProjectsScreen(
                            workerApi = remember { WorkerApi(this@MainActivity) },
                            onBack = { screen = AppScreen.HOME }
                        )
                        AppScreen.SETTINGS -> WorkerSettingsScreen(
                            workerApi = remember { WorkerApi(this@MainActivity) },
                            onBack = { screen = AppScreen.HOME },
                            onUpdates = { screen = AppScreen.UPDATES },
                            onModels = { screen = AppScreen.MODELS }
                        )
                        AppScreen.MODELS -> {
                            val modelVm = remember {
                                ViewModelProvider(this@MainActivity)[ModelLibraryViewModel::class.java]
                            }
                            ModelLibraryScreen(modelVm, onBack = { screen = AppScreen.HOME })
                        }
                        AppScreen.UPDATES -> UpdateScreen(
                            activity = this@MainActivity,
                            onBack = { screen = AppScreen.HOME }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onBack,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Text("‹  Back", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HomeScreen(
    onCreate: () -> Unit,
    onLibrary: () -> Unit,
    onModels: () -> Unit,
    onUpdates: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("CLIPVLIP", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("YOUR AI VIDEO STUDIO", style = MaterialTheme.typography.labelMedium, color = Color(0xFF45D5E8), fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onSettings) { Text("Settings") }
        }
        Image(
            painter = painterResource(id = R.drawable.clipvlip_logo),
            contentDescription = "ClipVlip logo",
            modifier = Modifier.fillMaxWidth().heightIn(max = 190.dp),
            contentScale = ContentScale.Fit
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Turn long videos into", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
            Text("scroll-stopping clips.", style = MaterialTheme.typography.headlineSmall, color = Accent, fontWeight = FontWeight.Bold)
            Text("Find powerful moments, add captions and create vertical videos.", color = Muted)
        }
        Button(
            onClick = onCreate,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(18.dp)
        ) { Text("＋  Create new clips", style = MaterialTheme.typography.titleMedium) }
        Text("YOUR WORKSPACE", style = MaterialTheme.typography.labelLarge, color = Muted, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WorkspaceCard("My Projects", "History and exports", Modifier.weight(1f), onLibrary)
            WorkspaceCard("AI Models", "On-device models", Modifier.weight(1f), onModels)
        }
        WorkspaceCard("App Updates", "Keep ClipVlip current", Modifier.fillMaxWidth(), onUpdates)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun WorkspaceCard(title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable
private fun CreateProjectScreen(workerApi: WorkerApi, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedVideo by rememberSaveable { mutableStateOf<Uri?>(null) }
    var videoName by rememberSaveable { mutableStateOf("") }
    var sourceUrl by rememberSaveable { mutableStateOf("") }
    var clipCount by rememberSaveable { mutableIntStateOf(5) }
    var minDuration by rememberSaveable { mutableIntStateOf(20) }
    var maxDuration by rememberSaveable { mutableIntStateOf(60) }
    var aspectRatio by rememberSaveable { mutableStateOf("9:16") }
    var captions by rememberSaveable { mutableStateOf(true) }
    var captionStyle by rememberSaveable { mutableStateOf("bold") }
    var isSubmitting by remember { mutableStateOf(false) }
    var submissionMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedVideo = uri
            videoName = runCatching {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }.getOrNull() ?: "Selected video"
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Create Clips", onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("NEW VIDEO PROJECT", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Choose a source video to get started.", color = Muted)
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("VIDEO SOURCE", fontWeight = FontWeight.Bold, color = Color.White)
                    OutlinedButton(onClick = { picker.launch(arrayOf("video/*")) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (selectedVideo == null) "Choose video from device" else "Change selected video")
                    }
                    if (videoName.isNotBlank()) Text("Selected: $videoName", color = Color(0xFF45D5E8))
                    Text("OR PASTE A VIDEO URL", style = MaterialTheme.typography.labelMedium, color = Muted)
                    OutlinedTextField(
                        value = sourceUrl,
                        onValueChange = { sourceUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://…") },
                        singleLine = true
                    )
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("CLIP SETTINGS", fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Number of clips: $clipCount")
                    Slider(value = clipCount.toFloat(), onValueChange = { clipCount = it.toInt() }, valueRange = 1f..12f, steps = 10)
                    Text("Clip duration: $minDuration–$maxDuration seconds")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = minDuration == 15, onClick = { minDuration = 15; maxDuration = 45 }, label = { Text("15–45s") })
                        FilterChip(selected = minDuration == 20, onClick = { minDuration = 20; maxDuration = 60 }, label = { Text("20–60s") })
                        FilterChip(selected = minDuration == 30, onClick = { minDuration = 30; maxDuration = 90 }, label = { Text("30–90s") })
                    }
                    Text("Aspect ratio")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("9:16", "16:9", "1:1").forEach { ratio ->
                            FilterChip(selected = aspectRatio == ratio, onClick = { aspectRatio = ratio }, label = { Text(ratio) })
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Auto captions")
                        Switch(checked = captions, onCheckedChange = { captions = it })
                    }
                    if (captions) {
                        Text("Caption style")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("bold", "clean", "neon", "minimal").forEach { style ->
                                FilterChip(selected = captionStyle == style, onClick = { captionStyle = style }, label = { Text(style.replaceFirstChar { it.uppercase() }) })
                            }
                        }
                    }
                }
            }
            Button(
                onClick = {
                    isSubmitting = true
                    submissionMessage = "Uploading video and creating your project…"
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                workerApi.createTask(
                                    context = context,
                                    sourceUri = selectedVideo,
                                    sourceUrl = sourceUrl.trim(),
                                    clipCount = clipCount,
                                    minDuration = minDuration,
                                    maxDuration = maxDuration,
                                    aspectRatio = aspectRatio,
                                    captions = captions,
                                    captionStyle = captionStyle
                                )
                            }
                        }
                        submissionMessage = result.fold(
                            { "Project queued successfully. Project ID: $it" },
                            { "Could not start project: ${it.message}" }
                        )
                        isSubmitting = false
                    }
                },
                enabled = !isSubmitting && (selectedVideo != null || sourceUrl.trim().startsWith("https://")),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text(if (isSubmitting) "Uploading…" else "Continue to AI processing") }
            if (isSubmitting) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (submissionMessage.isNotBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF211833))) {
                    Text(submissionMessage, Modifier.padding(14.dp), color = Color.White)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

 
@Composable
private fun WorkerSettingsScreen(
    workerApi: WorkerApi,
    onBack: () -> Unit,
    onUpdates: () -> Unit,
    onModels: () -> Unit
) {
    var url by rememberSaveable { mutableStateOf(workerApi.baseUrl) }
    var key by rememberSaveable { mutableStateOf(workerApi.accessKey) }
    var status by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Settings", onBack)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("WORKER CONNECTION", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Connect the Android app to your private ClipVlip processing worker. Use its HTTPS URL and the worker access key configured in the hosting dashboard.", color = Muted)
            OutlinedTextField(value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Worker HTTPS URL") }, placeholder = { Text("https://your-worker.onrender.com") }, singleLine = true)
            OutlinedTextField(value = key, onValueChange = { key = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Worker access key") }, singleLine = true)
            Button(onClick = {
                if (!url.startsWith("https://") || key.isBlank()) {
                    status = "Enter a valid HTTPS URL and access key."
                } else {
                    workerApi.baseUrl = url
                    workerApi.accessKey = key
                    status = "Worker connection settings saved on this device."
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Save connection") }
            Text(status, color = Muted)
            Divider()
            OutlinedButton(onClick = onUpdates, modifier = Modifier.fillMaxWidth()) { Text("App updates") }
            OutlinedButton(onClick = onModels, modifier = Modifier.fillMaxWidth()) { Text("AI model library") }
        }
    }
}

@Composable
private fun WorkerProjectsScreen(workerApi: WorkerApi, onBack: () -> Unit) {
    var tasks by remember { mutableStateOf<List<WorkerTask>>(emptyList()) }
    var status by remember { mutableStateOf("Loading projects…") }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun refresh() {
        refreshing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { workerApi.listTasks() } }
            tasks = result.getOrDefault(emptyList())
            status = result.fold({ if (it.isEmpty()) "No projects yet. Create your first video project." else "" }, { "Could not load projects: ${it.message}" })
            refreshing = false
        }
    }
    LaunchedEffect(Unit) { refresh() }
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("My Projects", onBack)
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("YOUR VIDEO PROJECTS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = { refresh() }, enabled = !refreshing) { Text("Refresh") }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (status.isNotBlank()) Text(status, color = Muted)
            tasks.forEach { task ->
                Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Project ${task.id.take(10)}", fontWeight = FontWeight.Bold)
                        Text(task.status.uppercase(), color = MaterialTheme.colorScheme.secondary)
                        Text(task.message, color = Muted)
                        if (task.status == "processing" || task.status == "queued") {
                            LinearProgressIndicator(progress = { (task.progress.coerceIn(0, 100) / 100f) }, Modifier.fillMaxWidth())
                        }
                        task.error?.let { Text(it, color = Color(0xFFFF8A8A)) }
                        if (task.clips.length() > 0) Text("${task.clips.length()} clips generated. Clip playback and export controls are the next integration step.", color = Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpleInfoScreen(
    title: String,
    heading: String,
    message: String,
    onBack: () -> Unit,
    extra: @Composable ColumnScope.() -> Unit = {}
) {
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title, onBack)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(heading, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(message, color = Muted)
            extra()
        }
    }
}

@Composable
private fun ModelLibraryScreen(viewModel: ModelLibraryViewModel, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("AI Model Library", onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("LOCAL AI MODEL LIBRARY", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Download models separately. Remove them whenever you need storage.", color = Muted)
            ModelCatalog.models.forEach { model ->
                ModelCard(
                    model = model,
                    installed = viewModel.installed[model.id] == true,
                    downloading = viewModel.busy[model.id] == true,
                    progress = viewModel.progress[model.id] ?: 0f,
                    error = viewModel.errors[model.id],
                    onDownload = { viewModel.download(model) },
                    onRemove = { viewModel.remove(model) }
                )
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: ModelArtifact, installed: Boolean, downloading: Boolean, progress: Float,
    error: String?, onDownload: () -> Unit, onRemove: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(model.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(model.role, color = MaterialTheme.colorScheme.secondary)
            Text(model.description, color = Muted)
            Text("${model.sizeLabel} • ${model.license}", style = MaterialTheme.typography.labelSmall)
            if (downloading) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = Color(0xFFFF8A8A)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onDownload, enabled = !installed && !downloading) {
                    Text(if (downloading) "Downloading…" else if (installed) "Installed" else "Download")
                }
                if (installed) OutlinedButton(onClick = onRemove) { Text("Remove") }
            }
        }
    }
}

@Composable
private fun UpdateScreen(activity: ComponentActivity, onBack: () -> Unit) {
    val repository = remember { UpdateRepository(activity.applicationContext) }
    val scope = rememberCoroutineScope()
    var channel by remember { mutableStateOf(repository.selectedChannel()) }
    var status by remember { mutableStateOf(repository.lastStatus) }
    DisposableEffect(repository) {
        repository.onStatusChanged = { status = it }
        onDispose { repository.onStatusChanged = null }
    }
    var release by remember { mutableStateOf<DirectRelease?>(null) }
    var busy by remember { mutableStateOf(false) }
    var config by remember { mutableStateOf(MobileRemoteConfig()) }

    LaunchedEffect(Unit) {
        config = withContext(Dispatchers.IO) {
            runCatching { repository.fetchRemoteConfig() }.getOrDefault(MobileRemoteConfig())
        }
        repository.resumePlayUpdate(activity)
        status = repository.lastStatus
    }

    fun checkDirect() {
        if (!config.directUpdates) { status = "Direct APK updates are disabled."; busy = false; return }
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repository.checkDirectRelease() } }
            release = result.getOrNull()
            status = result.fold(
                { if (it == null) repository.lastStatus else "Release ${it.tag} is available." },
                { "Update check failed: ${it.message}" }
            )
            busy = false
        }
    }

    fun checkPlay() {
        if (!config.googlePlayUpdates) { status = "Google Play updates are disabled."; busy = false; return }
        repository.checkPlayUpdate(activity) { available ->
            status = repository.lastStatus
            if (available) repository.startPlayUpdate(activity, UpdateRepository.PLAY_UPDATE_REQUEST_CODE)
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        ScreenTopBar("App Updates", onBack)
        Text("APP UPDATE CENTRE", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Select an update route. Direct APK updates require Android install approval; Play updates require a Play-installed app.", color = Muted)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { channel = UpdateChannel.AUTOMATIC; repository.setChannel(channel) }) { Text("Auto") }
            Button(onClick = { channel = UpdateChannel.DIRECT; repository.setChannel(channel) }) { Text("Direct") }
            Button(onClick = { channel = UpdateChannel.GOOGLE_PLAY; repository.setChannel(channel) }) { Text("Play") }
        }
        Spacer(Modifier.height(8.dp))
        Text("Selected: ${channel.name}")
        Spacer(Modifier.height(10.dp))
        Button(
            enabled = !busy && !config.maintenance,
            onClick = {
                release = null
                busy = true
                when (channel) {
                    UpdateChannel.DIRECT -> checkDirect()
                    UpdateChannel.GOOGLE_PLAY -> checkPlay()
                    UpdateChannel.AUTOMATIC -> {
                        if (config.googlePlayUpdates) {
                            repository.checkAndStartAutomaticPlayUpdate(activity) { handled ->
                                if (handled) {
                                    status = repository.lastStatus
                                    busy = false
                                } else if (config.directUpdates) {
                                    checkDirect()
                                } else {
                                    status = repository.lastStatus
                                    busy = false
                                }
                            }
                        } else checkDirect()
                    }
                }
            }
        ) { Text(if (busy) "Checking…" else "Check for updates") }
        if (config.googlePlayUpdates && channel != UpdateChannel.DIRECT) {
            Button(enabled = !busy, onClick = {
                busy = true
                repository.completePlayUpdate { result ->
                    status = result
                    busy = false
                }
            }) { Text("Install downloaded Play update") }
        }
        release?.let { available ->
            Button(
                enabled = !busy && config.directUpdates,
                onClick = {
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { repository.downloadAndInstall(activity, available) }
                        }
                        status = result.fold({ repository.lastStatus }, { "APK install failed: ${it.message}" })
                        busy = false
                    }
                }
            ) { Text("Download and install ${available.tag}") }
        }
        if (busy) CircularProgressIndicator()
        Spacer(Modifier.height(10.dp))
        Text(status, color = Muted)
        Spacer(Modifier.height(24.dp))
    }
}
