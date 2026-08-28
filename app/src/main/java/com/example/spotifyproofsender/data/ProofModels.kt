package com.example.spotifyproofsender.data

enum class ScreenshotFormat {
    JPG,
    PNG,
}

enum class ProofSlot(val playlistNumber: Int, val label: String, val keyPrefix: String) {
    PLAYLIST_1(1, "Playlist 1", "playlist_1"),
    PLAYLIST_2(2, "Playlist 2", "playlist_2"),
}

data class ProofRecord(
    val uri: String,
    val displayName: String,
    val createdAt: Long,
)

data class AppSettings(
    val statsUrl: String = DEFAULT_STATS_URL,
    val instagramInboxUrl: String = DEFAULT_INSTAGRAM_INBOX_URL,
    val savedInstagramGroupUrl: String = "",
    val screenshotFormat: ScreenshotFormat = ScreenshotFormat.JPG,
    val jpegQuality: Int = 92,
    val captureDelayMs: Long = 500L,
    val statsDesktopUserAgent: Boolean = false,
    val instagramDesktopUserAgent: Boolean = false,
    val forceMobileUserAgent: Boolean = false,
    val thirdPartyCookies: Boolean = true,
    val instagramHelperClicks: Boolean = false,
    val customStatsUserAgent: String = "",
    val customInstagramUserAgent: String = "",
    val playlist1Proof: ProofRecord? = null,
    val playlist2Proof: ProofRecord? = null,
    val playlist1Sent: Boolean = false,
    val playlist2Sent: Boolean = false,
) {
    fun proofFor(slot: ProofSlot): ProofRecord? = when (slot) {
        ProofSlot.PLAYLIST_1 -> playlist1Proof
        ProofSlot.PLAYLIST_2 -> playlist2Proof
    }

    fun sentFor(slot: ProofSlot): Boolean = when (slot) {
        ProofSlot.PLAYLIST_1 -> playlist1Sent
        ProofSlot.PLAYLIST_2 -> playlist2Sent
    }
}

const val DEFAULT_STATS_URL = "https://www.statsforspotify.com/track/recent"
const val DEFAULT_INSTAGRAM_INBOX_URL = "https://www.instagram.com/direct/inbox/"

