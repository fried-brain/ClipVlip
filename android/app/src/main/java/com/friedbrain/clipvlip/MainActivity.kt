package com.friedbrain.clipvlip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Deliberately small launch surface.
 *
 * Keep startup independent of ViewModels, network requests, update SDKs and
 * native model runtimes. Those features will be initialized only after the
 * first frame is visible, so a failure in an optional subsystem cannot prevent
 * Android from opening the app.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF9B5CFF),
                    secondary = Color(0xFF45D5E8),
                    background = Color(0xFF050509),
                    surface = Color(0xFF151927)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ClipVlipLaunchScreen()
                }
            }
        }
    }
}

@Composable
private fun ClipVlipLaunchScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.clipvlip_logo),
            contentDescription = "ClipVlip",
            modifier = Modifier.fillMaxWidth().height(280.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "CLIPVLIP",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Your AI video studio",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Startup check",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "The app shell is running. AI models and update services will be initialized separately.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFABB3C5)
        )
    }
}
