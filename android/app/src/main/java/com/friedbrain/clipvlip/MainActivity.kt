package com.friedbrain.clipvlip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val modelViewModel = ViewModelProvider(this)[ModelLibraryViewModel::class.java]
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF8B7CFF),
                    secondary = Color(0xFF45D5E8),
                    background = Color(0xFF090B12),
                    surface = Color(0xFF151927)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ModelLibraryScreen(modelViewModel)
                }
            }
        }
    }
}

@Composable
private fun ModelLibraryScreen(viewModel: ModelLibraryViewModel) {\n    val activity = LocalContext.current as ComponentActivity\n    val updateRepository = remember { UpdateRepository(activity) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            UpdateCenterCard(activity, updateRepository)
            Spacer(Modifier.height(18.dp))
            Image(
                painter = painterResource(R.drawable.clipvlip_logo),
                contentDescription = "ClipVlip logo",
                modifier = Modifier.fillMaxWidth().height(230.dp),
                contentScale = ContentScale.Fit
            )
            Text(
                text = "Your local AI studio",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Download the models you want to use. Video files and AI inference stay on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFABB3C5)
            )
            Spacer(Modifier.height(22.dp))
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF20243A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("LOCAL MODEL SETUP", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Choose a model for each AI task. Install them separately and remove them whenever you need storage.")
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        items(ModelCatalog.models, key = { it.id }) { model ->
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

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151927))
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("NEXT: SPEECH MODEL", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Whisper Tiny INT8 through Sherpa-ONNX is planned for offline transcription. It stays separate from the language models.",
                        color = Color(0xFFABB3C5),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: ModelArtifact,
    installed: Boolean,
    downloading: Boolean,
    progress: Float,
    error: String?,
    onDownload: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151927))
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(model.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text(model.role, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    text = if (installed) "READY" else model.sizeLabel,
                    color = if (installed) Color(0xFF61D7A5) else Color(0xFFABB3C5),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(model.description, color = Color(0xFFABB3C5), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Text("License: ${model.license}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFABB3C5))
            Text("Runtime: ${model.minimumRuntime}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFABB3C5))
            if (downloading) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(5.dp))
                Text("${(progress * 100).toInt()}% downloaded", color = Color(0xFFABB3C5), style = MaterialTheme.typography.labelSmall)
            }
            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(error, color = Color(0xFFFF8A8A), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onDownload,
                    enabled = !installed && !downloading,
                    modifier = Modifier.weight(1f)
                ) {
                    if (downloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text(if (installed) "Installed" else "Download model")
                    }
                }
                if (installed) {
                    Button(onClick = onRemove) { Text("Remove") }
                }
            }
        }
    }
}

@Composable
private fun UpdateCenterCard(activity: ComponentActivity, repository: UpdateRepository) {
    val scope = rememberCoroutineScope()
    var channel by remember { mutableStateOf(repository.selectedChannel()) }
    var status by remember { mutableStateOf(repository.lastStatus) }
    var release by remember { mutableStateOf<DirectRelease?>(null) }
    var busy by remember { mutableStateOf(false) }
    var remoteConfig by remember { mutableStateOf<MobileRemoteConfig?>(null) }
    LaunchedEffect(Unit) {
        val fetched = withContext(Dispatchers.IO) { runCatching { repository.fetchRemoteConfig() }.getOrNull() }
        remoteConfig = fetched
        if (fetched != null && fetched.maintenance) {
            status = fetched.maintenanceMessage.ifBlank { "ClipVlip is under maintenance." }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF20243A))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("APP UPDATES", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            Text("Choose how ClipVlip receives native app updates.")
            Text(
                remoteConfig?.let { if (it.maintenance) "Maintenance mode" else "Server configuration connected" }
                    ?: "Using local defaults until server configuration is reachable.",
                color = Color(0xFFABB3C5),
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { channel = UpdateChannel.AUTOMATIC; repository.setChannel(channel) }) { Text("Auto") }
                Button(onClick = { channel = UpdateChannel.DIRECT; repository.setChannel(channel) }) { Text("Direct") }
                Button(onClick = { channel = UpdateChannel.GOOGLE_PLAY; repository.setChannel(channel) }) { Text("Play") }
            }
            Text("Selected: ${channel.name.replace('_', ' ')}", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { repository.checkDirectRelease() }
                        }
                        release = result.getOrNull()
                        status = result.fold(
                            { if (it == null) repository.lastStatus else "Release ${it.tag} is available." },
                            { "Direct update check failed: ${it.message}" }
                        )
                        busy = false
                    }
                }) { Text("Check APK") }
                Button(enabled = !busy, onClick = {
                    repository.checkPlayUpdate(activity) { available ->
                        status = repository.lastStatus
                        if (available) repository.startPlayUpdate(activity, 4102)
                    }
                }) { Text("Check Play") }
            }
            if (release != null) {
                Button(enabled = !busy, onClick = {
                    val selected = release ?: return@Button
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { repository.downloadAndInstall(activity, selected) }
                        }
                        status = result.fold({ repository.lastStatus }, { "APK update failed: ${it.message}" })
                        busy = false
                    }
                }) { Text("Download and install ${release!!.tag}") }
            }
            if (busy) CircularProgressIndicator()
            Text(status, color = Color(0xFFABB3C5), style = MaterialTheme.typography.bodySmall)
        }
    }
}
