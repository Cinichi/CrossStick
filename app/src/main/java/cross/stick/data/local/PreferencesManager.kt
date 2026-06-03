package cross.stick.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "crossstick_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_BOT_TOKEN = stringPreferencesKey("bot_token")
        private val KEY_AUTHOR_NAME = stringPreferencesKey("author_name")
        private val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }

    val botToken: Flow<String> = context.dataStore.data.map { it[KEY_BOT_TOKEN] ?: "" }
    val authorName: Flow<String> = context.dataStore.data.map { it[KEY_AUTHOR_NAME] ?: "" }
    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map { it[KEY_ONBOARDING_COMPLETE] ?: false }

    suspend fun saveBotToken(token: String) {
        context.dataStore.edit { it[KEY_BOT_TOKEN] = token }
    }

    suspend fun saveAuthorName(name: String) {
        context.dataStore.edit { it[KEY_AUTHOR_NAME] = name }
    }

    suspend fun completeOnboarding() {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETE] = true }
    }
}
