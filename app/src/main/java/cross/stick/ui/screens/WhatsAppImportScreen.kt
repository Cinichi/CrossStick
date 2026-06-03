package cross.stick.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cross.stick.data.importer.DiscoveredStickerSource
import cross.stick.data.importer.StickerSourceScanner
import cross.stick.data.importer.UniversalStickerPack
import cross.stick.data.importer.WhatsAppInternalStickerImporter
import cross.stick.data.importer.WhatsAppMediaFolderImporter
import cross.stick.data.importer.WhatsAppStickerProviderImporter
import cross.stick.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppImportScreen(
    viewModel: MainViewModel,
    onExportToTelegram: (List<UniversalStickerPack>) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var sourceList by remember { mutableStateOf<List<DiscoveredStickerSource>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasRoot by remember { mutableStateOf(false) }
    var hasMediaFolder by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoading = true
        
        // 1. Scan ContentProviders
        val scanner = StickerSourceScanner(context)
        sourceList = scanner.scanProviders()
        
        // 2. Check root
        val rootImporter = WhatsAppInternalStickerImporter()
        hasRoot = rootImporter.isRootAvailable()
        
        // 3. Check media folder
        hasMediaFolder = try {
            val mediaDir = android.os.Environment.getExternalStorageDirectory()
            java.io.File(mediaDir, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers").exists()
        } catch (e: Exception) { false }
        
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
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Scanning for sticker sources...")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text("Sticker Sources", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Select a source to import stickers from WhatsApp", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // ContentProvider sources
                if (sourceList.isNotEmpty()) {
                    item {
                        Text("Installed Sticker Apps", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    items(sourceList) { source ->
                        SourceCard(
                            title = source.appLabel,
                            subtitle = source.authority,
                            confidence = if (source.isCompatible) "HIGH" else "LOW",
                            onSelect = {
                                val providerImporter = WhatsAppStickerProviderImporter(context)
                                val packs = providerImporter.importPacks(source)
                                if (packs.isNotEmpty()) {
                                    onExportToTelegram(packs)
                                } else {
                                    Toast.makeText(context, "No packs found", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }

                // Root internal importer
                if (hasRoot) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Root Access", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    item {
                        SourceCard(
                            title = "WhatsApp Internal Storage",
                            subtitle = "Provider-prefixed files (${if (sourceList.isEmpty()) "primary" else "fallback"})",
                            confidence = if (sourceList.isEmpty()) "MEDIUM" else "MEDIUM",
                            onSelect = {
                                val cacheDir = java.io.File(context.cacheDir, "wa_import")
                                val internalImporter = WhatsAppInternalStickerImporter()
                                val packs = internalImporter.importPacks(cacheDir)
                                if (packs.isNotEmpty()) {
                                    onExportToTelegram(packs)
                                } else {
                                    Toast.makeText(context, "No internal stickers found", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }

                // Media folder fallback
                if (hasMediaFolder) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Public Storage", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    item {
                        SourceCard(
                            title = "WhatsApp Media Folder",
                            subtitle = "Public sticker files (fallback)",
                            confidence = "LOW",
                            onSelect = {
                                val mediaImporter = WhatsAppMediaFolderImporter()
                                val packs = mediaImporter.importPacks()
                                if (packs.isNotEmpty()) {
                                    onExportToTelegram(packs)
                                } else {
                                    Toast.makeText(context, "No stickers found in media folder", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }

                if (sourceList.isEmpty() && !hasRoot && !hasMediaFolder) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Text(
                                "No sticker sources found. Install a sticker app or grant storage access.",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceCard(
    title: String,
    subtitle: String,
    confidence: String,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                SuggestionChip(
                    onClick = {},
                    label = { Text(confidence, style = MaterialTheme.typography.labelSmall) },
                    colors = when (confidence) {
                        "HIGH" -> SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        "MEDIUM" -> SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        else -> SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    }
                )
            }
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
