package cross.stick.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cross.stick.data.local.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)

    val botToken: StateFlow<String> = prefs.botToken.stateIn(
        viewModelScope, SharingStarted.Eagerly, ""
    )
    val authorName: StateFlow<String> = prefs.authorName.stateIn(
        viewModelScope, SharingStarted.Eagerly, ""
    )
    val onboardingComplete: StateFlow<Boolean> = prefs.onboardingComplete.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            // Wait for DataStore to load initial values
            onboardingComplete.collect {
                _isReady.value = true
            }
        }
    }

    fun completeOnboarding(token: String, author: String) {
        viewModelScope.launch {
            prefs.saveBotToken(token)
            prefs.saveAuthorName(author)
            prefs.completeOnboarding()
        }
    }

    fun fetchStickerSet(link: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                // TODO: Implement Telegram API call
                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
                _isLoading.value = false
            }
        }
    }
}
