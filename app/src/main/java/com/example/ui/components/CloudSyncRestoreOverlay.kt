package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cloud.CloudSyncStage
import com.example.ui.CloudSyncUiState
import com.example.ui.theme.IndBackground
import com.example.ui.theme.IndBlue
import com.example.ui.theme.IndCyan
import com.example.ui.theme.IndTextPrimary
import com.example.ui.theme.IndTextSecondary

@Composable
fun CloudSyncRestoreOverlay(
    state: CloudSyncUiState,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "cloudPulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "pulse"
    )

    val progress = when (state.stage) {
        CloudSyncStage.CONNECTING -> 0.08f
        CloudSyncStage.DOWNLOADING_EXPENSES -> 0.22f
        CloudSyncStage.DOWNLOADING_SALARY -> 0.38f
        CloudSyncStage.DOWNLOADING_CATEGORIES -> 0.48f
        CloudSyncStage.DOWNLOADING_LOANS -> 0.62f
        CloudSyncStage.DOWNLOADING_BANK -> 0.76f
        CloudSyncStage.UPLOADING -> 0.88f
        CloudSyncStage.FINISHING -> 0.95f
        CloudSyncStage.SUCCESS -> 1f
        CloudSyncStage.ERROR -> 0f
    }

    val done = state.stage == CloudSyncStage.SUCCESS

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(IndBackground.copy(alpha = 0.97f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .padding(28.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            Brush.linearGradient(listOf(IndBlue, IndCyan)),
                            RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (done) {
                        Icon(
                            Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    } else {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f + pulse * 0.15f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Text(
                    text = if (done) "Backup restored" else "Downloading your data",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = IndTextPrimary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = state.statusLine.ifBlank {
                        if (done) "Opening your ledger…" else "Please keep the app open"
                    },
                    fontSize = 14.sp,
                    color = IndTextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                if (state.restoredCount > 0 && done) {
                    Text(
                        text = "${state.restoredCount} items synced from cloud",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = IndBlue
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (!done) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .alpha(0.9f),
                        color = IndBlue,
                        trackColor = IndBlue.copy(alpha = 0.15f)
                    )
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.5.dp,
                        color = IndBlue
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = IndBlue,
                        trackColor = IndBlue.copy(alpha = 0.15f)
                    )
                }

                state.errorMessage?.let { err ->
                    Text(
                        text = err,
                        fontSize = 12.sp,
                        color = Color(0xFFC62828),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
