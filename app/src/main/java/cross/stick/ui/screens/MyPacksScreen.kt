package cross.stick.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cross.stick.viewmodel.MainViewModel

@Composable
fun MyPacksScreen(viewModel: MainViewModel) {
    val savedPacks by viewModel.savedPacks.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("My Packs", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (savedPacks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No packs yet", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(savedPacks) { pack ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(pack.name, style = MaterialTheme.typography.titleMedium)
                                Text("${pack.stickerCount} stickers", style = MaterialTheme.typography.bodySmall)
                            }
                            Button(onClick = { viewModel.addToWhatsApp(pack.id) }) {
                                Text("Add to WhatsApp")
                            }
                        }
                    }
                }
            }
        }
    }
}
