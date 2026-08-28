package com.example.spotifyproofsender.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.spotifyproofsender.data.ProofRecord
import com.example.spotifyproofsender.data.ProofStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ThumbnailState(
    val bitmap: Bitmap?,
    val finished: Boolean,
)


@Composable
fun ProofThumbnail(
    proof: ProofRecord?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val thumbnailState by produceState(
        initialValue = ThumbnailState(bitmap = null, finished = proof == null),
        key1 = proof?.uri,
    ) {
        value = if (proof == null) {
            ThumbnailState(bitmap = null, finished = true)
        } else {
            ThumbnailState(
                bitmap = withContext(Dispatchers.IO) {
                    ProofStorage.loadThumbnail(context.contentResolver, Uri.parse(proof.uri))
                },
                finished = true,
            )
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            thumbnailState.bitmap != null -> Image(
                bitmap = thumbnailState.bitmap!!.asImageBitmap(),
                contentDescription = proof?.displayName,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )

            proof != null && !thumbnailState.finished -> {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }

            proof != null -> {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Proof image unavailable",
                    tint = MaterialTheme.colorScheme.error,
                )
            }

            else -> {
                Box(
                    modifier = Modifier.matchParentSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun ProofSummaryCard(
    title: String,
    proof: ProofRecord?,
    sent: Boolean,
    onView: () -> Unit,
    onShare: () -> Unit,
    onDeleteReference: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProofThumbnail(
                proof = proof,
                modifier = Modifier.size(82.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (proof == null) {
                    Text(
                        "No proof captured yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        proof.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        formatTimestamp(proof.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (sent) "Marked sent" else "Not marked sent",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (sent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Android sharing may not support Instagram group chats.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    androidx.compose.material3.TextButton(onClick = onView, enabled = proof != null) {
                        Text("View proof")
                    }
                    androidx.compose.material3.TextButton(onClick = onShare, enabled = proof != null) {
                        Text("Share via Android")
                    }
                    androidx.compose.material3.TextButton(onClick = onDeleteReference, enabled = proof != null) {
                        Text("Delete reference")
                    }
                }
            }
        }
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

fun formatTimestamp(timestamp: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm:ss", Locale.UK).format(Date(timestamp))
