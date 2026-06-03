package cross.stick.ui.navigation

import androidx.compose.animation.*
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import cross.stick.ui.screens.*
import cross.stick.viewmodel.MainViewModel

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val DOWNLOADING = "downloading"
    const val PREVIEW = "preview"
    const val CONVERTING = "converting"
    const val MY_PACKS = "my_packs"
}

@Composable
fun AppNavGraph(viewModel: MainViewModel) {
    val navController = androidx.navigation.compose.rememberNavController()
    val onboardingComplete by viewModel.onboardingComplete.collectAsState(initial = false)
    val isReady by viewModel.isReady.collectAsState(initial = false)
    val startDestination = if (onboardingComplete) Routes.HOME else Routes.ONBOARDING

    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val stickerSet by viewModel.stickerSet.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val isConverting by viewModel.isConverting.collectAsState()
    val conversionProgress by viewModel.conversionProgress.collectAsState()
    val currentPackId by viewModel.currentPackId.collectAsState()
    val downloadedFiles = viewModel.downloadedFilePaths

    if (isReady) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { fadeOut(tween(150)) }
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onComplete = { token, author ->
                        viewModel.completeOnboarding(token, author)
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.HOME) {
                HomeScreen(
                    isLoading = isLoading,
                    error = error,
                    stickerSet = stickerSet,
                    onFetchPack = { link -> viewModel.fetchStickerSet(link) },
                    navigateToDownloading = { navController.navigate(Routes.DOWNLOADING) },
                    onImportFromWhatsApp = { uris, emojis -> viewModel.importToTelegram(uris, emojis) }
                )
            }

            composable(Routes.DOWNLOADING) {
                val packName = stickerSet?.name ?: "Unknown pack"
                DownloadingScreen(
                    packName = packName,
                    progress = downloadProgress,
                    onAutoNavigate = {
                        if (downloadedFiles.isNotEmpty()) {
                            navController.navigate(Routes.PREVIEW) {
                                popUpTo(Routes.DOWNLOADING) { inclusive = true }
                            }
                        } else {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.DOWNLOADING) { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(Routes.PREVIEW) {
                if (downloadedFiles.isEmpty()) {
                    LaunchedEffect(Unit) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.PREVIEW) { inclusive = true }
                        }
                    }
                } else {
                    PreviewScreen(
                        files = downloadedFiles,
                        onDelete = { /* TODO */ },
                        onAddFiles = { /* TODO */ },
                        onConvert = {
                            viewModel.convertAllStickers()
                            navController.navigate(Routes.CONVERTING) {
                                popUpTo(Routes.PREVIEW) { inclusive = true }
                            }
                        }
                    )
                }
            }

            composable(Routes.CONVERTING) {
                ConvertingScreen(
                    packName = currentPackId ?: "unknown",
                    progress = conversionProgress,
                    onAutoNavigate = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.CONVERTING) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.MY_PACKS) {
                MyPacksScreen()
            }
        }
    }
}
