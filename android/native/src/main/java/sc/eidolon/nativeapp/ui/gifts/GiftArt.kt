package sc.eidolon.nativeapp.ui.gifts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import sc.eidolon.nativeapp.model.GiftInfo

@Composable
fun GiftArt(
    baseUrl: String,
    gift: GiftInfo?,
    modifier: Modifier = Modifier,
    resetCard: Boolean = false,
) {
    val url = when {
        resetCard -> "$baseUrl/reset-card/reset-card.webp"
        gift != null -> "$baseUrl/gifts/${gift.id}.webp"
        else -> null
    }
    Box(
        modifier = modifier.background(
            MaterialTheme.colorScheme.surfaceVariant,
            RoundedCornerShape(12.dp),
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = if (resetCard) "重置卡" else gift?.name,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                text = if (resetCard) "重置卡" else gift?.name?.take(2).orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun GiftThumb(baseUrl: String, giftId: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            MaterialTheme.colorScheme.surfaceVariant,
            RoundedCornerShape(8.dp),
        ),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = "$baseUrl/gifts/$giftId.webp",
            contentDescription = null,
            modifier = Modifier.fillMaxSize().padding(2.dp),
            contentScale = ContentScale.Fit,
        )
    }
}
