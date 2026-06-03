package cross.stick.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cross.stick.data.importer.DiscoveredStickerSource
import cross.stick.data.importer.StickerSourceScanner
import cross.stick.data.importer.UniversalStickerPack
import cross.stick.data.importer.WhatsAppMediaFolderImporter
import cross.stick.data.importer.WhatsAppStickerProviderImporter
import cross.stick.viewmodel.MainViewModel

@Composable
fun WhatsAppImportScreen(
    viewModel: MainViewModel,
    onExportToTelegram: (List<UniversalStickerPack>) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var sourceList by remember { mutableStateOf<List<DiscoveredStickerSource>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedPack by remember { mutableStateOf<UniversalStickerPack?>(null) }

    // Tarama işlemi
    LaunchedEffect(Unit) {
        isLoading = true
        val scanner = StickerSourceScanner(context)
        sourceList = scanner.scanProviders()
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WhatsApp → Telegram") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Sticker kaynakları", style = MaterialTheme.typography.titleLarge)
                Text("Telefonunuzda bulunan WhatsApp sticker kaynakları taranıyor...",
                    style = MaterialTheme.typography.bodyMedium)

                if (sourceList.isEmpty()) {
                    // Hiçbir ContentProvider bulunamadıysa direkt medya klasörünü tara
                    Button(
                        onClick = {
                            val importer = WhatsAppMediaFolderImporter()
                            val packs = importer.importPacks()
                            if (packs.isNotEmpty()) {
                                onExportToTelegram(packs)
                            } else {
                                Toast.makeText(context, "No WhatsApp stickers found in media folder", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan Media Folder")
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(sourceList) { source ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    // Import and show pack selection
                                    val providerImporter = WhatsAppStickerProviderImporter(context)
                                    val packs = providerImporter.importPacks(source)
                                    if (packs.isNotEmpty()) {
                                        onExportToTelegram(packs)
                                    } else {
                                        Toast.makeText(context, "No packs found in ${source.appLabel}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(source.appLabel, style = MaterialTheme.typography.titleMedium)
                                        Text(source.authority, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (source.isCompatible) {
                                        Icon(Icons.Default.Check, contentDescription = "Compatible",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        }

                        // Medya klasörü opsiyonu
                        item {
                            OutlinedButton(
                                onClick = {
                                    val importer = WhatsAppMediaFolderImporter()
                                    val packs = importer.importPacks()
                                    if (packs.isNotEmpty()) {
                                        onExportToTelegram(packs)
                                    } else {
                                        Toast.makeText(context, "No stickers found in media folder", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scan WhatsApp Media Folder")
                            }
                        }
                    }
                }
            }
        }
    }
}
