package sc.eidolon.nativeapp.ui.gifts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sc.eidolon.nativeapp.model.Achievement
import sc.eidolon.nativeapp.model.AchievementGoal
import sc.eidolon.nativeapp.model.AchievementProgress
import sc.eidolon.nativeapp.model.achievementGoalText
import sc.eidolon.nativeapp.model.achievementName
import sc.eidolon.nativeapp.model.achievementProgressText
import sc.eidolon.nativeapp.model.achievementRewardText
import sc.eidolon.nativeapp.model.achievementStageText

@Composable
fun AchievementRow(
    achievement: Achievement,
    modifier: Modifier = Modifier,
) {
    val next = achievement.stages.firstOrNull { !it.unlocked }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = achievementName(achievement.id),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (next == null) "全部解锁" else achievementRewardText(next.draws),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = when (val progress = achievement.progress) {
                    is AchievementProgress.Count -> {
                        val goal = when (val g = next?.goal) {
                            is AchievementGoal.Count -> " / ${g.value}"
                            else -> ""
                        }
                        "${progress.value}$goal"
                    }
                    is AchievementProgress.Tier -> if (next != null) {
                        achievementStageText(next)
                    } else {
                        achievementProgressText(progress)
                    }
                    AchievementProgress.None -> if (next != null) {
                        achievementStageText(next)
                    } else {
                        "全部解锁"
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                achievement.stages.forEach { stage ->
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(
                                if (stage.unlocked) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape,
                            ),
                    )
                }
            }
        }
    }
}
