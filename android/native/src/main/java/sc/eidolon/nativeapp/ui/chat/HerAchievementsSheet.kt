package sc.eidolon.nativeapp.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sc.eidolon.nativeapp.ui.gifts.AchievementRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HerAchievementsSheet(
    state: ChatState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("${state.character?.name ?: "她"}的成就", style = MaterialTheme.typography.titleMedium)
            when {
                state.achievementsLoading -> CircularProgressIndicator()
                state.achievementsError != null -> {
                    Text(
                        state.achievementsError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetry) { Text("重试") }
                }
                state.herAchievements == null -> Text(
                    "成就暂时没有开放。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    val data = state.herAchievements
                    data.achievements.forEach { achievement ->
                        AchievementRow(achievement)
                    }
                    val complete = data.complete
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(
                            if (complete.completedAt != null) "集齐了" else "集齐",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (complete.completedAt == null && complete.resetCards > 0) {
                            Text(
                                "重置卡 ×${complete.resetCards}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
