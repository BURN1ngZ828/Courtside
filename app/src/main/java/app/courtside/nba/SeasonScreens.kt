@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package app.courtside.nba

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun seasonLabel(year: Int) = "${year-1}–${year.toString().takeLast(2)}"
private data class SortedStats(val rows: List<SeasonPlayer> = emptyList(), val maxima: Map<String, Double> = emptyMap())

@Composable internal fun DataPage(vm: AppViewModel, pref: Preferences) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal=16.dp)) { Header("数据", "联盟与球员", "刷新") { if(section == 0) vm.loadTable(true) else vm.loadLeague(force=true) } }
        Box(Modifier.padding(horizontal=16.dp,vertical=4.dp)) { Segments(listOf("球队排名", "球员统计"), section) { section=it } }
        Box(Modifier.weight(1f)) { if(section == 0) StandingsPage(vm,pref,showHeader=false) else LeaguePage(vm,pref) }
    }
}

@Composable private fun LeaguePage(vm: AppViewModel, pref: Preferences) {
    LaunchedEffect(Unit) { if(vm.league.data == null && !vm.league.loading) vm.loadLeague() }
    var query by rememberSaveable { mutableStateOf("") }
    var teamId by rememberSaveable { mutableStateOf("") }
    var minGames by rememberSaveable { mutableIntStateOf(0) }
    var minMinutes by rememberSaveable { mutableIntStateOf(0) }
    var chooser by rememberSaveable { mutableStateOf("") }
    val stats=pref.stats
    val horizontal=rememberScrollState()
    val source=vm.league.data?.rows.orEmpty()
    val sorted by produceState(SortedStats(), source, query, teamId, minGames, minMinutes, stats) {
        value=withContext(Dispatchers.Default) {
            val rows=source.filter { p -> (teamId.isBlank() || p.teams.any { it.id == teamId }) &&
                (query.isBlank() || p.name.contains(query.trim(),true) || p.english.contains(query.trim(),true)) &&
                (p.values["gamesPlayed"] ?: 0.0) >= minGames && (p.values["avgMinutes"] ?: 0.0) >= minMinutes }
            val comparator=Comparator<SeasonPlayer> { a,b ->
                val av=a.value(stats.sort,stats.totals); val bv=b.value(stats.sort,stats.totals)
                when { av == null && bv == null -> a.id.compareTo(b.id); av == null -> 1; bv == null -> -1
                    else -> (if(stats.descending) bv.compareTo(av) else av.compareTo(bv)).takeIf { it != 0 } ?: a.id.compareTo(b.id) }
            }
            SortedStats(rows.sortedWith(comparator), stats.columns.mapNotNull { key -> rows.mapNotNull { it.value(key,stats.totals) }.maxOrNull()?.takeIf { it != 0.0 }?.let { key to it } }.toMap())
        }
    }
    val bg=surface
    val columns=stats.columns.mapNotNull { key -> seasonColumns.find { it.key == key } }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=12.dp,end=12.dp,bottom=LocalBottomPadding.current)) {
        item(key="filters") {
            Card(Modifier.fillMaxWidth(),insideMargin=PaddingValues(10.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(seasonLabel(vm.dataSeason)+" ▾",color=ink,fontSize=13.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.clickable {chooser="season"}.heightIn(min=40.dp).wrapContentHeight())
                    Text((if(vm.dataType==3) "季后赛" else "常规赛")+" ▾",color=ink,fontSize=12.sp,modifier=Modifier.clickable {vm.loadLeague(type=if(vm.dataType==2) 3 else 2)}.heightIn(min=40.dp).wrapContentHeight())
                    Text((if(teamId.isBlank()) "全部球队" else shortTeamName(teamId))+" ▾",color=accent,fontSize=12.sp,modifier=Modifier.clickable {chooser="team"}.heightIn(min=40.dp).wrapContentHeight())
                }
                Segments(listOf("赛季场均", "赛季总计"), if(stats.totals) 1 else 0) {vm.setStats(stats.copy(totals=it==1))}
                Box(Modifier.fillMaxWidth().padding(top=8.dp).background(bg,RoundedCornerShape(12.dp)).padding(10.dp)) {
                    if(query.isBlank()) Text("搜索球员中文或英文名",color=muted,fontSize=12.sp)
                    BasicTextField(query,{query=it},Modifier.fillMaxWidth().semantics {contentDescription="搜索球员中文或英文名"},textStyle=TextStyle(color=ink,fontSize=13.sp),singleLine=true,cursorBrush=SolidColor(accent))
                }
                Row(Modifier.fillMaxWidth().padding(top=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("场次 ≥$minGames",color=muted,fontSize=11.sp,modifier=Modifier.clickable {minGames=when(minGames){0->10;10->20;else->0}}.heightIn(min=40.dp).wrapContentHeight())
                    Text("场均时间 ≥$minMinutes",color=muted,fontSize=11.sp,modifier=Modifier.clickable {minMinutes=when(minMinutes){0->10;10->20;else->0}}.heightIn(min=40.dp).wrapContentHeight())
                    Text("显示列 ▾",color=accent,fontSize=11.sp,modifier=Modifier.clickable {chooser="columns"}.heightIn(min=40.dp).wrapContentHeight())
                }
            }
            Meta(vm.league)
            Text("${sorted.rows.size} / ${source.size} 名 · 蓝色为筛选范围最高值 · 点击表头排序",color=muted,fontSize=10.sp,lineHeight=16.sp,modifier=Modifier.padding(vertical=4.dp))
            if(minGames>0 || minMinutes>0) Text("自定义出场筛选，不代表联盟官方榜单资格。",color=muted,fontSize=10.sp,lineHeight=15.sp)
        }
        stickyHeader(key="columns") {
            Row(Modifier.fillMaxWidth().background(bg).heightIn(min=36.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("球员 / 球队",color=muted,fontSize=10.sp,modifier=Modifier.width(108.dp).padding(start=6.dp))
                Row(Modifier.weight(1f).horizontalScroll(horizontal)) {columns.forEach { c ->
                    val selected=stats.sort==c.key
                    Text(c.label+if(selected) (if(stats.descending) "↓" else "↑") else "",color=if(selected) accent else muted,fontSize=10.sp,maxLines=1,textAlign=TextAlign.Center,
                        modifier=Modifier.width(if(c.percent) 50.dp else 38.dp).heightIn(min=36.dp).background(if(selected) accent.copy(alpha=.07f) else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable {vm.setStats(stats.copy(sort=c.key,descending=if(selected) !stats.descending else true))}.wrapContentHeight())
                }}
            }
            Rule()
        }
        if(sorted.rows.isEmpty()) item(key="empty") {
            Empty(if(vm.league.loading) "正在获取完整球员数据" else if(vm.league.error!=null) "球员数据暂不可用" else "暂无符合条件的球员", "${seasonLabel(vm.dataSeason)} ${if(vm.dataType==3) "季后赛" else "常规赛"} · 未开始的赛季没有统计数据。")
            if(source.isEmpty() && !vm.league.loading && vm.dataSeason==vm.season) Button({vm.loadLeague(year=vm.season-1)},modifier=Modifier.padding(top=8.dp)) {Text("查看上一赛季",color=ink)}
        }
        items(sorted.rows,key={it.id},contentType={"season-player"}) {p ->
            Row(Modifier.fillMaxWidth().heightIn(min=45.dp).background(bg.copy(alpha=.72f)),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.width(108.dp).clickable {vm.openPlayer(p)}.padding(horizontal=6.dp,vertical=5.dp)) {
                    Text(p.name,color=ink,fontSize=12.sp,lineHeight=15.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(p.english,color=muted,fontSize=9.sp,lineHeight=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        p.teams.lastOrNull()?.let {Badge(it,12)}
                        Text(if(p.teams.size>1) "多队 · 合并赛季" else p.teams.firstOrNull()?.shortName ?: "球队待更新",color=muted,fontSize=9.sp,lineHeight=12.sp,maxLines=1,modifier=Modifier.padding(start=3.dp))
                    }
                }
                Row(Modifier.weight(1f).horizontalScroll(horizontal)) {columns.forEach {c ->
                    val v=p.value(c.key,stats.totals); val high=v!=null && sorted.maxima[c.key]==v
                    Text(seasonNumber(v,c.key,stats.totals),color=if(high) accent else ink,fontSize=11.sp,style=numericStyle,textAlign=TextAlign.Center,maxLines=1,
                        modifier=Modifier.width(if(c.percent) 50.dp else 38.dp).heightIn(min=45.dp).background(if(stats.sort==c.key) accent.copy(alpha=.035f) else androidx.compose.ui.graphics.Color.Transparent).wrapContentHeight())
                }}
            }
            Rule()
        }
    }
    if(chooser.isNotBlank()) Dialog(onDismissRequest={chooser=""}) {
        Card(Modifier.fillMaxWidth().heightIn(max=580.dp),insideMargin=PaddingValues(12.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text(when(chooser){"season"->"选择赛季";"team"->"选择球队";else->"统计列与排列"},color=ink,fontSize=17.sp,modifier=Modifier.weight(1f));Text("完成",color=accent,fontSize=12.sp,modifier=Modifier.clickable {chooser=""}.padding(12.dp))}
            LazyColumn {
                when(chooser) {
                    "season" -> items((vm.season downTo vm.season-5).toList()) {year -> Text(seasonLabel(year),color=if(year==vm.dataSeason) accent else ink,fontSize=15.sp,modifier=Modifier.fillMaxWidth().clickable {vm.loadLeague(year=year);chooser=""}.padding(14.dp))}
                    "team" -> {
                        item {Text("全部球队",color=ink,fontSize=15.sp,modifier=Modifier.fillMaxWidth().clickable {teamId="";chooser=""}.padding(14.dp))}
                        items(vm.teams.data.orEmpty(),key={it.id}) {team -> Row(Modifier.fillMaxWidth().clickable {teamId=team.id;chooser=""}.padding(10.dp),verticalAlignment=Alignment.CenterVertically) {Badge(team,24);Text(team.name,color=if(team.id==teamId) accent else ink,fontSize=13.sp,modifier=Modifier.padding(start=8.dp))}}
                    }
                    else -> {
                        item {Text("点击添加或移除，箭头调整顺序。",color=muted,fontSize=11.sp,modifier=Modifier.padding(8.dp))}
                        items((stats.columns.mapNotNull {key->seasonColumns.find {it.key==key}}+seasonColumns.filter {it.key !in stats.columns}),key={it.key}) {c ->
                            val index=stats.columns.indexOf(c.key)
                            Row(Modifier.fillMaxWidth().heightIn(min=44.dp),verticalAlignment=Alignment.CenterVertically) {
                                Text((if(index>=0) "✓ " else "+ ")+c.label,color=if(index>=0) accent else ink,fontSize=13.sp,modifier=Modifier.weight(1f).clickable {
                                    if(index<0) vm.setStats(stats.copy(columns=stats.columns+c.key)) else if(stats.columns.size>1) vm.setStats(stats.copy(columns=stats.columns-c.key))
                                }.padding(10.dp))
                                if(index>=0) listOf(-1 to "↑",1 to "↓").forEach {(offset,label)->Text(label,color=muted,fontSize=18.sp,modifier=Modifier.clickable {
                                    val to=index+offset
                                    if(to in stats.columns.indices) {val next=stats.columns.toMutableList();next.add(to,next.removeAt(index));vm.setStats(stats.copy(columns=next))}
                                }.padding(12.dp))}
                            }
                        }
                        item {Button({vm.setStats(StatsPreferences());chooser=""}) {Text("恢复默认",color=ink)}}
                    }
                }
            }
        }
    }
}

@Composable internal fun PlayerPage(vm: AppViewModel) {
    var last by remember {mutableStateOf(vm.selectedPlayer)}
    if(vm.selectedPlayer!=null) SideEffect {last=vm.selectedPlayer}
    val selection=vm.selectedPlayer ?: last ?: return
    val row=selection.row
    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item {Header(row.name,row.english,"返回") {vm.closePlayer()}}
        item {Card(Modifier.fillMaxWidth(),insideMargin=PaddingValues(12.dp)) {
            Meta(vm.playerStats)
            row.teams.forEach {t -> Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {Badge(t,26);Column(Modifier.padding(start=8.dp)) {Text(t.name,color=ink,fontSize=13.sp);Text(t.english,color=muted,fontSize=10.sp)}}}
            Text(seasonLabel(selection.season)+" · "+if(selection.type==3) "季后赛" else "常规赛",color=muted,fontSize=11.sp,modifier=Modifier.padding(top=6.dp))
            if(row.teams.size>1) Text("下列为各队合并的整个赛季数据。",color=muted,fontSize=10.sp)
        }}
        item {Card(Modifier.fillMaxWidth(),insideMargin=PaddingValues(12.dp)) {
            Row {Text("统计",color=muted,fontSize=11.sp,modifier=Modifier.weight(1f));Text("场均",color=muted,fontSize=11.sp,modifier=Modifier.width(64.dp),textAlign=TextAlign.End);Text("总计",color=muted,fontSize=11.sp,modifier=Modifier.width(64.dp),textAlign=TextAlign.End)}
            seasonColumns.filter {it.key in listOf("GP","MIN","PTS","REB","AST","STL","BLK","FG%","3P%","FT%","TO")}.forEach {c ->
                Row(Modifier.padding(vertical=7.dp)) {Text(c.label,color=ink,fontSize=12.sp,modifier=Modifier.weight(1f));Text(seasonNumber(row.value(c.key,false),c.key,false),color=ink,fontSize=12.sp,style=numericStyle,modifier=Modifier.width(64.dp),textAlign=TextAlign.End);Text(seasonNumber(row.value(c.key,true),c.key,true),color=ink,fontSize=12.sp,style=numericStyle,modifier=Modifier.width(64.dp),textAlign=TextAlign.End)}
                Rule()
            }
        }}
        item {Text("最近五场",color=ink,fontSize=17.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=8.dp));Meta(vm.playerGames)}
        if(vm.playerGames.data.orEmpty().isEmpty()) item {Empty(if(vm.playerGames.loading) "正在获取比赛记录" else "暂无比赛记录","仅显示所选赛季和阶段的实际出场记录。")}
        items(vm.playerGames.data.orEmpty(),key={it.id}) {g -> Card(Modifier.fillMaxWidth().clickable {vm.openGame(g.id)},insideMargin=PaddingValues(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {Badge(g.opponent,24);Text((if(g.away) "客场 · " else "主场 · ")+g.opponent.shortName,color=ink,fontSize=13.sp,modifier=Modifier.weight(1f).padding(start=6.dp));Text(g.instant?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("M/d")) ?: "—",color=muted,fontSize=11.sp);Text(" · "+when(g.result){"W"->"胜";"L"->"负";else->g.result},color=ink,fontSize=11.sp)}
            Text("${g.cells["points"] ?: "—"} 分   ${g.cells["totalRebounds"] ?: "—"} 篮板   ${g.cells["assists"] ?: "—"} 助攻   ${g.cells["minutes"] ?: "—"} 分钟",color=muted,fontSize=11.sp,modifier=Modifier.padding(top=7.dp))
        }}
    }
}
