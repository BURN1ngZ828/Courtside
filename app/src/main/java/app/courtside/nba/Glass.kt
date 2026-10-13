package app.courtside.nba

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults

internal val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop> { emptyBackdrop() }
internal val homeColor: Color @Composable get() = if(LocalDark.current) Color(0xFF90B8FF) else Color(0xFF245EAF)
internal val awayColor: Color @Composable get() = if(LocalDark.current) Color(0xFFFFB67B) else Color(0xFFAC4B15)
@Composable internal fun teamInk(game: Game, team: Team) = if(team.id == game.home.id) homeColor else awayColor

/** Cards sample the background only. The floating bar samples the separately captured page.
 * Keeping those layers separate avoids recursive captures and expensive nested blur passes. */
@Composable internal fun Modifier.glass(
    radius: Dp = 20.dp,
    tint: Color = surface,
    backdrop: Backdrop = LocalGlassBackdrop.current,
    navigation: Boolean = false,
    sheen: Float = .25f
): Modifier {
    val dark = LocalDark.current
    val shape = RoundedCornerShape(radius)
    val base = if(tint.alpha < .6f) tint.compositeOver(surface) else tint
    val material = base.copy(alpha = if(navigation) { if(dark) .50f else .58f } else { if(dark) .80f else .79f })
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            if(Build.VERSION.SDK_INT >= 31) blur((if(navigation) 12.dp else 8.dp).toPx())
            if(Build.VERSION.SDK_INT >= 33) lens((if(navigation) 12.dp else 6.dp).toPx(), (if(navigation) 14.dp else 7.dp).toPx())
        },
        highlight = { Highlight(width=.45.dp, alpha=if(navigation) .23f else if(dark) .12f else .19f) },
        shadow = { Shadow(radius=if(navigation) 12.dp else 6.dp, color=Color.Black.copy(alpha=if(dark) .12f else .035f)) },
        onDrawSurface = {
            drawRect(material)
            drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha=if(dark) .035f else .07f), Color.Transparent),
                center=Offset(size.width*sheen, 0f), radius=size.width.coerceAtLeast(1f)*.8f))
        }
    )
}

@Composable internal fun Card(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    insideMargin: PaddingValues = PaddingValues(0.dp),
    colors: CardColors = CardDefaults.defaultColors(color=surface),
    content: @Composable ColumnScope.() -> Unit
) {
    MiuixCard(modifier.glass(cornerRadius,colors.color), cornerRadius, insideMargin,
        CardDefaults.defaultColors(color=Color.Transparent), content)
}

@Composable internal fun GlassBackground(backdrop: LayerBackdrop, team: Team?) {
    val dark = LocalDark.current
    val seed = runCatching { Color(android.graphics.Color.parseColor("#${team?.color ?: "648CCB"}")) }.getOrDefault(Color(0xFF648CCB))
    Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
        drawRect(if(dark) Color(0xFF0D1320) else Color(0xFFF0F3F8))
        drawRect(Brush.radialGradient(listOf(seed.copy(alpha=if(dark) .25f else .13f), Color.Transparent),
            center=Offset(size.width*.90f,size.height*.15f),radius=size.width*1.15f))
        drawRect(Brush.radialGradient(listOf(Color(0xFF7189E5).copy(alpha=if(dark) .13f else .085f),Color.Transparent),
            center=Offset(size.width*.10f,size.height*.76f),radius=size.width*.95f))
    }
}
