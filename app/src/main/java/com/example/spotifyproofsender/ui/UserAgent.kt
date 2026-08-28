package com.example.spotifyproofsender.ui

import android.content.Context
import android.webkit.WebSettings

private const val DESKTOP_CHROME_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

fun resolveUserAgent(
    context: Context,
    customUserAgent: String,
    forceMobile: Boolean,
    useDesktop: Boolean,
): String {
    customUserAgent.trim().takeIf(String::isNotBlank)?.let { return it }

    val defaultUserAgent = WebSettings.getDefaultUserAgent(context)
    return when {
        forceMobile -> defaultUserAgent.ensureMobileToken()
        useDesktop -> DESKTOP_CHROME_USER_AGENT
        else -> defaultUserAgent
    }
}

private fun String.ensureMobileToken(): String =
    if (contains("Mobile", ignoreCase = true)) this else "$this Mobile"

