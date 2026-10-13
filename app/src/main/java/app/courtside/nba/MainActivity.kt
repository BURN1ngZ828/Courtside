package app.courtside.nba

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Courtside() }
    }
}
private val Accent = Color(0xFF91B5FF)
private val LocalDark = staticCompositionLocalOf { true }
private val ink: Color @Composable get() = if(LocalDark.current) Color(0xFFF0F3FA) else Color(0xFF172135)
private val muted: Color @Composable get() = if(LocalDark.current) Color(0xFF95A3BA) else Color(0xFF607087)
private val surface: Color @Composable get() = if(LocalDark.current) Color(0xFF151F30) else Color(0xFFE9EFF8)

@Composable private fun Courtside(vm: AppViewModel = viewModel()) {
    val pref by vm.preferences.collectAsStateWithLifecycle()
    val dark = when(pref.theme) { "light" -> false; "system" -> isSystemInDarkTheme(); else -> true }
    val activity = LocalContext.current as? android.app.Activity
    SideEffect {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var sub by rememberSaveable { mutableStateOf("") }
    var revealed by remember(vm.detailId) { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentPage by rememberUpdatedState(page)
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.foreground()
            while(true) { delay(60000); vm.poll(currentPage) }
        }
    }
    BackHandler(vm.detailId.isNotBlank() || sub.isNotBlank() || page != 0) {
        when { vm.detailId.isNotBlank() -> vm.closeGame(); sub == "teams" -> sub = "settings"; sub.isNotBlank() -> sub = ""; else -> page = 0 }
    }
    MiuixTheme(colors = if(dark) darkColorScheme() else lightColorScheme()) {
        CompositionLocalProvider(LocalDark provides dark) {
            Box(Modifier.fillMaxSize().background(if(dark) Color(0xFF080E1B) else Color(0xFFF4F7FC)).statusBarsPadding()) {
                when {
                    vm.detailId.isNotBlank() -> DetailPage(vm, pref.spoiler && !revealed, { revealed = true })
                    sub == "teams" -> TeamsPage(vm, pref, { sub = "settings" })
                    sub == "settings" -> SettingsPage(pref, vm, { sub = "" }, { sub = "teams" })
                    page == 0 -> HomePage(vm, pref, { sub = "settings" }, { sub = "teams" }, { page = 1; vm.loadDate(vm.currentDate) })
                    page == 1 -> SchedulePage(vm, pref)
                    else -> StandingsPage(vm, pref)
                }
                if(sub.isEmpty() && vm.detailId.isEmpty()) {
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        FloatingNavigationBar(color = surface.copy(alpha = 0.98f), showDivider = true) {
                            listOf("首页", "赛程", "排名").forEachIndexed { index, title ->
                                Column(Modifier.clip(RoundedCornerShape(28.dp)).background(if(page == index) Accent.copy(alpha = 0.16f) else Color.Transparent).clickable { page = index }.padding(horizontal = 10.dp, vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    FloatingNavigationBarItem(page == index, { page = index }, navIcon(index), title)
                                    Text(title, fontSize = 11.sp, color = if(page == index) ink else muted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
private fun navIcon(index: Int): ImageVector = ImageVector.Builder("nav$index", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = androidx.compose.ui.graphics.SolidColor(Color.Black)) {
        when(index) {
            0 -> { moveTo(3f, 10f); lineTo(12f, 2f); lineTo(21f, 10f); lineTo(21f, 22f); lineTo(14f, 22f); lineTo(14f, 14f); lineTo(10f, 14f); lineTo(10f, 22f); lineTo(3f, 22f); close() }
            1 -> { moveTo(3f, 3f); lineTo(21f, 3f); lineTo(21f, 7f); lineTo(3f, 7f); close(); moveTo(3f, 10f); lineTo(21f, 10f); lineTo(21f, 21f); lineTo(3f, 21f); close() }
            else -> { moveTo(3f, 12f); lineTo(8f, 12f); lineTo(8f, 22f); lineTo(3f, 22f); close(); moveTo(10f, 3f); lineTo(15f, 3f); lineTo(15f, 22f); lineTo(10f, 22f); close(); moveTo(17f, 8f); lineTo(22f, 8f); lineTo(22f, 22f); lineTo(17f, 22f); close() }
        }
    }
}.build()
@Composable private fun Header(title: String, subtitle: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = ink, fontSize = 29.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = muted, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 5.dp))
        }
        Button(onClick = onAction, minWidth = 48.dp, minHeight = 44.dp, insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) { Text(action, color = ink, fontSize = 13.sp) }
    }
}
@Composable private fun Section(title: String, trailing: String = "", click: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(trailing, color = muted, fontSize = 12.sp, modifier = if(click != null) Modifier.clickable(onClick = click).padding(10.dp) else Modifier)
    }
}
@Composable private fun Meta(value: Loaded<*>) {
    val text = when {
        value.loading -> "正在更新…"
        value.error != null -> value.error + if(value.updated > 0) " · 显示缓存" else ""
        value.updated > 0 -> "ESPN · 最近获取 " + DateTimeFormatter.ofPattern("MM/dd HH:mm").format(Instant.ofEpochMilli(value.updated).atZone(ZoneId.systemDefault()))
        else -> ""
    }
    if(text.isNotBlank()) Text(text, color = if(value.error != null) Color(0xFFD99E66) else muted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 3.dp))
}
@Composable private fun Empty(title: String, detail: String) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(22.dp)) {
        Text(title, color = ink, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Text(detail, color = muted, fontSize = 13.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp))
    }
}
@Composable private fun Badge(team: Team, size: Int = 42) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
        Text(team.abbr, color = muted, fontSize = (size / 5).coerceAtLeast(9).sp, fontWeight = FontWeight.Bold)
        if(team.logo.isNotBlank()) AsyncImage(team.logo, team.name, modifier = Modifier.fillMaxSize().padding(3.dp))
    }
}
@Composable private fun Hero(team: Team?, record: Standing?, spoiler: Boolean, onSelect: () -> Unit) {
    val teamColor = runCatching { Color(android.graphics.Color.parseColor("#${team?.color ?: "26487D"}")) }.getOrDefault(Color(0xFF26487D))
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(teamColor.copy(alpha = .55f), Color(0xFF111D35), Color(0xFF0B1426)))).clickable(onClick = onSelect)) {
        Text(team?.abbr ?: "NBA", color = Color.White.copy(alpha = .065f), fontSize = 112.sp, fontWeight = FontWeight.Black, letterSpacing = (-6).sp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp))
        Canvas(Modifier.size(180.dp).align(Alignment.BottomEnd)) {
            val c = Color.White.copy(alpha = .06f)
            drawCircle(c, size.minDimension / 2.5f, style = Stroke(2.dp.toPx()))
            drawLine(c, Offset(size.width / 2, 15f), Offset(size.width / 2, size.height - 15f), 2.dp.toPx())
            drawLine(c, Offset(15f, size.height / 2), Offset(size.width - 15f, size.height / 2), 2.dp.toPx())
        }
        Column(Modifier.padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("MY COURT  /  我的主队", color = Color(0xFFB7C6DD), fontSize = 11.sp, letterSpacing = 2.sp)
                Text("更换 ›", color = Color(0xFFB7C6DD), fontSize = 11.sp)
            }
            Spacer(Modifier.height(23.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(team?.name ?: "选择你的主队", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text(team?.english ?: "每一场，都不想错过。", color = Color(0xFFB8C8E2), fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
                }
                if(team != null) Badge(team, 67)
            }
            Spacer(Modifier.height(23.dp))
            if(team == null) Text("点击开始关注  →", color = Accent, fontSize = 15.sp)
            else if(spoiler) Text("防剧透已开启 · 战绩已隐藏", color = Accent, fontSize = 13.sp)
            else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(record?.let { "${it.wins}–${it.losses}" } ?: "—", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Light, fontFamily = FontFamily.SansSerif)
                    Text("  常规赛胜负", color = Color(0xFF9FAFC8), fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
                Text(if(record != null && record.rank > 0) "${if(record.east) "东部" else "西部"}第 ${record.rank} · 胜率 ${record.pct}" else if(record != null && record.wins + record.losses == 0) "常规赛尚未开始" else "战绩暂不可用", color = Color(0xFF9FAFC8), fontSize = 11.sp)
            }
        }
    }
}
@Composable private fun GameCard(game: Game, spoiler: Boolean, onClick: (() -> Unit)?, prominent: Boolean = false) {
    Card(Modifier.fillMaxWidth().then(if(onClick != null) Modifier.clickable(onClick = onClick) else Modifier), insideMargin = PaddingValues(if(prominent) 20.dp else 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(game.kind + " · " + (game.instant?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("MM/dd HH:mm")) ?: "时间待定"), color = muted, fontSize = 11.sp)
            Text(if(spoiler && (game.live || game.finished)) "结果已隐藏" else game.status, color = if(game.live && !spoiler) Accent else muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(15.dp))
        listOf(game.away to game.awayScore, game.home to game.homeScore).forEachIndexed { index, (team, score) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Badge(team, 35)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(team.name, color = ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(if(index == 0) "客场 · ${team.abbr}" else "主场 · ${team.abbr}", color = muted, fontSize = 10.sp)
                }
                Text(if(spoiler || (!game.live && !game.finished)) "—" else score, color = ink, fontSize = if(prominent) 29.sp else 24.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
            }
        }
        if(prominent && onClick != null) Text("查看比赛详情  ↗", color = Accent, fontSize = 11.sp, modifier = Modifier.padding(top = 13.dp))
    }
}
@Composable private fun HomePage(vm: AppViewModel, pref: Preferences, settings: () -> Unit, choose: () -> Unit, all: () -> Unit) {
    val team = vm.teams.data.orEmpty().find { it.id == pref.team }
    val record = vm.table.data?.rows?.find { it.team.id == pref.team }
    val live = vm.today.data.orEmpty().firstOrNull { it.involves(pref.team) && it.live }
    val next = live ?: vm.schedule.data.orEmpty().firstOrNull { it.state == "pre" && it.instant != null && it.instant > Instant.now() && !it.statusName.contains("POSTPON") && !it.statusName.contains("CANCEL") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 145.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Header("Courtside", "球场边  /  ${vm.season - 1}–${vm.season.toString().takeLast(2)}", "设置", settings) }
        item { Hero(team, record, pref.spoiler, choose); if(team != null && !pref.spoiler) Meta(vm.table) }
        item { Section(if(live != null) "主队正在比赛" else "下一场比赛", "刷新", { vm.refreshHome(true) }) }
        item {
            if(pref.team.isBlank()) Empty("先选一支主队", "关注后，这里会显示正在进行或下一场已公布的比赛。")
            else if(next != null) GameCard(next, pref.spoiler, { vm.openGame(next.id) }, true)
            else Empty(if(vm.schedule.loading) "正在查找比赛" else "暂无可展示的比赛", if(vm.schedule.error != null) "赛程未能完整更新，请稍后重试。" else "下一场安排尚未公布，或当前赛季已结束。")
            if(pref.team.isNotBlank()) Meta(if(live != null) vm.today else vm.schedule)
        }
        item { Section("今日赛场", "全部赛程 ›", all); Text("设备时区 · ${ZoneId.systemDefault().id}", color = muted, fontSize = 10.sp) }
        val games = vm.today.data.orEmpty().sortedByDescending { it.involves(pref.team) }.take(3)
        if(games.isEmpty()) item { Empty(if(vm.today.loading) "正在获取今日赛程" else if(vm.today.error != null) "今日赛程暂不可用" else "今天没有比赛", "可前往赛程页选择其他日期。") }
        items(games, key = { it.id }) { game -> GameCard(game, pref.spoiler, { vm.openGame(game.id) }) }
        item { Meta(vm.today) }
    }
}
@Composable private fun SchedulePage(vm: AppViewModel, pref: Preferences) {
    var only by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 145.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Header("赛程", "每一个比赛日", "刷新", { vm.loadDate(vm.date, true) }) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Button({ vm.loadDate(vm.date.minusDays(1)) }, minWidth = 42.dp, insideMargin = PaddingValues(10.dp)) { Text("‹", color = ink, fontSize = 23.sp) }
                Column(Modifier.clickable { DatePickerDialog(context, { _, y, m, d -> vm.loadDate(LocalDate.of(y, m + 1, d)) }, vm.date.year, vm.date.monthValue - 1, vm.date.dayOfMonth).show() }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(vm.date.format(DateTimeFormatter.ofPattern("M月d日")), color = ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("${vm.date.year} · ${listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[vm.date.dayOfWeek.value - 1]}  ▾", color = muted, fontSize = 11.sp)
                }
                Button({ vm.loadDate(vm.date.plusDays(1)) }, minWidth = 42.dp, insideMargin = PaddingValues(10.dp)) { Text("›", color = ink, fontSize = 23.sp) }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button({ vm.loadDate(LocalDate.now()) }, insideMargin = PaddingValues(12.dp)) { Text("回到今天", color = ink, fontSize = 12.sp) }
                Spacer(Modifier.weight(1f))
                Text("仅看主队  ", color = muted, fontSize = 12.sp)
                Switch(only, { only = it }, enabled = pref.team.isNotBlank())
            }
        }
        item { Meta(vm.games); Text("日期按设备时区 ${ZoneId.systemDefault().id} 显示", color = muted, fontSize = 10.sp) }
        val filtered = vm.games.data.orEmpty().filter { !only || it.involves(pref.team) }
        if(filtered.isEmpty()) item { Empty(if(vm.games.loading) "正在获取赛程" else if(vm.games.error != null) "赛程暂不完整" else "这一天没有${if(only) "主队" else ""}比赛", "点击日期可跳转，或使用左右按钮切换比赛日。") }
        items(filtered, key = { it.id }) { game -> GameCard(game, pref.spoiler, { vm.openGame(game.id) }) }
    }
}
@Composable private fun StandingsPage(vm: AppViewModel, pref: Preferences) {
    var east by rememberSaveable { mutableStateOf(true) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 145.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Header("联盟排名", "${vm.season - 1}–${vm.season.toString().takeLast(2)}  /  常规赛", "刷新", { vm.loadTable(true) }) }
        if(pref.spoiler) item { Empty("排名已隐藏", "防剧透模式同时隐藏胜负战绩和排名，避免间接泄露赛果。") }
        else {
            item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Button({ east = true }) { Text(if(east) "● 东部" else "东部", color = ink) }; Button({ east = false }) { Text(if(!east) "● 西部" else "西部", color = ink) } } }
            item { Meta(vm.table) }
            val rows = vm.table.data?.rows.orEmpty().filter { it.east == east }.sortedBy { if(it.rank > 0) it.rank else Int.MAX_VALUE }
            if(rows.isNotEmpty() && rows.all { it.wins + it.losses == 0 }) item { Empty("常规赛尚未开始", "以下为球队列表，暂无正式排名。季前赛战绩不计入此表。") }
            if(rows.isEmpty()) item { Empty(if(vm.table.loading) "正在获取排名" else "排名暂不可用", "请稍后刷新，未使用推算排名。") }
            item { Row(Modifier.padding(horizontal = 10.dp)) { Text("球队", color = muted, fontSize = 12.sp, modifier = Modifier.weight(1f)); Text("胜–负       胜率", color = muted, fontSize = 12.sp) } }
            items(rows, key = { it.team.id }) { row ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if(row.team.id == pref.team) Accent.copy(alpha = .12f) else surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if(row.rank > 0) row.rank.toString().padStart(2, '0') else "—", color = muted, fontSize = 12.sp, modifier = Modifier.width(27.dp))
                    Badge(row.team, 31)
                    Text(row.team.name + if(row.team.id == pref.team) " ★" else "", color = ink, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    Text("${row.wins}–${row.losses}", color = ink, fontSize = 12.sp, modifier = Modifier.width(48.dp), textAlign = TextAlign.End)
                    Text(if(row.wins + row.losses == 0) "—" else row.pct, color = muted, fontSize = 12.sp, modifier = Modifier.width(48.dp), textAlign = TextAlign.End)
                }
            }
        }
    }
}
@Composable private fun SettingsPage(pref: Preferences, vm: AppViewModel, back: () -> Unit, teams: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Header("设置", "让比赛，按你的方式呈现", "返回", back) }
        item { Card(Modifier.fillMaxWidth().clickable(onClick = teams), insideMargin = PaddingValues(20.dp)) { Text("我的主队", color = ink, fontWeight = FontWeight.Bold); Text((chineseNames[pref.team] ?: "尚未选择") + "   ›", color = muted, modifier = Modifier.padding(top = 10.dp)) } }
        item { Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(20.dp)) {
            Text("外观", color = ink, fontWeight = FontWeight.Bold)
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("dark" to "深色", "light" to "浅色", "system" to "系统").forEach { (key, title) -> Button({ vm.setTheme(key) }, modifier = Modifier.weight(1f), minWidth = 0.dp, insideMargin = PaddingValues(10.dp)) { Text(if(pref.theme == key) "● $title" else title, color = ink, fontSize = 12.sp) } }
            }
        } }
        item { Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("防剧透模式", color = ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Switch(pref.spoiler, vm::setSpoiler) }
            Text("隐藏比分、节次、技术统计和战绩排名。在比赛详情中可临时查看本场结果。", color = muted, fontSize = 13.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 12.dp))
        } }
        item { Empty("数据与更新", "数据来源：ESPN 公开赛事接口。非 ESPN / NBA 官方应用。\n\n不需要账号或 API Key；接口没有稳定性保证，可能受网络、限流或结构变化影响。缓存时间表示成功获取时间，不保证数据与赛场同步。\n\n前台约每分钟检查比赛更新；后台停止轮询。失败时保留真实缓存，可手动刷新。球队标识来自数据源，仅作为球队识别。") }
        item { Text("COURTSIDE  /  球场边\n0.1.0 · 首版测试 APK\nKotlin · Jetpack Compose · miuix 0.9.4\n仅申请联网权限，无广告、无账号、无后台通知。", color = muted, fontSize = 12.sp, lineHeight = 21.sp) }
    }
}
@Composable private fun TeamsPage(vm: AppViewModel, pref: Preferences, back: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Header("选择主队", "一个属于你的主场", "返回", back); Meta(vm.teams) }
        if(vm.teams.data.isNullOrEmpty()) item { Empty("球队资料暂不可用", "检查网络后返回首页刷新，再进入选择。") }
        items(vm.teams.data.orEmpty(), key = { it.id }) { team ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if(pref.team == team.id) Accent.copy(alpha = .15f) else surface).clickable { vm.setTeam(team.id); back() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Badge(team, 42)
                Column(Modifier.weight(1f).padding(start = 14.dp)) { Text(team.name, color = ink, fontSize = 16.sp); Text(team.english, color = muted, fontSize = 11.sp) }
                if(pref.team == team.id) Text("✓", color = Accent, fontSize = 21.sp)
            }
        }
    }
}
@Composable private fun DetailPage(vm: AppViewModel, spoiler: Boolean, reveal: () -> Unit) {
    val detail = vm.detail.data
    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Header("比赛详情", "GAME CENTER", "返回", vm::closeGame) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Meta(vm.detail) }; Button({ vm.openGame(vm.detailId, true) }, insideMargin = PaddingValues(10.dp)) { Text("刷新", color = ink, fontSize = 12.sp) } } }
        if(detail == null) item { Empty(if(vm.detail.loading) "正在获取比赛" else "比赛详情暂不可用", "可刷新重试；未使用演示数据替代。") }
        else {
            item { GameCard(detail.game, spoiler, null, true); if(detail.game.venue.isNotBlank()) Text(detail.game.venue, color = muted, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp)) }
            if(spoiler) item { Empty("本场结果已隐藏", "查看后仅在当前详情页显示；离开页面会恢复防剧透。停留期间的新比分也会显示。"); Spacer(Modifier.height(12.dp)); Button(reveal, Modifier.fillMaxWidth()) { Text("查看本场结果", color = ink) } }
            else {
                item { Section("每节比分") }
                item {
                    val g = detail.game
                    val count = maxOf(g.homePeriods.size, g.awayPeriods.size)
                    if(count == 0) Empty("暂无分节数据", "比赛未开始或来源尚未提供。")
                    else Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                        Column(Modifier.horizontalScroll(rememberScrollState())) {
                            Row { StatCell("球队", 64); repeat(count) { StatCell(if(it < 4) "Q${it + 1}" else "OT${it - 3}", 45) } }
                            listOf(g.away to g.awayPeriods, g.home to g.homePeriods).forEach { (t, values) -> Row { StatCell(t.abbr, 64); repeat(count) { StatCell(values.getOrElse(it) { "—" }, 45) } } }
                        }
                    }
                }
                if(detail.game.state != "pre") {
                    item { Section("球队统计", "${detail.game.away.abbr}  /  ${detail.game.home.abbr}") }
                    item {
                        Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(18.dp)) {
                            detail.stats.forEach { stat ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                                    Text(stat.away, color = ink, modifier = Modifier.weight(1f))
                                    Text(stat.label, color = muted, fontSize = 13.sp)
                                    Text(stat.home, color = ink, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                }
                            }
                        }
                    }
                    detail.players.forEach { team ->
                        item { Section(team.team.name); Text("球员统计 · 左右滑动查看更多", color = muted, fontSize = 11.sp) }
                        item {
                            val columns = listOf("MIN" to "时间", "PTS" to "得分", "REB" to "篮板", "AST" to "助攻", "FG" to "投篮", "3PT" to "三分", "+/-" to "+/−")
                            Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(14.dp)) {
                                Column(Modifier.horizontalScroll(rememberScrollState())) {
                                    Row { StatCell("球员", 150); columns.forEach { StatCell(it.second, 57) } }
                                    team.players.forEach { p -> Row { StatCell(p.name + if(p.dnp) " · 未上场" else "", 150); columns.forEach { StatCell(if(p.dnp) "—" else p.cells[it.first] ?: "—", 57) } } }
                                }
                            }
                        }
                    }
                    if(detail.players.isEmpty()) item { Empty("暂无球员统计", "来源尚未提供本场球员数据。") }
                }
            }
        }
    }
}
@Composable private fun StatCell(value: String, width: Int) { Text(value, color = ink, fontSize = 12.sp, modifier = Modifier.width(width.dp).padding(vertical = 10.dp, horizontal = 3.dp)) }
