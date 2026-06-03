package cross.stick.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cross.stick.viewmodel.ImportPhase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    phase: ImportPhase,
    onFetchPack: (String) -> Unit,
    onNavigateToProgress: () -> Unit,
    onImportFromWhatsApp: (List<Uri>, List<String>) -> Unit
) {
    var link by remember { mutableStateOf("") }
    val isProcessing = phase !is ImportPhase.Idle && phase !is ImportPhase.Failed

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val emojis = List(uris.size) { "😀" }
            onImportFromWhatsApp(uris, emojis)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(64.dp))

        Text(
            text = "Transfer",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Stickers",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Paste a Telegram sticker pack link",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )

        OutlinedTextField(
            value = link,
            onValueChange = { link = it },
            label = { Text("Sticker pack link") },
            placeholder = { Text("https://t.me/addstickers/PackName") },
            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            singleLine = true,
            enabled = !isProcessing,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onFetchPack(link) },
            enabled = link.isNotBlank() && !isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Icon(Icons.Default.Search, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Fetch Stickers")
        }

        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "From WhatsApp",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Select WebP stickers to import to Telegram",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = { importLauncher.launch("image/webp") },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            enabled = !isProcessing,
            shape = MaterialTheme.shapes.large
        ) {
            Text("Import to Telegram")
        }
    }
}
