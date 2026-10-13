@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package app.courtside.nba

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.Text
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable internal fun Header(title: String, subtitle: String, action: String="", onAction: (() -> Unit)?=null) {
    Row(Modifier.fillMaxWidth().padding(top=10.dp, bottom=6.dp), verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color=ink, fontSize=28.sp, fontWeight=FontWeight.Bold, letterSpacing=(-1).sp)
            if(subtitle.isNotBlank()) Text(subtitle, color=muted, fontSize=12.sp, modifier=Modifier.padding(top=3.dp))
        }
        if(onAction != null) Button(onAction, minWidth=44.dp, insideMargin=PaddingValues(12.dp)) { Text(action, color=ink, fontSize=12.sp) }
    }
}
@Composable private fun Section(title: String, trailing: String="", click: (() -> Unit)?=null) {
    Row(Modifier.fillMaxWidth().padding(top=4.dp, bottom=4.dp), verticalAlignment=Alignment.CenterVertically) {
        Text(title, color=ink, fontSize=19.sp, fontWeight=FontWeight.SemiBold, modifier=Modifier.weight(1f))
        if(trailing.isNotBlank()) Text(trailing, color=muted, fontSize=12.sp, modifier=if(click!=null) Modifier.clickable(onClick=click).padding(8.dp) else Modifier)
    }
}
@Composable internal fun Meta(value: Loaded<*>) {
    val stamp = if(value.updated>0) DateTimeFormatter.ofPattern("MM/dd HH:mm").format(Instant.ofEpochMilli(value.updated).atZone(ZoneId.systemDefault())) else ""
    val message = when {
        value.loading -> "正在更新" + if(stamp.isNotBlank()) " · 上次 $stamp" else "…"
        value.error != null -> value.error + if(stamp.isNotBlank()) "\n缓存获取于 $stamp" else ""
        stamp.isNotBlank() -> "ESPN · 最近获取 $stamp"
        else -> ""
    }
    if(message.isNotBlank()) Text(message, color=if(value.error!=null) Color(0xFFB88749) else muted, fontSize=11.sp, lineHeight=17.sp, modifier=Modifier.padding(vertical=4.dp))
}
@Composable internal fun Empty(title: String, note: String) {
    Card(Modifier.fillMaxWidth(), cornerRadius=22.dp, colors=CardDefaults.defaultColors(color=surface), insideMargin=PaddingValues(14.dp)) {
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
@Composable internal fun Rule(modifier: Modifier=Modifier) { Box(modifier.fillMaxWidth().height(1.dp).background(line)) }
@Composable internal fun Segments(tabs: List<String>, selected: Int, onSelect: (Int)->Unit) {
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    TabRowWithContour(tabs, selected.coerceIn(tabs.indices), onSelect, Modifier.fillMaxWidth(),
        colors=TabRowDefaults.tabRowColors(backgroundColor=surface.copy(alpha=.50f), contentColor=muted, selectedBackgroundColor=sheet, selectedContentColor=accent),
        minWidth=60.dp, maxWidth=500.dp, height=(38 * scale).dp, cornerRadius=14.dp)
}
@Composable internal fun HomePage(vm:AppViewModel,pref:Preferences,all:()->Unit) {
    val team=vm.teams.data.orEmpty().find {it.id==pref.team}
    val record=vm.table.data?.rows?.find {it.team.id==pref.team}
    val cards=homeGames(vm.today.data.orEmpty(),vm.schedule.data.orEmpty(),pref.team,Instant.now())
    val primary=cards.firstOrNull()
    val following=cards.drop(1).take(2)
    val today=vm.today.data.orEmpty().sortedByDescending {it.involves(pref.team)}.take(4)
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=16.dp,end=16.dp,bottom=LocalBottomPadding.current),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item {Header("我的主场","${vm.season-1}–${vm.season.toString().takeLast(2)}","刷新"){vm.refreshHome(true)}}
        if(pref.teams.size>1) item {
            LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                items(pref.teams,key={it}) {id -> vm.teams.data.orEmpty().find {it.id==id}?.let {t ->
                    Row(Modifier.background(if(id==pref.team) accent.copy(alpha=.10f) else surface.copy(alpha=.6f),RoundedCornerShape(20.dp)).clickable {vm.setTeam(id)}.heightIn(min=40.dp).padding(horizontal=10.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                        Badge(t,20);Text(t.shortName,color=if(id==pref.team) accent else ink,fontSize=12.sp,fontWeight=if(id==pref.team) FontWeight.SemiBold else FontWeight.Normal,modifier=Modifier.padding(start=6.dp))
                    }
                }}
            }
        }
        item {
            if(team==null) Empty("选择你的主队","在底部设置中添加球队。")
            else Card(Modifier.fillMaxWidth(),insideMargin=PaddingValues(12.dp)) {
                AnimatedContent(team,transitionSpec={fadeIn(tween(150)) togetherWith fadeOut(tween(100))},label="main-team") {shown ->
                    val r=vm.table.data?.rows?.find {it.team.id==shown.id}
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Badge(shown,30)
                        Column(Modifier.weight(1f).padding(horizontal=10.dp)) {
                            Text(shown.shortName,color=ink,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
                            Text(shown.english,color=muted,fontSize=10.sp,lineHeight=13.sp)
                        }
                        Text(r?.let {"${it.wins}–${it.losses}"} ?: "—",color=ink,fontSize=18.sp,fontWeight=FontWeight.SemiBold,style=numericStyle)
                        Text(if(r!=null && r.rank>0) " · ${if(r.east) "东" else "西"}${r.rank}" else " · 常规赛",color=muted,fontSize=10.sp)
                    }
                }
            }
        }
        if(team!=null) {
            item {Section("主队赛场",if(record!=null && record.wins+record.losses==0) "常规赛未开始" else "")}
            item {
                if(primary==null) Empty(if(vm.schedule.loading) "正在查找比赛" else "暂无比赛安排",if(vm.schedule.error!=null) "赛程未完整更新，请稍后刷新。" else "来源尚未提供下一场安排。")
                else Spotlight(primary,team,if(primary.live) "正在进行" else if(primary.finished) "最近一场" else "下一场比赛"){vm.openGame(primary.id)}
                if(primary!=null && vm.today.data.orEmpty().any {it.id==primary.id}) Meta(vm.today) else Meta(vm.schedule)
                if(vm.schedule.error!=null && primary!=null && vm.today.data.orEmpty().any {it.id==primary.id}) Meta(vm.schedule)
            }
            if(following.isNotEmpty()) item {GameGroup(following){vm.openGame(it.id)}}
        }
        item {Section("今日赛事","查看全部 ›",all)}
        item {
            if(today.isEmpty()) Empty(if(vm.today.loading) "正在获取赛程" else "今日暂无赛事","可在赛程页查看其他日期。")
            else GameGroup(today){vm.openGame(it.id)}
            Meta(vm.today)
            Text("${vm.currentDate.format(DateTimeFormatter.ofPattern("M月d日"))} · ${ZoneId.systemDefault().id}",color=muted,fontSize=10.sp)
        }
    }
}
@Composable private fun Spotlight(game:Game,team:Team,label:String,open:()->Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick=open),insideMargin=PaddingValues(12.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(label,color=if(game.live) accent else ink,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
            Text(game.kind,color=muted,fontSize=10.sp)
        }
        Spacer(Modifier.height(7.dp))
        listOf(game.away to game.awayScore,game.home to game.homeScore).forEach {(t,score) ->
            Row(Modifier.fillMaxWidth().padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically) {
                Badge(t,26)
                Text(t.shortName,color=ink,fontSize=16.sp,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f).padding(start=8.dp))
                Text(if(game.live||game.finished) score else "—",color=scoreInk(game,t),fontSize=23.sp,fontWeight=if(leadingTeam(game)==t.id) FontWeight.Bold else FontWeight.Medium,style=numericStyle)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(game.instant?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("M月d日 HH:mm")) ?: "时间待定",color=muted,fontSize=10.sp)
            Text(if(game.live||game.finished) leadLabel(game) else game.status,color=ink,fontSize=10.sp)
        }
        if(game.live) Text(game.status,color=muted,fontSize=10.sp,lineHeight=14.sp,modifier=Modifier.padding(top=2.dp))
    }
}
@Composable private fun GameGroup(games:List<Game>,open:(Game)->Unit) {
    Card(Modifier.fillMaxWidth(),cornerRadius=18.dp,colors=CardDefaults.defaultColors(color=sheet)) {
        games.forEachIndexed {i,g -> CompactGame(g){open(g)};if(i<games.lastIndex) Rule(Modifier.padding(horizontal=12.dp))}
    }
}
@Composable private fun CompactGame(g:Game,open:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=open).padding(horizontal=10.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.width(48.dp)) {
            Text(if(g.live) (if(g.period>4) "加时" else "第${g.period}节") else g.instant?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "待定",color=if(g.live) accent else ink,fontSize=12.sp,fontWeight=FontWeight.Medium,style=numericStyle)
            Text(if(g.live) g.clock else g.kind,color=muted,fontSize=10.sp,lineHeight=13.sp,modifier=Modifier.padding(top=3.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal=7.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            listOf(g.away to g.awayScore,g.home to g.homeScore).forEach {(t,value) ->
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Badge(t,22)
                    Text(t.shortName,color=ink,fontSize=13.sp,modifier=Modifier.weight(1f).padding(start=6.dp,end=4.dp),maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(if(g.live||g.finished) value else "—",color=scoreInk(g,t),fontSize=16.sp,fontWeight=if(leadingTeam(g)==t.id) FontWeight.Bold else FontWeight.Normal,style=numericStyle,textAlign=TextAlign.End,modifier=Modifier.widthIn(min=30.dp))
                }
            }
        }
        Text(if(g.live) "进行中" else g.status,color=if(g.live) accent else muted,fontSize=10.sp,textAlign=TextAlign.End,modifier=Modifier.width(42.dp))
    }
}
@Composable internal fun SchedulePage(vm: AppViewModel, pref: Preferences) {
    val context=LocalContext.current
    val filtered=vm.games.data.orEmpty().filter { when(vm.scheduleFilter) {1 -> it.involves(pref.team);2 -> pref.teams.any(it::involves);else -> true} }
    LazyColumn(Modifier.fillMaxSize(), contentPadding=PaddingValues(start=16.dp,end=16.dp,bottom=LocalBottomPadding.current), verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item { Header("赛程", "每一个比赛日", "刷新") { vm.loadDate(vm.date,true) } }
        item {
            Card(Modifier.fillMaxWidth(),insideMargin=PaddingValues(8.dp)) {Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween) {
                Button({vm.loadDate(vm.date.minusDays(1))}, minWidth=42.dp, insideMargin=PaddingValues(10.dp)) { Text("‹",color=ink,fontSize=24.sp) }
                Column(Modifier.weight(1f).clickable { DatePickerDialog(context,{_,y,m,d->vm.loadDate(LocalDate.of(y,m+1,d))},vm.date.year,vm.date.monthValue-1,vm.date.dayOfMonth).show() }.padding(8.dp), horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(vm.date.format(DateTimeFormatter.ofPattern("M月d日"))+"  ▾",color=ink,fontSize=18.sp,fontWeight=FontWeight.SemiBold)
                    Text("${vm.date.year} · ${weekday(vm.date)}",color=muted,fontSize=11.sp)
                }
                Button({vm.loadDate(vm.date.plusDays(1))},minWidth=42.dp,insideMargin=PaddingValues(10.dp)) { Text("›",color=ink,fontSize=24.sp) }
                Button({vm.loadDate(LocalDate.now())},minWidth=38.dp,insideMargin=PaddingValues(8.dp)){Text("今天",color=ink,fontSize=11.sp)}
            } }
        }
        item {Segments(listOf("全部比赛","当前主队","全部关注"),vm.scheduleFilter) {vm.scheduleFilter=it}}
        item { Meta(vm.games); Text("主队：${shortTeamName(pref.team).ifBlank {"尚未关注"}} · ${ZoneId.systemDefault().id}",color=muted,fontSize=10.sp) }
        item { Text((if(vm.date==vm.currentDate) "今天" else vm.date.format(DateTimeFormatter.ofPattern("M月d日")))+" · ${weekday(vm.date)}",color=muted,fontSize=12.sp,fontWeight=FontWeight.Medium) }
        item {
            if(filtered.isEmpty()) Empty(if(vm.games.loading) "正在获取赛程" else if(vm.games.error!=null) "赛程暂不可用" else "这一天没有${if(vm.scheduleFilter>0) "关注球队的" else ""}比赛","点击日期或左右按钮查看其他比赛日。")
            else GameGroup(filtered) { vm.openGame(it.id) }
        }
    }
}
private fun weekday(day: LocalDate)=listOf("周一","周二","周三","周四","周五","周六","周日")[day.dayOfWeek.value-1]
@Composable internal fun StandingsPage(vm: AppViewModel,pref: Preferences) {
    var conference by rememberSaveable { mutableIntStateOf(0) }
    val rows=vm.table.data?.rows.orEmpty().filter { it.east==(conference==0) }.sortedBy { if(it.rank>0) it.rank else Int.MAX_VALUE }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=16.dp,end=16.dp,bottom=LocalBottomPadding.current),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item { Header("联盟排名","${vm.season-1}–${vm.season.toString().takeLast(2)} · 常规赛","刷新") {vm.loadTable(true)} }
        item { Segments(listOf("东部","西部"),conference) {conference=it} }
        item { Meta(vm.table) }
        if(rows.isNotEmpty() && rows.all {it.wins+it.losses==0}) item { Empty("常规赛尚未开始","以下为球队列表，暂无正式排名。季前赛战绩不计入排名。") }
        item {
            if(rows.isEmpty()) Empty(if(vm.table.loading) "正在获取排名" else "排名暂不可用","请稍后刷新。")
            else Card(Modifier.fillMaxWidth(),cornerRadius=18.dp,colors=CardDefaults.defaultColors(color=sheet)) {
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp)) {
                    Text("球队",color=muted,fontSize=11.sp,modifier=Modifier.weight(1f))
                    Text("胜–负",color=muted,fontSize=11.sp,modifier=Modifier.width(52.dp),textAlign=TextAlign.End)
                    Text("胜率",color=muted,fontSize=11.sp,modifier=Modifier.width(48.dp),textAlign=TextAlign.End)
                }
                Rule()
                rows.forEachIndexed { i,r ->
                    Row(Modifier.fillMaxWidth().background(if(r.team.id in pref.teams) accent.copy(alpha=.08f) else Color.Transparent).padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(if(r.rank>0) r.rank.toString() else "—",color=muted,fontSize=12.sp,modifier=Modifier.width(24.dp))
                        Badge(r.team,28)
                        Text(r.team.shortName+if(r.team.id in pref.teams) " ★" else "",color=ink,fontSize=14.sp,modifier=Modifier.weight(1f).padding(start=8.dp),maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text("${r.wins}–${r.losses}",color=ink,fontSize=12.sp,style=numericStyle,modifier=Modifier.width(52.dp),textAlign=TextAlign.End)
                        Text(if(r.wins+r.losses==0) "—" else r.pct,color=muted,fontSize=12.sp,style=numericStyle,modifier=Modifier.width(48.dp),textAlign=TextAlign.End)
                    }
                    if(i<rows.lastIndex) Rule(Modifier.padding(start=44.dp,end=12.dp))
                }
            }
        }
    }
}
@Composable internal fun SettingsPage(vm: AppViewModel,pref: Preferences,teams: ()->Unit) {
    var about by rememberSaveable {mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=16.dp,end=16.dp,bottom=LocalBottomPadding.current),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {Header("设置","你的球队，你的主场")}
        item {Card(Modifier.fillMaxWidth().clickable(onClick=teams),cornerRadius=22.dp,colors=CardDefaults.defaultColors(color=surface),insideMargin=PaddingValues(14.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                vm.teams.data.orEmpty().find {it.id==pref.team}?.let {Badge(it,40);Spacer(Modifier.width(14.dp))}
                Column(Modifier.weight(1f)) {Text("我的主队",color=ink,fontSize=17.sp,fontWeight=FontWeight.SemiBold);Text(if(pref.teams.isEmpty()) "添加你关注的球队" else "已关注 ${pref.teams.size} 支 · ${pref.teams.joinToString("、") {shortTeamName(it)}}",color=muted,fontSize=12.sp,lineHeight=18.sp,modifier=Modifier.padding(top=6.dp))}
                Text("›",color=muted,fontSize=24.sp)
            }
        }}
        item {Card(Modifier.fillMaxWidth(),insideMargin=PaddingValues(14.dp)) {Text("外观",color=ink,fontSize=17.sp,fontWeight=FontWeight.SemiBold);Spacer(Modifier.height(14.dp));Segments(listOf("深色","浅色","系统"),listOf("dark","light","system").indexOf(pref.theme).coerceAtLeast(0)) {vm.setTheme(listOf("dark","light","system")[it])};Text("液态玻璃 · 按系统能力启用模糊与折射",color=muted,fontSize=11.sp,modifier=Modifier.padding(top=12.dp))}}
        item {Card(Modifier.fillMaxWidth(),cornerRadius=22.dp,colors=CardDefaults.defaultColors(color=surface),insideMargin=PaddingValues(14.dp)) {
            Row(Modifier.fillMaxWidth().clickable {about=!about},verticalAlignment=Alignment.CenterVertically) {
                Text("关于与数据来源",color=ink,fontSize=17.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f));Text(if(about) "⌃" else "⌄",color=muted,fontSize=20.sp)
            }
            Text("Courtside 0.4.0 · ESPN",color=muted,fontSize=12.sp,modifier=Modifier.padding(top=8.dp))
            AnimatedVisibility(about) {Text("真实赛事数据来自 ESPN 公开接口。非 ESPN / NBA 官方应用。\n\n最近获取时间表示接口成功响应的时间，不保证与赛场同步。失败时保留真实缓存，前台按需刷新，后台停止持续轮询。\n\n30 队图标和球员中文名内置于应用。未收录的球员保留英文名。\n\nKotlin · Jetpack Compose · miuix 0.9.4\n仅申请联网权限，无广告、无账号。",color=muted,fontSize=12.sp,lineHeight=20.sp,modifier=Modifier.padding(top=16.dp))}
        }}
    }
}
@Composable internal fun TeamsPage(vm: AppViewModel,pref: Preferences,back: ()->Unit) {
    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Header("我的主队","已关注 ${pref.teams.size} 支 · 点击球队添加或取消","完成",back);Meta(vm.teams)}
        items(vm.teams.data.orEmpty(),key={it.id}) {t ->
            Row(Modifier.fillMaxWidth().glass(18.dp,tint=if(t.id in pref.teams) accent.copy(alpha=.12f) else surface).clickable {vm.toggleTeam(t.id)}.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Badge(t,40)
                Column(Modifier.weight(1f).padding(start=14.dp)) {Text(t.name,color=ink,fontSize=16.sp);Text(t.english,color=muted,fontSize=11.sp,modifier=Modifier.padding(top=4.dp))}
                Text(if(t.id in pref.teams) "✓" else "+",color=if(t.id in pref.teams) accent else muted,fontSize=22.sp)
            }
        }
    }
}
