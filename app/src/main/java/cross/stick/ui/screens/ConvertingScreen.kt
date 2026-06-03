package cross.stick.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ConvertingScreen(
    converted: Int,
    total: Int
) {
    val percentage = if (total > 0) (converted.toFloat() / total) else 0f
    val animatedPercentage by animateFloatAsState(
        targetValue = percentage,
        animationSpec = tween(500)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Decorative cluster
        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .size(100.dp)
                    .padding(8.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ) {}
            Surface(
                modifier = Modifier
                    .size(80.dp)
                    .padding(4.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Style,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Converting Stickers",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = "$converted of $total completed",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        LinearProgressIndicator(
            progress = { animatedPercentage },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Step list
        ConversionStep(
            label = "Image Scaling",
            state = when {
                animatedPercentage > 0.3f -> StepState.DONE
                animatedPercentage > 0.1f -> StepState.ACTIVE
                else -> StepState.WAITING
            }
        )
        ConversionStep(
            label = "WebP Compression",
            state = when {
                animatedPercentage > 0.8f -> StepState.DONE
                animatedPercentage > 0.4f -> StepState.ACTIVE
                else -> StepState.WAITING
            }
        )
        ConversionStep(
            label = "Packaging",
            state = when {
                animatedPercentage >= 1f -> StepState.DONE
                animatedPercentage > 0.8f -> StepState.ACTIVE
                else -> StepState.WAITING
            }
        )
    }
}

enum class StepState { DONE, ACTIVE, WAITING }

@Composable
private fun ConversionStep(label: String, state: StepState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (state) {
            StepState.DONE -> Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            StepState.ACTIVE -> Icon(
                Icons.Default.Sync,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp)
            )
            StepState.WAITING -> Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = when (state) {
                StepState.DONE -> MaterialTheme.colorScheme.primary
                StepState.ACTIVE -> MaterialTheme.colorScheme.onSurface
                StepState.WAITING -> MaterialTheme.colorScheme.outlineVariant
            }
        )
        Spacer(modifier = Modifier.weight(1f))
        if (state == StepState.ACTIVE) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
        }
    }
}
