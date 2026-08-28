package com.example.spotifyproofsender.data

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ProofStorage {
    fun exists(resolver: ContentResolver, uri: Uri): Boolean = runCatching {
        resolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    }.getOrDefault(false)

    fun saveBitmap(
        resolver: ContentResolver,
        bitmap: Bitmap,
        slot: ProofSlot,
        format: ScreenshotFormat,
        jpegQuality: Int,
    ): ProofRecord {
        val extension = if (format == ScreenshotFormat.PNG) "png" else "jpg"
        val mimeType = if (format == ScreenshotFormat.PNG) "image/png" else "image/jpeg"
        val timestamp = System.currentTimeMillis()
        val datePart = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(Date(timestamp))
        val fileName = "spotify-proof-playlist-${slot.playlistNumber}-$datePart.$extension"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/SpotifyProof/")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            values,
        ) ?: error("MediaStore could not create $fileName")

        try {
            val compressed = resolver.openOutputStream(uri)?.use { output ->
                bitmap.compress(
                    if (format == ScreenshotFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                    jpegQuality,
                    output,
                )
            } ?: false

            if (!compressed) error("Android could not encode $fileName")

            val finalised = resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null,
            )
            if (finalised == 0) error("Android could not finalise $fileName")
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }

        return ProofRecord(
            uri = uri.toString(),
            displayName = fileName,
            createdAt = timestamp,
        )
    }

    fun loadThumbnail(resolver: ContentResolver, uri: Uri): Bitmap? {
        val decoded = runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, _, _ ->
                decoder.setTargetSize(320, 320)
                decoder.isMutableRequired = false
            }
        }.getOrNull()
        return decoded ?: runCatching {
            resolver.openInputStream(uri)?.use { stream -> BitmapFactory.decodeStream(stream) }
        }.getOrNull()
    }

    fun displayName(resolver: ContentResolver, uri: Uri): String? {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return uri.lastPathSegment
    }
}
