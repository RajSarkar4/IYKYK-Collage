package com.iykyk.collage.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iykyk.collage.domain.model.PersonCluster
import com.iykyk.collage.domain.model.ProcessingResult
import com.iykyk.collage.ui.theme.Cyan400
import com.iykyk.collage.ui.theme.Indigo500
import com.iykyk.collage.ui.theme.Pink500
import com.iykyk.collage.ui.theme.Slate800
import com.iykyk.collage.ui.theme.Slate900
import com.iykyk.collage.ui.theme.TextPrimary
import com.iykyk.collage.ui.theme.TextSecondary

@Composable
fun ResultsScreen(
    result: ProcessingResult,
    onViewCollage: () -> Unit
) {
    var isDebugMode by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate900)
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Detected People",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${result.clusters.size} distinct identities found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

                // Debug Mode Toggle (Correction #19)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = "Debug Mode",
                        tint = if (isDebugMode) Pink500 else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(
                        checked = isDebugMode,
                        onCheckedChange = { isDebugMode = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Pink500,
                            checkedTrackColor = Slate800
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isDebugMode) {
                DebugStatsCard(result = result)
                Spacer(modifier = Modifier.height(16.dp))
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(result.clusters) { cluster ->
                    PersonClusterCard(cluster = cluster, isDebugMode = isDebugMode)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onViewCollage,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Indigo500,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "View Story Collage",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DebugStatsCard(result: ProcessingResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Pipeline Debug Metrics",
                style = MaterialTheme.typography.titleLarge,
                color = Pink500,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "• Sampled Frames: ${result.totalSampledFrames}", color = TextPrimary)
            Text(text = "• Valid Face Observations: ${result.totalDetectedFaces}", color = TextPrimary)
            Text(text = "• Clustered Identities: ${result.clusters.size}", color = TextPrimary)
            Text(text = "• Video Duration: ${result.videoDurationMs / 1000f}s", color = TextPrimary)
        }
    }
}

@Composable
private fun PersonClusterCard(cluster: PersonCluster, isDebugMode: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val repBitmap = cluster.representativeBitmap

            if (repBitmap != null && !repBitmap.isRecycled) {
                val displayBitmap = remember(repBitmap, cluster.representativeObservation) {
                    cropBestFrameThumbnail(repBitmap, cluster.representativeObservation)
                }
                Image(
                    bitmap = displayBitmap.asImageBitmap(),
                    contentDescription = cluster.label,
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Indigo500),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = cluster.label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${cluster.appearanceCount} ${if (cluster.appearanceCount == 1) "Appearance" else "Appearances"}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Cyan400,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• ${cluster.totalObservationCount} detections",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

                if (isDebugMode) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Best Frame Timestamp: ${cluster.representativeObservation.timestampMs} ms",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Pink500,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

private fun cropBestFrameThumbnail(
    sourceBitmap: Bitmap,
    obs: com.iykyk.collage.domain.model.FaceObservation
): Bitmap {
    return try {
        val box = obs.boundingBox
        val scaleX = if (obs.frameWidth > 0) sourceBitmap.width.toFloat() / obs.frameWidth else 1.0f
        val scaleY = if (obs.frameHeight > 0) sourceBitmap.height.toFloat() / obs.frameHeight else 1.0f

        val faceW = box.width() * scaleX
        val faceH = box.height() * scaleY
        val centerX = box.centerX() * scaleX
        val centerY = box.centerY() * scaleY

        val marginW = faceW * 1.0f
        val marginH = faceH * 1.5f

        val left = (centerX - faceW / 2f - marginW).toInt().coerceIn(0, sourceBitmap.width - 1)
        val top = (centerY - faceH / 2f - marginH).toInt().coerceIn(0, sourceBitmap.height - 1)
        val right = (centerX + faceW / 2f + marginW).toInt().coerceIn(left + 1, sourceBitmap.width)
        val bottom = (centerY + faceH / 2f + marginH * 2.0f).toInt().coerceIn(top + 1, sourceBitmap.height)

        Bitmap.createBitmap(sourceBitmap, left, top, right - left, bottom - top)
    } catch (_: Exception) {
        sourceBitmap
    }
}
