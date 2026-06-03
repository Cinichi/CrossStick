package cross.stick.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cross.stick.viewmodel.ImportPhase

@Composable
fun ProgressScreen(
    phase: ImportPhase,
    packName: String,
    onDone: () -> Unit,
    onRetry: () -> Unit
) {
    var showMinimum by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(400)
        showMinimum = true
    }

    LaunchedEffect(phase, showMinimum) {
        if (phase is ImportPhase.Done && showMinimum) {
            kotlinx.coroutines.delay(600)
            onDone()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (phase) {
            is ImportPhase.Idle -> {}
            is ImportPhase.Fetching -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Fetching sticker pack...", style = MaterialTheme.typography.bodyLarge)
                Text(packName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            is ImportPhase.Downloading -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Downloading stickers", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(packName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { phase.current.toFloat() / phase.total },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("${phase.current} of ${phase.total}", style = MaterialTheme.typography.bodyMedium)
            }
            is ImportPhase.PreviewReady -> {
                Text("Ready to preview", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Opening preview...", style = MaterialTheme.typography.bodyLarge)
            }
            is ImportPhase.Converting -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Converting stickers", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(packName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { phase.current.toFloat() / phase.total },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("${phase.current} of ${phase.total}", style = MaterialTheme.typography.bodyMedium)
            }
            is ImportPhase.Done -> {
                Text("✅", style = MaterialTheme.typography.displaySmall)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Done!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Stickers added to WhatsApp", style = MaterialTheme.typography.bodyLarge)
            }
            is ImportPhase.Failed -> {
                Text("❌", style = MaterialTheme.typography.displaySmall)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Error", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(phase.error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onRetry) {
                    Text("Try Again")
                }
            }
        }
    }
}
