package app.courtside.nba

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Courtside() }
    }
}
internal val LocalDark = staticCompositionLocalOf { true }
internal val LocalBottomPadding = staticCompositionLocalOf { 140.dp }
internal val ink: Color @Composable get() = if(LocalDark.current) Color(0xFFF3F4F7) else Color(0xFF18191C)
internal val muted: Color @Composable get() = if(LocalDark.current) Color(0xFFA0A3AC) else Color(0xFF777A83)
internal val surface: Color @Composable get() = if(LocalDark.current) Color(0xFF1A1B20) else Color(0xFFF4F4F7)
internal val sheet: Color @Composable get() = if(LocalDark.current) Color(0xFF151C2B).copy(alpha=.32f) else Color.White.copy(alpha=.32f)
internal val accent: Color @Composable get() = if(LocalDark.current) Color(0xFF9DBBFF) else Color(0xFF2868D9)
internal val line: Color @Composable get() = if(LocalDark.current) Color.White.copy(alpha=.08f) else Color.Black.copy(alpha=.07f)
internal val numericStyle = TextStyle(fontFeatureSettings="tnum")
private data class Route(val page: Int, val sub: String, val game: String) {
    val depth get() = if(sub.isNotBlank() || game.isNotBlank()) 1 else 0
    val key get() = when { game.isNotBlank() -> "game-$game"; sub.isNotBlank() -> sub; else -> "tab-$page" }
}
@Composable private fun Courtside(vm: AppViewModel = viewModel()) {
    val pref by vm.preferences.collectAsStateWithLifecycle()
    val dark = when(pref.theme) { "light" -> false; "system" -> isSystemInDarkTheme(); else -> true }
    val activity = LocalContext.current as? android.app.Activity
    SideEffect { activity?.window?.let { window ->
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    } }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var sub by rememberSaveable { mutableStateOf("") }
    val pageState = rememberSaveableStateHolder()
    val density = LocalDensity.current
    val background = rememberLayerBackdrop()
    val pageBackdrop = rememberLayerBackdrop()
    val navigationBackdrop = rememberCombinedBackdrop(background, pageBackdrop)
    var barHeight by remember { mutableStateOf(90.dp) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentPage by rememberUpdatedState(page)
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        vm.foreground()
        while(true) { delay(60000); vm.poll(currentPage) }
    } }
    BackHandler(vm.detailId.isNotBlank() || sub.isNotBlank() || page != 0) {
        when { vm.detailId.isNotBlank() -> vm.closeGame(); sub.isNotBlank() -> sub = ""; else -> page = 0 }
    }
    val route = Route(page, sub, vm.detailId)
    MiuixTheme(colors = if(dark) darkColorScheme() else lightColorScheme()) {
        CompositionLocalProvider(LocalDark provides dark, LocalGlassBackdrop provides background, LocalBottomPadding provides (barHeight + 18.dp)) {
            Box(Modifier.fillMaxSize()) {
                GlassBackground(background, vm.teams.data.orEmpty().find { it.id == pref.team })
                AnimatedContent(route, modifier = Modifier.fillMaxSize().statusBarsPadding().layerBackdrop(pageBackdrop), contentKey = { it.key }, transitionSpec = {
                    if(targetState.depth == initialState.depth && targetState.depth == 0) {
                        val direction = if(targetState.page > initialState.page) 1 else -1
                        val distance = with(density) { 24.dp.roundToPx() } * direction
                        (slideInHorizontally(tween(240, easing=FastOutSlowInEasing)) { distance } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(240, easing=FastOutSlowInEasing)) { -distance } + fadeOut(tween(140)))
                    } else if(targetState.depth > initialState.depth) {
                        (slideInHorizontally(tween(280, easing=FastOutSlowInEasing)) { it } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(280)) { -it/4 } + fadeOut(tween(200)))
                    } else {
                        (slideInHorizontally(tween(280, easing=FastOutSlowInEasing)) { -it/4 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(280, easing=FastOutSlowInEasing)) { it } + fadeOut(tween(180)))
                    }
                }, label = "page") { target ->
                    when {
                        target.game.isNotBlank() -> DetailPage(vm, target.game)
                        target.sub == "teams" -> TeamsPage(vm, pref, { sub = "" })
                        else -> pageState.SaveableStateProvider("tab-${target.page}") {
                            when(target.page) {
                                0 -> HomePage(vm, pref) { page = 1; vm.scheduleFilter=1; vm.loadDate(vm.currentDate) }
                                1 -> SchedulePage(vm, pref)
                                2 -> StandingsPage(vm, pref)
                                else -> SettingsPage(vm, pref) { sub = "teams" }
                            }
                        }
                    }
                }
                AnimatedVisibility(route.depth == 0, Modifier.align(Alignment.BottomCenter),
                    enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it/3 },
                    exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it/3 }) {
                    Box(Modifier.navigationBarsPadding().padding(bottom=14.dp).fillMaxWidth(.86f).widthIn(max=480.dp)
                        .onSizeChanged { barHeight = with(density) { it.height.toDp() } }) {
                        BottomTabs(page, navigationBackdrop) { page = it }
                    }
                }
            }
        }
    }
}
@Composable private fun BottomTabs(page: Int, backdrop: Backdrop, select: (Int) -> Unit) {
    val sheen by animateFloatAsState((page+.5f)/4, spring(dampingRatio=.8f,stiffness=400f),label="glass-light")
    val position by animateFloatAsState(page.toFloat(),spring(dampingRatio=.82f,stiffness=500f),label="tab-selector")
    val density=LocalDensity.current
    var contentWidth by remember {mutableStateOf(0.dp)}
    var contentHeight by remember {mutableStateOf(48.dp)}
    Box(Modifier.fillMaxWidth().glass(30.dp,backdrop=backdrop,navigation=true,sheen=sheen).padding(4.dp)) {
        if(contentWidth>0.dp) Box(Modifier.offset(x=(contentWidth/4)*position)
            .width(contentWidth/4).height(contentHeight)
            .background(if(LocalDark.current) Color.White.copy(alpha=.12f) else Color.Black.copy(alpha=.075f),RoundedCornerShape(26.dp)))
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).onSizeChanged {
            contentWidth=with(density){it.width.toDp()};contentHeight=with(density){it.height.toDp()}
        }.selectableGroup()) {
            listOf("首页", "赛程", "排名", "设置").forEachIndexed { index, title ->
                val selected = page == index
                val tint by animateColorAsState(if(selected) accent else ink.copy(alpha=.78f), tween(190), label="tab-color")
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val scale by animateFloatAsState(if(pressed) .90f else 1f, spring(dampingRatio=.75f, stiffness=650f), label="tab-press")
                val icon = remember(index) { navIcon(index) }
                Column(Modifier.weight(1f).selectable(selected, role=Role.Tab, interactionSource=interaction, indication=null, onClick={ select(index) })
                    .heightIn(min=48.dp).padding(vertical=3.dp).graphicsLayer { scaleX=scale; scaleY=scale },
                    horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Image(icon, null, Modifier.size(22.dp), colorFilter=ColorFilter.tint(tint))
                    Text(title, color=tint, fontSize=11.sp, lineHeight=15.sp, maxLines=1,
                        fontWeight=if(selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}
private fun navIcon(index: Int): ImageVector = ImageVector.Builder("nav$index", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill=androidx.compose.ui.graphics.SolidColor(Color.Black)) {
        when(index) {
            0 -> { moveTo(3f,10f); lineTo(12f,2f); lineTo(21f,10f); lineTo(21f,22f); lineTo(14f,22f); lineTo(14f,14f); lineTo(10f,14f); lineTo(10f,22f); lineTo(3f,22f); close() }
            1 -> { moveTo(4f,4f); lineTo(20f,4f); lineTo(20f,8f); lineTo(4f,8f); close(); moveTo(4f,10f); lineTo(20f,10f); lineTo(20f,21f); lineTo(4f,21f); close() }
            2 -> { moveTo(3f,12f); lineTo(8f,12f); lineTo(8f,22f); lineTo(3f,22f); close(); moveTo(10f,3f); lineTo(15f,3f); lineTo(15f,22f); lineTo(10f,22f); close(); moveTo(17f,8f); lineTo(22f,8f); lineTo(22f,22f); lineTo(17f,22f); close() }
            3 -> {
                moveTo(10f,2f); lineTo(14f,2f); lineTo(15f,5f); lineTo(18f,6f); lineTo(21f,6f); lineTo(23f,10f); lineTo(20f,12f); lineTo(21f,16f); lineTo(18f,19f); lineTo(15f,18f); lineTo(14f,22f); lineTo(10f,22f); lineTo(9f,19f); lineTo(6f,18f); lineTo(3f,18f); lineTo(1f,14f); lineTo(4f,12f); lineTo(3f,8f); lineTo(6f,5f); lineTo(9f,6f); close()
                moveTo(16f,12f); curveTo(16f,9.8f,14.2f,8f,12f,8f); curveTo(9.8f,8f,8f,9.8f,8f,12f); curveTo(8f,14.2f,9.8f,16f,12f,16f); curveTo(14.2f,16f,16f,14.2f,16f,12f); close()
            }
        }
    }
}.build()
