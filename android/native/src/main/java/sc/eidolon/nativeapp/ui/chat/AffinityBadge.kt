package sc.eidolon.nativeapp.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import sc.eidolon.nativeapp.model.AffinityChange
import sc.eidolon.nativeapp.model.AffinityStatus
import sc.eidolon.nativeapp.model.TIER_LADDER
import sc.eidolon.nativeapp.model.tierLabel
import sc.eidolon.nativeapp.model.tierNote
import sc.eidolon.nativeapp.model.untilText
import sc.eidolon.nativeapp.ui.theme.herColors
import java.time.Instant
import java.time.ZoneId

@Composable
fun AffinityBadge(
    name: String,
    color: String,
    status: AffinityStatus,
    change: AffinityChange?,
    now: Instant,
    zone: ZoneId,
    onGiveGift: () -> Unit,
    onAchievements: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val her = herColors(color)
    val cooling = status.cooldownUntil?.isAfter(now) == true
    val tint = if (cooling) MaterialTheme.colorScheme.onSurfaceVariant else her.mark

    Surface(
        shape = RoundedCornerShape(100.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable { open = true },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = if (cooling) Icons.Outlined.HourglassEmpty else Icons.Filled.Favorite,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = if (cooling) {
                    untilText(status.cooldownUntil, now, zone)
                } else {
                    tierLabel(status.tier).ifBlank { "关系" }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            change?.up?.let { up ->
                Icon(
                    imageVector = if (up) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                    contentDescription = null,
                    tint = if (up) her.mark else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = {
                Text(
                    if (cooling) "$name 现在不想说话" else "你们现在的关系：${tierLabel(status.tier)}",
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (cooling) {
                            "${untilText(status.cooldownUntil, now, zone)} 以后可以再找她。送她一件礼物也可能让她消气。"
                        } else {
                            tierNote(status.tier)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TIER_LADDER.forEach { tier ->
                            val current = !cooling && tier == status.tier
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (current) her.mark else Color.Transparent,
                                modifier = Modifier
                                    .background(
                                        if (current) Color.Transparent
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(8.dp),
                                    ),
                            ) {
                                Text(
                                    text = tierLabel(tier),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (current) Color.White
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                    Text(
                        "她对你的态度会随你说的话变化。低于「信任」时，不联系也会慢慢回到「信任」。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        open = false
                        onGiveGift()
                    },
                ) { Text("送她一件礼物") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            open = false
                            onAchievements()
                        },
                    ) { Text("查看成就") }
                    TextButton(onClick = { open = false }) { Text("知道了") }
                }
            },
        )
    }
}
