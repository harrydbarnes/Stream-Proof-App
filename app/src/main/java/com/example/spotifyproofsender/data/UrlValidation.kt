package com.example.spotifyproofsender.data

import android.net.Uri
import java.util.Locale

fun isHttpsUrl(value: String): Boolean {
    val uri = runCatching { Uri.parse(value.trim()) }.getOrNull() ?: return false
    return uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
}

fun isInstagramGroupChatUrl(value: String): Boolean {
    val uri = runCatching { Uri.parse(value.trim()) }.getOrNull() ?: return false
    val host = uri.host?.lowercase(Locale.US) ?: return false
    val pathSegments = uri.pathSegments

    return uri.scheme.equals("https", ignoreCase = true) &&
        host in setOf("instagram.com", "www.instagram.com") &&
        pathSegments.size >= 3 &&
        pathSegments[0].equals("direct", ignoreCase = true) &&
        pathSegments[1].equals("t", ignoreCase = true) &&
        pathSegments[2].isNotBlank()
}
