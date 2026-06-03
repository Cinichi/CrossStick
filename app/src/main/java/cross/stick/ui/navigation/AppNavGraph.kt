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
    val isConverting by viewModel.isConverting.collectAsState()
    val currentPackId by viewModel.currentPackId.collectAsState()
    val downloadedFiles = viewModel.downloadedFilePaths

    if (isReady) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -it/3 }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it/3 }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
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
                DownloadingScreen(packName = packName, progress = 50)
                LaunchedEffect(isDownloading) {
                    if (!isDownloading) {
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
                }
            }

            composable(Routes.PREVIEW) {
                if (downloadedFiles.isEmpty()) {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.PREVIEW) { inclusive = true }
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
                ConvertingScreen(packName = currentPackId ?: "unknown", progress = 70)
                LaunchedEffect(isConverting) {
                    if (!isConverting) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.CONVERTING) { inclusive = true }
                        }
                    }
                }
            }

            composable(Routes.MY_PACKS) {
                MyPacksScreen()
            }
        }
    }
}
