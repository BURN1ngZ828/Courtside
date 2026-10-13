@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package app.courtside.nba

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.Text
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable internal fun Header(title: String, subtitle: String, action: String="", onAction: (() -> Unit)?=null) {
    Row(Modifier.fillMaxWidth().padding(top=18.dp, bottom=14.dp), verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color=ink, fontSize=34.sp, fontWeight=FontWeight.Bold, letterSpacing=(-1).sp)
            if(subtitle.isNotBlank()) Text(subtitle, color=muted, fontSize=12.sp, modifier=Modifier.padding(top=6.dp))
        }
        if(onAction != null) Button(onAction, minWidth=44.dp, insideMargin=PaddingValues(12.dp)) { Text(action, color=ink, fontSize=12.sp) }
    }
}
@Composable private fun Section(title: String, trailing: String="", click: (() -> Unit)?=null) {
    Row(Modifier.fillMaxWidth().padding(top=8.dp, bottom=8.dp), verticalAlignment=Alignment.CenterVertically) {
        Text(title, color=ink, fontSize=22.sp, fontWeight=FontWeight.Bold, modifier=Modifier.weight(1f))
        if(trailing.isNotBlank()) Text(trailing, color=muted, fontSize=12.sp, modifier=if(click!=null) Modifier.clickable(onClick=click).padding(8.dp) else Modifier)
    }
}
@Composable private fun Meta(value: Loaded<*>) {
    val stamp = if(value.updated>0) DateTimeFormatter.ofPattern("MM/dd HH:mm").format(Instant.ofEpochMilli(value.updated).atZone(ZoneId.systemDefault())) else ""
    val message = when {
        value.loading -> "正在更新" + if(stamp.isNotBlank()) " · 上次 $stamp" else "…"
        value.error != null -> value.error + if(stamp.isNotBlank()) "\n缓存获取于 $stamp" else ""
        stamp.isNotBlank() -> "ESPN · 最近获取 $stamp"
        else -> ""
    }
    if(message.isNotBlank()) Text(message, color=if(value.error!=null) Color(0xFFB88749) else muted, fontSize=11.sp, lineHeight=17.sp, modifier=Modifier.padding(vertical=4.dp))
}
@Composable private fun Empty(title: String, note: String) {
    Card(Modifier.fillMaxWidth(), cornerRadius=22.dp, colors=CardDefaults.defaultColors(color=surface), insideMargin=PaddingValues(20.dp)) {
        Text(title, color=ink, fontSize=17.sp, fontWeight=FontWeight.Medium)
        Text(note, color=muted, fontSize=12.sp, lineHeight=19.sp, modifier=Modifier.padding(top=8.dp))
    }
}
@Composable internal fun Badge(team: Team, size: Int=32) {
    val logo = teamLogoResource(team.id)
    Box(Modifier.size(size.dp), contentAlignment=Alignment.Center) {
        if(logo!=null) Image(painterResource(logo), team.name, Modifier.fillMaxSize())
        else Text(team.abbr, color=muted, fontSize=10.sp, maxLines=1)
    }
}
@Composable private fun Rule(modifier: Modifier=Modifier) { Box(modifier.fillMaxWidth().height(1.dp).background(line)) }
@Composable private fun Segments(tabs: List<String>, selected: Int, onSelect: (Int)->Unit) {
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    TabRowWithContour(tabs, selected.coerceIn(tabs.indices), onSelect, Modifier.fillMaxWidth(),
        colors=TabRowDefaults.tabRowColors(backgroundColor=surface, contentColor=muted, selectedBackgroundColor=sheet, selectedContentColor=accent),
        minWidth=60.dp, maxWidth=500.dp, height=(44 * scale).dp, cornerRadius=14.dp)
}
@Composable internal fun HomePage(vm: AppViewModel, pref: Preferences, all: ()->Unit) {
    val team = vm.teams.data.orEmpty().find { it.id==pref.team }
    val record = vm.table.data?.rows?.find { it.team.id==pref.team }
    val cards = homeGames(vm.today.data.orEmpty(), vm.schedule.data.orEmpty(), pref.team, Instant.now())
    val today = vm.today.data.orEmpty().sortedByDescending { it.involves(pref.team) }.take(4)
    LazyColumn(Modifier.fillMaxSize(), contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=LocalBottomPadding.current), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Header("我的主场", "COURTSIDE  ·  ${vm.season-1}–${vm.season.toString().takeLast(2)}", "刷新") { vm.refreshHome(true) } }
        item {
            if(team==null) Empty("选择你的主队", "在底部「设置」中选择球队，关注它的每一场比赛。")
            else Row(Modifier.fillMaxWidth().padding(vertical=6.dp), verticalAlignment=Alignment.CenterVertically) {
                Badge(team,44)
                Column(Modifier.weight(1f).padding(horizontal=12.dp)) {
                    Text(team.shortName, color=ink, fontSize=22.sp, fontWeight=FontWeight.SemiBold)
                    Text(team.english, color=muted, fontSize=11.sp, modifier=Modifier.padding(top=3.dp))
                }
                Column(horizontalAlignment=Alignment.End) {
                    Text(record?.let { "${it.wins}–${it.losses}" } ?: "—", color=ink, fontSize=24.sp, fontWeight=FontWeight.Bold)
                    Text(if(record!=null && record.rank>0) "${if(record.east) "东部" else "西部"}第${record.rank}" else "常规赛战绩", color=muted, fontSize=10.sp)
                }
            }
        }
        if(team!=null) {
            item { Section("主队赛场"); if(record!=null && record.wins+record.losses==0) Text("常规赛尚未开始", color=muted, fontSize=11.sp) }
            item {
                if(cards.isEmpty()) Empty(if(vm.schedule.loading) "正在查找比赛" else "暂无比赛安排", if(vm.schedule.error!=null) "赛程未完整更新，请稍后刷新。" else "来源尚未提供下一场安排。")
                else {
                    val pager = rememberPagerState { cards.size }
                    HorizontalPager(pager, contentPadding=PaddingValues(end=24.dp), pageSpacing=12.dp, key={cards[it].id}) { index ->
                        val game=cards[index]
                        Spotlight(game, team, if(game.live) "正在进行" else if(game.finished) "最近一场" else "下一场比赛") { vm.openGame(game.id) }
                    }
                    Row(Modifier.fillMaxWidth().padding(top=10.dp), horizontalArrangement=Arrangement.Center) {
                        repeat(cards.size) { index -> Box(Modifier.padding(horizontal=3.dp).size(if(pager.currentPage==index) 6.dp else 4.dp).clip(CircleShape).background(if(pager.currentPage==index) accent else muted.copy(alpha=.3f))) }
                    }
                }
                Meta(vm.schedule)
            }
        }
        item { Section("今日赛事", "查看全部 ›", all); Text("${vm.currentDate.format(DateTimeFormatter.ofPattern("M月d日"))} · ${ZoneId.systemDefault().id}", color=muted, fontSize=11.sp) }
        item {
            if(today.isEmpty()) Empty(if(vm.today.loading) "正在获取赛程" else "今日暂无赛事", "可在赛程页查看其他日期。")
            else GameGroup(today) { vm.openGame(it.id) }
            Meta(vm.today)
        }
    }
}
@Composable private fun Spotlight(game: Game, team: Team, label: String, open: ()->Unit) {
    val color = runCatching { Color(android.graphics.Color.parseColor("#${team.color}")) }.getOrDefault(accent)
    val dark=LocalDark.current
    val end = if(dark) Color(0xFF171B27) else Color(0xFFEDF0F7)
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(color.copy(alpha=if(dark) .42f else .18f),end))).clickable(onClick=open)) {
        Text(team.abbr, color=ink.copy(alpha=.045f), fontSize=92.sp, fontWeight=FontWeight.Black, modifier=Modifier.align(Alignment.BottomEnd).padding(end=12.dp))
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                Text(label, color=accent, fontSize=12.sp, fontWeight=FontWeight.SemiBold)
                Text(game.kind, color=muted, fontSize=11.sp)
            }
            Spacer(Modifier.height(17.dp))
            listOf(game.away to game.awayScore, game.home to game.homeScore).forEach { (t,score) ->
                Row(Modifier.fillMaxWidth().padding(vertical=6.dp), verticalAlignment=Alignment.CenterVertically) {
                    Badge(t,36)
                    Text(t.shortName, color=ink, fontSize=19.sp, fontWeight=FontWeight.SemiBold, modifier=Modifier.weight(1f).padding(start=12.dp))
                    Text(if(game.live||game.finished) score else "—", color=ink, fontSize=25.sp, fontWeight=FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(15.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                Text(game.instant?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("M月d日 HH:mm")) ?: "时间待定", color=muted, fontSize=12.sp)
                Text(if(game.live) game.status else "${game.status}  ›", color=if(game.live) accent else muted, fontSize=11.sp)
            }
        }
    }
}
@Composable private fun GameGroup(games: List<Game>, open: (Game)->Unit) {
    Card(Modifier.fillMaxWidth().border(1.dp,line,RoundedCornerShape(22.dp)), cornerRadius=22.dp, colors=CardDefaults.defaultColors(color=sheet)) {
        games.forEachIndexed { i,g ->
            CompactGame(g) { open(g) }
            if(i<games.lastIndex) Rule(Modifier.padding(horizontal=14.dp))
        }
    }
}
@Composable private fun CompactGame(g: Game, open: ()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=open).padding(horizontal=12.dp, vertical=16.dp), verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.width(54.dp)) {
            Text(if(g.live) (if(g.period>4) "加时" else "第${g.period}节") else g.instant?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "待定", color=if(g.live) accent else ink, fontSize=13.sp, fontWeight=FontWeight.Medium)
            Text(if(g.live) g.clock else g.kind, color=muted, fontSize=10.sp, modifier=Modifier.padding(top=6.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal=8.dp), verticalArrangement=Arrangement.spacedBy(9.dp)) {
            listOf(g.away to g.awayScore, g.home to g.homeScore).forEach { (t,s) ->
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Badge(t,25)
                    Text(t.shortName, color=ink, fontSize=14.sp, modifier=Modifier.weight(1f).padding(start=7.dp,end=4.dp), maxLines=1, overflow=TextOverflow.Ellipsis)
                    Text(if(g.live||g.finished) s else "—", color=ink, fontSize=16.sp,
                        fontWeight=if(isWinner(g,t.id)) FontWeight.Bold else FontWeight.Normal, textAlign=TextAlign.End, modifier=Modifier.widthIn(min=30.dp))
                }
            }
        }
        Column(Modifier.width(48.dp), horizontalAlignment=Alignment.End) {
            Text(if(g.live) "进行中" else g.status, color=if(g.live) accent else muted, fontSize=11.sp, textAlign=TextAlign.End)
        }
    }
}
@Composable internal fun SchedulePage(vm: AppViewModel, pref: Preferences) {
    var only by rememberSaveable { mutableStateOf(false) }
    val context=LocalContext.current
    val filtered=vm.games.data.orEmpty().filter { !only || it.involves(pref.team) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=LocalBottomPadding.current), verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Header("赛程", "每一个比赛日", "刷新") { vm.loadDate(vm.date,true) } }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween) {
                Button({vm.loadDate(vm.date.minusDays(1))}, minWidth=42.dp, insideMargin=PaddingValues(10.dp)) { Text("‹",color=ink,fontSize=24.sp) }
                Column(Modifier.clickable { DatePickerDialog(context,{_,y,m,d->vm.loadDate(LocalDate.of(y,m+1,d))},vm.date.year,vm.date.monthValue-1,vm.date.dayOfMonth).show() }.padding(8.dp), horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(vm.date.format(DateTimeFormatter.ofPattern("M月d日"))+"  ▾",color=ink,fontSize=22.sp,fontWeight=FontWeight.SemiBold)
                    Text("${vm.date.year} · ${weekday(vm.date)}",color=muted,fontSize=11.sp)
                }
                Button({vm.loadDate(vm.date.plusDays(1))},minWidth=42.dp,insideMargin=PaddingValues(10.dp)) { Text("›",color=ink,fontSize=24.sp) }
            }
        }
        item {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Button({vm.loadDate(LocalDate.now())},insideMargin=PaddingValues(10.dp)) { Text("今天",color=ink,fontSize=12.sp) }
                Spacer(Modifier.weight(1f))
                Text("仅看主队  ",color=muted,fontSize=12.sp)
                Switch(only,{only=it},enabled=pref.team.isNotBlank())
            }
        }
        item { Meta(vm.games); Text("按设备时区 ${ZoneId.systemDefault().id} 显示",color=muted,fontSize=10.sp) }
        item { Text((if(vm.date==vm.currentDate) "今天" else vm.date.format(DateTimeFormatter.ofPattern("M月d日")))+" · ${weekday(vm.date)}",color=muted,fontSize=12.sp,fontWeight=FontWeight.Medium) }
        item {
            if(filtered.isEmpty()) Empty(if(vm.games.loading) "正在获取赛程" else if(vm.games.error!=null) "赛程暂不可用" else "这一天没有${if(only) "主队" else ""}比赛","点击日期或左右按钮查看其他比赛日。")
            else GameGroup(filtered) { vm.openGame(it.id) }
        }
    }
}
private fun weekday(day: LocalDate)=listOf("周一","周二","周三","周四","周五","周六","周日")[day.dayOfWeek.value-1]
@Composable internal fun StandingsPage(vm: AppViewModel,pref: Preferences) {
    var conference by rememberSaveable { mutableIntStateOf(0) }
    val rows=vm.table.data?.rows.orEmpty().filter { it.east==(conference==0) }.sortedBy { if(it.rank>0) it.rank else Int.MAX_VALUE }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=LocalBottomPadding.current),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Header("联盟排名","${vm.season-1}–${vm.season.toString().takeLast(2)} · 常规赛","刷新") {vm.loadTable(true)} }
        item { Segments(listOf("东部","西部"),conference) {conference=it} }
        item { Meta(vm.table) }
        if(rows.isNotEmpty() && rows.all {it.wins+it.losses==0}) item { Empty("常规赛尚未开始","以下为球队列表，暂无正式排名。季前赛战绩不计入排名。") }
        item {
            if(rows.isEmpty()) Empty(if(vm.table.loading) "正在获取排名" else "排名暂不可用","请稍后刷新。")
            else Card(Modifier.fillMaxWidth().border(1.dp,line,RoundedCornerShape(22.dp)),cornerRadius=22.dp,colors=CardDefaults.defaultColors(color=sheet)) {
                Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=13.dp)) {
                    Text("球队",color=muted,fontSize=11.sp,modifier=Modifier.weight(1f))
                    Text("胜–负",color=muted,fontSize=11.sp,modifier=Modifier.width(52.dp),textAlign=TextAlign.End)
                    Text("胜率",color=muted,fontSize=11.sp,modifier=Modifier.width(48.dp),textAlign=TextAlign.End)
                }
                Rule()
                rows.forEachIndexed { i,r ->
                    Row(Modifier.fillMaxWidth().background(if(r.team.id==pref.team) accent.copy(alpha=.08f) else Color.Transparent).padding(horizontal=12.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(if(r.rank>0) r.rank.toString() else "—",color=muted,fontSize=12.sp,modifier=Modifier.width(24.dp))
                        Badge(r.team,28)
                        Text(r.team.shortName+if(r.team.id==pref.team) " ★" else "",color=ink,fontSize=14.sp,modifier=Modifier.weight(1f).padding(start=8.dp),maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text("${r.wins}–${r.losses}",color=ink,fontSize=12.sp,modifier=Modifier.width(52.dp),textAlign=TextAlign.End)
                        Text(if(r.wins+r.losses==0) "—" else r.pct,color=muted,fontSize=12.sp,modifier=Modifier.width(48.dp),textAlign=TextAlign.End)
                    }
                    if(i<rows.lastIndex) Rule(Modifier.padding(start=44.dp,end=12.dp))
                }
            }
        }
    }
}
@Composable internal fun SettingsPage(vm: AppViewModel,pref: Preferences,teams: ()->Unit) {
    var about by rememberSaveable {mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=LocalBottomPadding.current),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Header("设置","你的球队，你的主场")}
        item {Card(Modifier.fillMaxWidth().clickable(onClick=teams),cornerRadius=22.dp,colors=CardDefaults.defaultColors(color=surface),insideMargin=PaddingValues(20.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                vm.teams.data.orEmpty().find {it.id==pref.team}?.let {Badge(it,40);Spacer(Modifier.width(14.dp))}
                Column(Modifier.weight(1f)) {Text("我的主队",color=ink,fontSize=17.sp,fontWeight=FontWeight.SemiBold);Text(chineseNames[pref.team] ?: "选择你关注的球队",color=muted,fontSize=12.sp,modifier=Modifier.padding(top=6.dp))}
                Text("›",color=muted,fontSize=24.sp)
            }
        }}
        item {Section("外观");Segments(listOf("深色","浅色","系统"),listOf("dark","light","system").indexOf(pref.theme).coerceAtLeast(0)) {vm.setTheme(listOf("dark","light","system")[it])}}
        item {Card(Modifier.fillMaxWidth(),cornerRadius=22.dp,colors=CardDefaults.defaultColors(color=surface),insideMargin=PaddingValues(20.dp)) {
            Row(Modifier.fillMaxWidth().clickable {about=!about},verticalAlignment=Alignment.CenterVertically) {
                Text("关于与数据来源",color=ink,fontSize=17.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f));Text(if(about) "⌃" else "⌄",color=muted,fontSize=20.sp)
            }
            Text("Courtside 0.2.0 · ESPN",color=muted,fontSize=12.sp,modifier=Modifier.padding(top=8.dp))
            AnimatedVisibility(about) {Text("真实赛事数据来自 ESPN 公开接口。非 ESPN / NBA 官方应用。\n\n最近获取时间表示接口成功响应的时间，不保证与赛场同步。失败时保留真实缓存，前台按需刷新，后台停止持续轮询。\n\n30 队图标和球员中文名内置于应用。未收录的球员保留英文名。\n\nKotlin · Jetpack Compose · miuix 0.9.4\n仅申请联网权限，无广告、无账号。",color=muted,fontSize=12.sp,lineHeight=20.sp,modifier=Modifier.padding(top=16.dp))}
        }}
    }
}
@Composable internal fun TeamsPage(vm: AppViewModel,pref: Preferences,back: ()->Unit) {
    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Header("选择主队","一个属于你的主场","返回",back);Meta(vm.teams)}
        items(vm.teams.data.orEmpty(),key={it.id}) {t ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if(pref.team==t.id) accent.copy(alpha=.1f) else surface).clickable {vm.setTeam(t.id);back()}.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                Badge(t,40)
                Column(Modifier.weight(1f).padding(start=14.dp)) {Text(t.name,color=ink,fontSize=16.sp);Text(t.english,color=muted,fontSize=11.sp,modifier=Modifier.padding(top=4.dp))}
                if(pref.team==t.id) Text("✓",color=accent,fontSize=22.sp)
            }
        }
    }
}
@Composable internal fun DetailPage(vm: AppViewModel,gameId: String) {
    // Retain outgoing content until the navigation transition finishes.
    var snapshot by remember(gameId) {mutableStateOf(vm.detail)}
    LaunchedEffect(vm.detail,vm.detailId) {if(vm.detailId==gameId) snapshot=vm.detail}
    val loaded=if(vm.detailId==gameId) vm.detail else snapshot
    val detail=loaded.data
    var statsTab by rememberSaveable(gameId) {mutableIntStateOf(0)}
    var teamTab by rememberSaveable(gameId) {mutableIntStateOf(0)}
    val horizontal=rememberScrollState()
    val state=rememberLazyListState()
    val players=detail?.players?.getOrNull(teamTab.coerceAtMost((detail.players.size-1).coerceAtLeast(0)))
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val nameWidth=((maxWidth-40.dp)*.38f).coerceAtLeast(102.dp)
        val cellWidth=((maxWidth-40.dp-nameWidth)/4).coerceAtLeast(40.dp)
        val columns=listOf("MIN" to "时间","PTS" to "得分","REB" to "篮板","AST" to "助攻","FG" to "投篮","3PT" to "三分","FT" to "罚球","STL" to "抢断","BLK" to "盖帽","TO" to "失误","+/-" to "+/−")
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(),state=state,contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(0.dp)) {
            item {Header("比赛详情","GAME CENTER","返回",vm::closeGame)}
            item {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Column(Modifier.weight(1f)) {Meta(loaded)};Button({vm.openGame(gameId,true)},insideMargin=PaddingValues(10.dp)) {Text("刷新",color=ink,fontSize=12.sp)}};Spacer(Modifier.height(12.dp))}
            if(detail==null) item {Empty(if(loaded.loading) "正在获取比赛" else "比赛详情暂不可用","可刷新重试，未使用演示数据替代。")}
            else {
                item {Spotlight(detail.game,detail.game.home,"${detail.game.away.shortName} · ${detail.game.home.shortName}") {};if(detail.game.venue.isNotBlank()) Text(detail.game.venue,color=muted,fontSize=10.sp,modifier=Modifier.padding(top=8.dp))}
                item {Section("每节比分");val g=detail.game;val count=maxOf(g.homePeriods.size,g.awayPeriods.size)
                    if(count==0) Empty("暂无分节数据","来源尚未提供。")
                    else Card(Modifier.fillMaxWidth(),cornerRadius=20.dp,colors=CardDefaults.defaultColors(color=surface),insideMargin=PaddingValues(12.dp)) {
                        Column(Modifier.horizontalScroll(rememberScrollState())) {
                            Row {Cell("球队",76.dp);repeat(count) {Cell(if(it<4) "Q${it+1}" else "OT${it-3}",44.dp)}}
                            listOf(g.away to g.awayPeriods,g.home to g.homePeriods).forEach {(t,values)->Row {Cell(t.shortName,76.dp);repeat(count) {Cell(values.getOrElse(it) {"—"},44.dp)}}}
                        }
                    };Spacer(Modifier.height(18.dp))
                }
                stickyHeader(key="statistics-controls") {
                    Column(Modifier.fillMaxWidth().background(if(LocalDark.current) Color(0xFF090A0D) else Color(0xFFFCFCFD)).padding(top=8.dp,bottom=10.dp)) {
                        Segments(listOf("球队统计","球员统计"),statsTab) {statsTab=it}
                        if(statsTab==1 && detail.players.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp));Segments(detail.players.map {it.team.shortName},teamTab.coerceIn(detail.players.indices)) {teamTab=it}
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth().background(surface,RoundedCornerShape(topStart=16.dp,topEnd=16.dp))) {
                                Cell("球员",nameWidth)
                                Row(Modifier.weight(1f).horizontalScroll(horizontal)) {columns.forEach {Cell(it.second,cellWidth)}}
                            }
                        }
                    }
                }
                if(detail.game.state=="pre") item {Empty("统计尚未开始","比赛开始且来源提供数据后显示。")}
                else if(statsTab==0) {
                    item {Row(Modifier.fillMaxWidth().padding(vertical=14.dp),horizontalArrangement=Arrangement.SpaceBetween) {Text(detail.game.away.shortName,color=muted,fontSize=12.sp);Text(detail.game.home.shortName,color=muted,fontSize=12.sp)}}
                    item {Card(Modifier.fillMaxWidth(),cornerRadius=22.dp,colors=CardDefaults.defaultColors(color=surface),insideMargin=PaddingValues(16.dp)) {
                        detail.stats.forEachIndexed {i,s->
                            Row(Modifier.fillMaxWidth().padding(vertical=13.dp),verticalAlignment=Alignment.CenterVertically) {
                                Text(s.away,color=ink,fontSize=16.sp,modifier=Modifier.weight(1f))
                                Text(s.label,color=muted,fontSize=12.sp)
                                Text(s.home,color=ink,fontSize=16.sp,modifier=Modifier.weight(1f),textAlign=TextAlign.End)
                            };if(i<detail.stats.lastIndex) Rule()
                        }
                    }}
                } else if(players==null || players.players.isEmpty()) item {Empty("暂无球员统计","来源尚未提供本场球员数据。")}
                else {
                    items(players.players,key={it.name}) {p ->
                        Row(Modifier.fillMaxWidth().background(sheet).padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.width(nameWidth).padding(horizontal=8.dp,vertical=10.dp)) {
                                Text(p.name,color=ink,fontSize=12.sp,lineHeight=17.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                                if(p.dnp) Text("未上场",color=muted,fontSize=10.sp,modifier=Modifier.padding(top=3.dp))
                            }
                            Row(Modifier.weight(1f).horizontalScroll(horizontal)) {columns.forEach {Cell(if(p.dnp) "—" else p.cells[it.first] ?: "—",cellWidth,it.first=="PTS")}}
                        }
                        Rule()
                    }
                    item {Text("左右滑动数值查看更多 · 姓名与表头固定",color=muted,fontSize=10.sp,modifier=Modifier.padding(top=12.dp))}
                }
            }
        }
    }
}
@Composable private fun Cell(value: String,width: Dp,strong: Boolean=false) {
    Text(value,color=ink,fontSize=12.sp,lineHeight=17.sp,fontWeight=if(strong) FontWeight.SemiBold else FontWeight.Normal,
        textAlign=TextAlign.Center,modifier=Modifier.width(width).padding(horizontal=3.dp,vertical=13.dp),maxLines=2,overflow=TextOverflow.Ellipsis)
}
