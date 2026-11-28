package com.friedbrain.clipvlip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppScreen { HOME, MODELS, UPDATES }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF9B5CFF),
                secondary = Color(0xFF45D5E8),
                background = Color(0xFF050509),
                surface = Color(0xFF151927)
            )) {
                var screen by remember { mutableStateOf(AppScreen.HOME) }
                Surface(
                    Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (screen) {
                        AppScreen.HOME -> LaunchScreen(
                            onModels = { screen = AppScreen.MODELS },
                            onUpdates = { screen = AppScreen.UPDATES }
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
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFE9E7F2)
        )
    }
}

@Composable
private fun LaunchScreen(onModels: () -> Unit, onUpdates: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.clipvlip_logo),
            contentDescription = "ClipVlip brand logo",
            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(18.dp))
        Text("CLIPVLIP", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Your AI video studio", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(20.dp))
        Text("The app shell is running. Open a feature when you're ready.", color = Color(0xFFABB3C5))
        Spacer(Modifier.height(20.dp))
        Button(onClick = onModels, modifier = Modifier.fillMaxWidth()) { Text("Open AI model library") }
        OutlinedButton(onClick = onUpdates, modifier = Modifier.fillMaxWidth()) { Text("App updates") }
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
            Text("Download models separately. Remove them whenever you need storage.", color = Color(0xFFABB3C5))
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
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF151927))) {
        Column(Modifier.padding(16.dp)) {
            Text(model.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(model.role, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(6.dp))
            Text(model.description, color = Color(0xFFABB3C5))
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
        Text("Select an update route. Direct APK updates require Android install approval; Play updates require a Play-installed app.", color = Color(0xFFABB3C5))
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
            enabled = !busy && config.maintenance.not(),
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
        Text(status, color = Color(0xFFABB3C5))
        Spacer(Modifier.height(24.dp))
    }
}
