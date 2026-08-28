package com.example.spotifyproofsender.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.spotifyProofDataStore by preferencesDataStore(name = "spotify_proof_preferences")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val statsUrl = stringPreferencesKey("stats_url")
        val instagramInboxUrl = stringPreferencesKey("instagram_inbox_url")
        val savedInstagramGroupUrl = stringPreferencesKey("saved_instagram_group_url")
        val screenshotFormat = stringPreferencesKey("screenshot_format")
        val jpegQuality = intPreferencesKey("jpeg_quality")
        val captureDelayMs = longPreferencesKey("capture_delay_ms")
        val statsDesktopUserAgent = booleanPreferencesKey("stats_desktop_user_agent")
        val instagramDesktopUserAgent = booleanPreferencesKey("instagram_desktop_user_agent")
        val forceMobileUserAgent = booleanPreferencesKey("force_mobile_user_agent")
        val thirdPartyCookies = booleanPreferencesKey("third_party_cookies")
        val instagramHelperClicks = booleanPreferencesKey("instagram_helper_clicks")
        val customStatsUserAgent = stringPreferencesKey("custom_stats_user_agent")
        val customInstagramUserAgent = stringPreferencesKey("custom_instagram_user_agent")

        val playlist1Uri = stringPreferencesKey("playlist_1_uri")
        val playlist1Name = stringPreferencesKey("playlist_1_name")
        val playlist1CreatedAt = longPreferencesKey("playlist_1_created_at")
        val playlist1Sent = booleanPreferencesKey("playlist_1_sent")
        val playlist2Uri = stringPreferencesKey("playlist_2_uri")
        val playlist2Name = stringPreferencesKey("playlist_2_name")
        val playlist2CreatedAt = longPreferencesKey("playlist_2_created_at")
        val playlist2Sent = booleanPreferencesKey("playlist_2_sent")
    }

    val settings: Flow<AppSettings> = context.spotifyProofDataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { preferences -> preferences.toAppSettings() }

    suspend fun saveUrls(statsUrl: String, instagramInboxUrl: String, savedGroupUrl: String) {
        context.spotifyProofDataStore.edit { preferences ->
            preferences[Keys.statsUrl] = statsUrl
            preferences[Keys.instagramInboxUrl] = instagramInboxUrl
            preferences[Keys.savedInstagramGroupUrl] = savedGroupUrl
        }
    }

    suspend fun setScreenshotFormat(format: ScreenshotFormat) {
        context.spotifyProofDataStore.edit { it[Keys.screenshotFormat] = format.name }
    }

    suspend fun setJpegQuality(quality: Int) {
        context.spotifyProofDataStore.edit { it[Keys.jpegQuality] = quality.coerceIn(10, 100) }
    }

    suspend fun setCaptureDelay(delayMs: Long) {
        context.spotifyProofDataStore.edit { it[Keys.captureDelayMs] = delayMs.coerceIn(0L, 5_000L) }
    }

    suspend fun setStatsDesktopUserAgent(enabled: Boolean) {
        context.spotifyProofDataStore.edit { it[Keys.statsDesktopUserAgent] = enabled }
    }

    suspend fun setInstagramDesktopUserAgent(enabled: Boolean) {
        context.spotifyProofDataStore.edit { it[Keys.instagramDesktopUserAgent] = enabled }
    }

    suspend fun setForceMobileUserAgent(enabled: Boolean) {
        context.spotifyProofDataStore.edit { it[Keys.forceMobileUserAgent] = enabled }
    }

    suspend fun setThirdPartyCookies(enabled: Boolean) {
        context.spotifyProofDataStore.edit { it[Keys.thirdPartyCookies] = enabled }
    }

    suspend fun setInstagramHelperClicks(enabled: Boolean) {
        context.spotifyProofDataStore.edit { it[Keys.instagramHelperClicks] = enabled }
    }

    suspend fun setCustomStatsUserAgent(value: String) {
        context.spotifyProofDataStore.edit { it[Keys.customStatsUserAgent] = value }
    }

    suspend fun setCustomInstagramUserAgent(value: String) {
        context.spotifyProofDataStore.edit { it[Keys.customInstagramUserAgent] = value }
    }

    suspend fun saveProof(slot: ProofSlot, proof: ProofRecord) {
        context.spotifyProofDataStore.edit { preferences ->
            when (slot) {
                ProofSlot.PLAYLIST_1 -> {
                    preferences[Keys.playlist1Uri] = proof.uri
                    preferences[Keys.playlist1Name] = proof.displayName
                    preferences[Keys.playlist1CreatedAt] = proof.createdAt
                    preferences[Keys.playlist1Sent] = false
                }

                ProofSlot.PLAYLIST_2 -> {
                    preferences[Keys.playlist2Uri] = proof.uri
                    preferences[Keys.playlist2Name] = proof.displayName
                    preferences[Keys.playlist2CreatedAt] = proof.createdAt
                    preferences[Keys.playlist2Sent] = false
                }
            }
        }
    }

    suspend fun clearProofReference(slot: ProofSlot) {
        context.spotifyProofDataStore.edit { preferences ->
            when (slot) {
                ProofSlot.PLAYLIST_1 -> {
                    preferences.remove(Keys.playlist1Uri)
                    preferences.remove(Keys.playlist1Name)
                    preferences.remove(Keys.playlist1CreatedAt)
                    preferences[Keys.playlist1Sent] = false
                }

                ProofSlot.PLAYLIST_2 -> {
                    preferences.remove(Keys.playlist2Uri)
                    preferences.remove(Keys.playlist2Name)
                    preferences.remove(Keys.playlist2CreatedAt)
                    preferences[Keys.playlist2Sent] = false
                }
            }
        }
    }

    suspend fun setSent(slot: ProofSlot, sent: Boolean) {
        context.spotifyProofDataStore.edit { preferences ->
            when (slot) {
                ProofSlot.PLAYLIST_1 -> preferences[Keys.playlist1Sent] = sent
                ProofSlot.PLAYLIST_2 -> preferences[Keys.playlist2Sent] = sent
            }
        }
    }

    suspend fun resetAll() {
        context.spotifyProofDataStore.edit { it.clear() }
    }

    private fun Preferences.toAppSettings(): AppSettings {
        val format = get(Keys.screenshotFormat)
            ?.let { value -> ScreenshotFormat.entries.firstOrNull { it.name == value } }
            ?: ScreenshotFormat.JPG

        return AppSettings(
            statsUrl = get(Keys.statsUrl)?.trim()?.takeIf(::isHttpsUrl) ?: DEFAULT_STATS_URL,
            instagramInboxUrl = get(Keys.instagramInboxUrl)?.trim()?.takeIf(::isHttpsUrl)
                ?: DEFAULT_INSTAGRAM_INBOX_URL,
            savedInstagramGroupUrl = get(Keys.savedInstagramGroupUrl)
                ?.trim()
                ?.takeIf(::isInstagramGroupChatUrl)
                .orEmpty(),
            screenshotFormat = format,
            jpegQuality = (get(Keys.jpegQuality) ?: 92).coerceIn(10, 100),
            captureDelayMs = (get(Keys.captureDelayMs) ?: 500L).coerceIn(0L, 5_000L),
            statsDesktopUserAgent = get(Keys.statsDesktopUserAgent) ?: false,
            instagramDesktopUserAgent = get(Keys.instagramDesktopUserAgent) ?: false,
            forceMobileUserAgent = get(Keys.forceMobileUserAgent) ?: false,
            thirdPartyCookies = get(Keys.thirdPartyCookies) ?: true,
            instagramHelperClicks = get(Keys.instagramHelperClicks) ?: false,
            customStatsUserAgent = get(Keys.customStatsUserAgent).orEmpty(),
            customInstagramUserAgent = get(Keys.customInstagramUserAgent).orEmpty(),
            playlist1Proof = readProof(Keys.playlist1Uri, Keys.playlist1Name, Keys.playlist1CreatedAt),
            playlist2Proof = readProof(Keys.playlist2Uri, Keys.playlist2Name, Keys.playlist2CreatedAt),
            playlist1Sent = get(Keys.playlist1Sent) ?: false,
            playlist2Sent = get(Keys.playlist2Sent) ?: false,
        )
    }

    private fun Preferences.readProof(
        uriKey: Preferences.Key<String>,
        nameKey: Preferences.Key<String>,
        createdAtKey: Preferences.Key<Long>,
    ): ProofRecord? {
        val uri = get(uriKey) ?: return null
        val name = get(nameKey) ?: return null
        val createdAt = get(createdAtKey) ?: return null
        return ProofRecord(uri = uri, displayName = name, createdAt = createdAt)
    }
}
