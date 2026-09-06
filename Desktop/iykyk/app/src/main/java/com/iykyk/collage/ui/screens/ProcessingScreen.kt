package com.iykyk.collage.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iykyk.collage.domain.model.ProcessingState
import com.iykyk.collage.ui.theme.Cyan400
import com.iykyk.collage.ui.theme.Indigo500
import com.iykyk.collage.ui.theme.Slate800
import com.iykyk.collage.ui.theme.Slate900
import com.iykyk.collage.ui.theme.TextPrimary
import com.iykyk.collage.ui.theme.TextSecondary

@Composable
fun ProcessingScreen(
    state: ProcessingState.Processing,
    onCancel: () -> Unit
) {
    val progressPct = (state.progress * 100).toInt()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate900)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Circular progress ring
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(140.dp)
            ) {
                CircularProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxSize(),
                    color = Indigo500,
                    strokeWidth = 10.dp,
                    trackColor = Slate800
                )
                Text(
                    text = "$progressPct%",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = state.stage.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = state.stageMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Cyan400,
                trackColor = Slate800
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Cancel Processing",
                    color = Color.Red,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

