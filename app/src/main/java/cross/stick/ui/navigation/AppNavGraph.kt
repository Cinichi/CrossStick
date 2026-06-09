package cross.stick.ui.navigation

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cross.stick.ui.screens.*
import cross.stick.viewmodel.ImportPhase
import cross.stick.viewmodel.MainViewModel

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val PROGRESS = "progress"
    const val PREVIEW = "preview"
    const val WHATSAPP_IMPORT = "whatsapp_import"
    const val MY_PACKS = "my_packs"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavGraph(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val onboardingComplete by viewModel.onboardingComplete.collectAsState(initial = false)
    val isReady by viewModel.isReady.collectAsState(initial = false)
    val phase by viewModel.phase.collectAsState()
    val currentPackId by viewModel.currentPackId.collectAsState()
    val previewStickers by viewModel.previewStickers.collectAsState()
    val startDestination = if (onboardingComplete) Routes.HOME else Routes.ONBOARDING
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val context = LocalContext.current

    // Queue of pack IDs still waiting to be sent to WhatsApp
    var pendingPackIds by remember { mutableStateOf<List<String>>(emptyList()) }

    // Use a ref so the launcher callback can access the latest pendingPackIds
    // without a self-reference cycle
    val pendingRef = remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(pendingPackIds) { pendingRef.value = pendingPackIds }

    val waLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_CANCELED) {
            val error = result.data?.getStringExtra("validation_error")
            Toast.makeText(context, error ?: "Sticker pack not added", Toast.LENGTH_LONG).show()
        }
        // Send next pending pack if any
        val remaining = pendingRef.value
        if (remaining.isNotEmpty()) {
            val nextId = remaining.first()
            pendingPackIds = remaining.drop(1)
            val errors = viewModel.validatePackForWhatsApp(nextId)
            if (errors.isEmpty()) {
                // Use a post to avoid calling launcher inside its own callback synchronously
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    runCatching { viewModel.getWhatsAppIntent(nextId).also { } }
                }
            } else {
                Toast.makeText(context, "Pack $nextId: ${errors.joinToString()}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Separate effect to actually launch next pack (avoids calling launcher in its own result)
    var nextPackToLaunch by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(nextPackToLaunch) {
        val id = nextPackToLaunch ?: return@LaunchedEffect
        nextPackToLaunch = null
        val errors = viewModel.validatePackForWhatsApp(id)
        if (errors.isEmpty()) {
            waLauncher.launch(viewModel.getWhatsAppIntent(id))
        } else {
            Toast.makeText(context, errors.joinToString("\n"), Toast.LENGTH_LONG).show()
        }
    }

    if (!isReady) return

    LaunchedEffect(phase, currentRoute) {
        when (phase) {
            is ImportPhase.Fetching, is ImportPhase.Downloading, is ImportPhase.Converting -> {
                if (currentRoute != Routes.PROGRESS) {
                    navController.navigate(Routes.PROGRESS) { launchSingleTop = true }
                }
            }
            is ImportPhase.PreviewReady -> {
                if (currentRoute != Routes.PREVIEW) {
                    navController.navigate(Routes.PREVIEW) { launchSingleTop = true }
                }
            }
            is ImportPhase.Done -> {
                val packId = (phase as ImportPhase.Done).packId
                val errors = viewModel.validatePackForWhatsApp(packId)
                if (errors.isEmpty()) {
                    waLauncher.launch(viewModel.getWhatsAppIntent(packId))
                } else {
                    Toast.makeText(context, errors.joinToString("\n"), Toast.LENGTH_LONG).show()
                }
                viewModel.resetPhase()
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.PROGRESS) { inclusive = true }
                }
            }
            is ImportPhase.MultiDone -> {
                val packIds = (phase as ImportPhase.MultiDone).packIds
                Toast.makeText(
                    context,
                    "Created ${packIds.size} packs. Adding to WhatsApp one by one...",
                    Toast.LENGTH_LONG
                ).show()

                if (packIds.isNotEmpty()) {
                    // Queue all except the first; launch the first directly
                    pendingPackIds = packIds.drop(1)
                    val firstId = packIds.first()
                    val errors = viewModel.validatePackForWhatsApp(firstId)
                    if (errors.isEmpty()) {
                        waLauncher.launch(viewModel.getWhatsAppIntent(firstId))
                    } else {
                        Toast.makeText(context, errors.joinToString("\n"), Toast.LENGTH_LONG).show()
                    }
                }

                viewModel.resetPhase()
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.PROGRESS) { inclusive = true }
                }
            }
            else -> Unit
        }
    }

    Scaffold(
        bottomBar = {
            if (onboardingComplete && currentRoute in listOf(
                    Routes.HOME, Routes.MY_PACKS, Routes.SETTINGS
                )
            ) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        selected = currentRoute == Routes.HOME,
                        onClick = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Inventory2, contentDescription = "My Packs") },
                        label = { Text("My Packs") },
                        selected = currentRoute == Routes.MY_PACKS,
                        onClick = {
                            navController.navigate(Routes.MY_PACKS) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        selected = currentRoute == Routes.SETTINGS,
                        onClick = {
                            navController.navigate(Routes.SETTINGS) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(300)) },
            exitTransition = { fadeOut(tween(300)) }
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onComplete = { token, author ->
                    viewModel.completeOnboarding(token, author)
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                })
            }
            composable(Routes.HOME) {
                HomeScreen(
                    phase = phase,
                    onFetchPack = { link -> viewModel.fetchStickerSet(link) },
                    onNavigateToWhatsAppImport = { navController.navigate(Routes.WHATSAPP_IMPORT) },
                    onImportFromWhatsApp = { uris, emojis -> viewModel.importToTelegram(uris, emojis) }
                )
            }
            composable(Routes.PROGRESS) {
                ProgressScreen(
                    phase = phase,
                    packName = currentPackId ?: "Unknown",
                    onDone = {
                        viewModel.resetPhase()
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.PROGRESS) { inclusive = true }
                        }
                    },
                    onRetry = {
                        viewModel.resetPhase()
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.PROGRESS) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.PREVIEW) {
                PreviewScreen(
                    packName = currentPackId ?: "Preview",
                    stickers = previewStickers,
                    onRemoveSticker = { viewModel.removePreviewSticker(it) },
                    onAddStickers = { viewModel.addPreviewUris(it) },
                    onConvert = { viewModel.convertPreviewToWhatsApp() },
                    onBack = {
                        viewModel.resetPhase()
                        navController.popBackStack(Routes.HOME, inclusive = false)
                    }
                )
            }
            composable(Routes.WHATSAPP_IMPORT) {
                WhatsAppImportScreen(
                    viewModel = viewModel,
                    onExportToTelegram = { packs -> viewModel.exportToTelegram(packs) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.MY_PACKS) { MyPacksScreen(viewModel) }
            composable(Routes.SETTINGS) { SettingsScreen(viewModel) }
        }
    }
}
