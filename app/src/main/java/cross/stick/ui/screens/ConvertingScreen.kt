package cross.stick.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun ConvertingScreen(
    packName: String,
    progress: Int,
    onAutoNavigate: () -> Unit
) {
    var showMinimum by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(500)
        showMinimum = true
    }

    LaunchedEffect(progress) {
        if (progress == 100 && showMinimum) {
            delay(300)
            onAutoNavigate()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Converting to WhatsApp format", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        Text(packName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(32.dp))
        LinearProgressIndicator(
            progress = { progress / 100f },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("$progress%", style = MaterialTheme.typography.bodyMedium)
    }
}
