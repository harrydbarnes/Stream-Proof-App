package com.example.spotifyproofsender

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotifyproofsender.data.AppSettings
import com.example.spotifyproofsender.data.DEFAULT_INSTAGRAM_INBOX_URL
import com.example.spotifyproofsender.data.DEFAULT_STATS_URL
import com.example.spotifyproofsender.data.ProofSlot
import com.example.spotifyproofsender.data.ProofStorage
import com.example.spotifyproofsender.data.ScreenshotFormat
import com.example.spotifyproofsender.data.SettingsRepository
import com.example.spotifyproofsender.data.isHttpsUrl
import com.example.spotifyproofsender.data.isInstagramGroupChatUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application.applicationContext)

    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    private val _captureSlot = MutableStateFlow<ProofSlot?>(null)
    val captureSlot: StateFlow<ProofSlot?> = _captureSlot

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val events = _events.asSharedFlow()

    fun captureProof(webView: WebView, slot: ProofSlot) {
        if (_captureSlot.value != null) return

        val snapshot = settings.value
        if (webView.url.isNullOrBlank()) {
            showMessage("Stats for Spotify has not loaded yet. Wait for the page, then try again.")
            return
        }

        _captureSlot.value = slot
        viewModelScope.launch {
            var bitmap: Bitmap? = null
            try {
                if (snapshot.captureDelayMs > 0) kotlinx.coroutines.delay(snapshot.captureDelayMs)

                bitmap = withContext(Dispatchers.Main.immediate) {
                    if (!webView.isAttachedToWindow || webView.width < 16 || webView.height < 16) {
                        error("The Stats WebView has no usable size yet. Wait for it to appear and try again.")
                    }
                    if (webView.url.isNullOrBlank()) {
                        error("Stats for Spotify is no longer loaded. Refresh the page and try again.")
                    }

                    Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888).also { target ->
                        webView.draw(Canvas(target))
                    }
                }

                val record = withContext(Dispatchers.IO) {
                    ProofStorage.saveBitmap(
                        resolver = getApplication<android.app.Application>().contentResolver,
                        bitmap = requireNotNull(bitmap),
                        slot = slot,
                        format = snapshot.screenshotFormat,
                        jpegQuality = snapshot.jpegQuality,
                    )
                }
                repository.saveProof(slot, record)
                showMessage("Saved ${record.displayName} in Pictures/SpotifyProof")
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                val detail = error.message?.takeIf(String::isNotBlank) ?: error.javaClass.simpleName
                showMessage("Could not save the proof: $detail")
            } finally {
                bitmap?.recycle()
                _captureSlot.value = null
            }
        }
    }

    fun saveUrls(statsUrl: String, instagramInboxUrl: String, savedGroupUrl: String) {
        val cleanStatsUrl = statsUrl.trim().ifBlank { DEFAULT_STATS_URL }
        val cleanInboxUrl = instagramInboxUrl.trim().ifBlank { DEFAULT_INSTAGRAM_INBOX_URL }
        val cleanSavedGroupUrl = savedGroupUrl.trim()
        if (!isHttpsUrl(cleanStatsUrl) || !isHttpsUrl(cleanInboxUrl) ||
            (cleanSavedGroupUrl.isNotBlank() && !isInstagramGroupChatUrl(cleanSavedGroupUrl))
        ) {
            showMessage("Use valid HTTPS URLs and an Instagram /direct/t/... group chat URL.")
            return
        }

        launchSettingUpdate {
            repository.saveUrls(
                statsUrl = cleanStatsUrl,
                instagramInboxUrl = cleanInboxUrl,
                savedGroupUrl = cleanSavedGroupUrl,
            )
            showMessage("URL settings saved")
        }
    }

    fun saveCurrentInstagramGroupUrl(url: String) {
        val trimmed = url.trim()
        if (!isInstagramGroupChatUrl(trimmed)) {
            showMessage("Open the Instagram group chat first, then save its /direct/t/... URL.")
            return
        }

        launchSettingUpdate {
            repository.saveUrls(
                statsUrl = settings.value.statsUrl,
                instagramInboxUrl = settings.value.instagramInboxUrl,
                savedGroupUrl = trimmed,
            )
            showMessage("Saved this Instagram direct URL as the group chat")
        }
    }

    fun setScreenshotFormat(format: ScreenshotFormat) = launchSettingUpdate {
        repository.setScreenshotFormat(format)
    }

    fun setJpegQuality(quality: Int) = launchSettingUpdate {
        repository.setJpegQuality(quality)
    }

    fun setCaptureDelay(delayMs: Long) = launchSettingUpdate {
        repository.setCaptureDelay(delayMs)
    }

    fun setStatsDesktopUserAgent(enabled: Boolean) = launchSettingUpdate {
        repository.setStatsDesktopUserAgent(enabled)
    }

    fun setInstagramDesktopUserAgent(enabled: Boolean) = launchSettingUpdate {
        repository.setInstagramDesktopUserAgent(enabled)
    }

    fun setForceMobileUserAgent(enabled: Boolean) = launchSettingUpdate {
        repository.setForceMobileUserAgent(enabled)
    }

    fun setThirdPartyCookies(enabled: Boolean) = launchSettingUpdate {
        repository.setThirdPartyCookies(enabled)
    }

    fun setInstagramHelperClicks(enabled: Boolean) = launchSettingUpdate {
        repository.setInstagramHelperClicks(enabled)
    }

    fun setCustomStatsUserAgent(value: String) = launchSettingUpdate {
        repository.setCustomStatsUserAgent(value)
    }

    fun setCustomInstagramUserAgent(value: String) = launchSettingUpdate {
        repository.setCustomInstagramUserAgent(value)
    }

    fun markSent(slot: ProofSlot, sent: Boolean = true) = launchSettingUpdate {
        repository.setSent(slot, sent)
    }

    fun clearProofReference(slot: ProofSlot) = launchSettingUpdate {
        repository.clearProofReference(slot)
        showMessage("${slot.label} proof reference removed. The image remains in Pictures/SpotifyProof.")
    }

    fun resetAllSettings() {
        launchSettingUpdate {
            repository.resetAll()
            showMessage("Settings and proof references reset")
        }
    }

    fun shareProof(context: Context, slot: ProofSlot) {
        val proof = settings.value.proofFor(slot)
        if (proof == null) {
            showMessage("There is no saved ${slot.label} proof yet.")
            return
        }

        val uri = Uri.parse(proof.uri)
        if (!ProofStorage.exists(context.contentResolver, uri)) {
            showMessage("The saved ${slot.label} image is no longer available. Capture it again.")
            return
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(proof.displayName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            val chooser = Intent.createChooser(shareIntent, "Share ${slot.label} proof").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            showMessage("Android could not find an app to share this image.")
        } catch (error: Throwable) {
            showMessage("Android share failed: ${error.message ?: "unknown error"}")
        }
    }

    fun viewProof(context: Context, slot: ProofSlot) {
        val proof = settings.value.proofFor(slot)
        if (proof == null) {
            showMessage("There is no saved ${slot.label} proof yet.")
            return
        }

        val uri = Uri.parse(proof.uri)
        if (!ProofStorage.exists(context.contentResolver, uri)) {
            showMessage("The saved ${slot.label} image is no longer available. Capture it again.")
            return
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            showMessage("No image viewer is installed for this proof.")
        } catch (error: Throwable) {
            showMessage("Could not open the proof: ${error.message ?: "unknown error"}")
        }
    }

    fun clearDomainCookies(host: String, displayName: String) {
        val cookieManager = CookieManager.getInstance()
        val domains = listOf("https://$host/", "https://www.$host/")
        val cookieNames = domains
            .flatMap { cookieManager.getCookie(it).orEmpty().split(';') }
            .map { it.trim().substringBefore('=').trim() }
            .filter(String::isNotBlank)
            .toSet()

        domains.forEach { domain ->
            cookieNames.forEach { name ->
                cookieManager.setCookie(
                    domain,
                    "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/",
                )
            }
        }
        cookieManager.flush()
        showMessage("Requested clearing of $displayName cookies")
    }

    private fun launchSettingUpdate(update: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                update()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                val detail = error.message?.takeIf(String::isNotBlank) ?: error.javaClass.simpleName
                showMessage("Could not save settings: $detail")
            }
        }
    }

    private fun showMessage(message: String) {
        _events.tryEmit(message)
    }
}
