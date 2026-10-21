package sc.eidolon.nativeapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import sc.eidolon.nativeapp.core.ColorMath

/** 列表和聊天顶栏共用的角色头像共享元素修饰符；不参与共享时传 { Modifier }。 */
typealias SharedAvatarModifier = @Composable (characterId: String) -> Modifier

@Composable
fun CharacterAvatar(
    name: String,
    color: String,
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = name,
            modifier = modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        return
    }
    val background = Color(ColorMath.parseHex(color) ?: 0xFF7FB4FF.toInt())
    val foreground = Color(
        ColorMath.readableOn(
            background.toArgb(),
            listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt()),
        ),
    )
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(1),
            color = foreground,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = (size.value * 0.4f).sp),
        )
    }
}
