@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package app.courtside.nba

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.max
import kotlinx.coroutines.delay

private val playerColumns = listOf("MIN" to "时间", "PTS" to "得分", "REB" to "篮板", "AST" to "助攻", "FG" to "投篮", "3PT" to "三分", "FT" to "罚球", "STL" to "抢断", "BLK" to "盖帽", "TO" to "失误", "OREB" to "前板", "DREB" to "后板", "PF" to "犯规", "+/-" to "+/−")
private val arenas = mapOf("Madison Square Garden" to "麦迪逊广场花园", "TD Garden" to "TD花园球馆", "Chase Center" to "大通中心", "United Center" to "联合中心", "Barclays Center" to "巴克莱中心", "American Airlines Center" to "美国航空中心", "Toyota Center" to "丰田中心", "Ball Arena" to "波尔球馆", "Golden 1 Center" to "金色一号中心", "Kaseya Center" to "卡西亚中心")

@Composable internal fun DetailPage(vm: AppViewModel, gameId: String) {
    var snapshot by remember(gameId) { mutableStateOf(vm.detail) }
    LaunchedEffect(vm.detail, vm.detailId) { if(vm.detailId == gameId) snapshot=vm.detail }
    val loaded = if(vm.detailId == gameId) vm.detail else snapshot
    val detail = loaded.data
    var tab by rememberSaveable(gameId) { mutableIntStateOf(0) }
    var selectedTeam by rememberSaveable(gameId) { mutableIntStateOf(0) }
    val horizontal = rememberScrollState()
    val listState = rememberLazyListState()
    val tables = detail?.let { d -> listOf(d.game.away,d.game.home).mapNotNull { t -> d.players.find { it.team.id==t.id } } }.orEmpty()
    val players = tables.getOrNull(selectedTeam.coerceAtMost((tables.size-1).coerceAtLeast(0)))
    val maxima = remember(players) { playerColumns.associate { it.first to statMaxima(players?.players.orEmpty(),it.first) } }
    var expired by remember(loaded.updated) {mutableStateOf(false)}
    LaunchedEffect(loaded.updated) {delay((90000-(System.currentTimeMillis()-loaded.updated)).coerceAtLeast(0));expired=true}
    val fresh = !expired && loaded.error == null && loaded.updated>0 && System.currentTimeMillis()-loaded.updated < 90000
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val nameWidth = ((maxWidth-40.dp)*.39f).coerceAtLeast(110.dp)
        val cellWidth = (((maxWidth-40.dp-nameWidth)/4).coerceAtLeast(42.dp) * fontScale.coerceAtMost(1.5f))
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), state=listState,
            contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=26.dp)) {
            item { Header("比赛详情", "GAME CENTER", "返回", vm::closeGame) }
            item { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Meta(loaded) }
                Button({vm.openGame(gameId,true)},insideMargin=PaddingValues(10.dp)) {Text("刷新",color=ink,fontSize=12.sp)}
            }; Spacer(Modifier.height(12.dp)) }
            if(detail == null) item { Empty(if(loaded.loading) "正在获取比赛" else "比赛详情暂不可用", "可刷新重试，未使用演示数据替代。") }
            else {
                val game = detail.game
                item { MatchSummary(game); Spacer(Modifier.height(16.dp)) }
                item { PeriodCard(game); Spacer(Modifier.height(16.dp)) }
                item { ScoreCurve(detail); Spacer(Modifier.height(16.dp)) }
                stickyHeader(key="statistics-controls") {
                    val headerBase=if(LocalDark.current) Color(0xFF111B2A) else Color(0xFFF8FAFD)
                    Column(Modifier.fillMaxWidth().background(headerBase,RoundedCornerShape(22.dp)).glass(22.dp,tint=headerBase).padding(8.dp)) {
                        Segments(listOf("球队统计", "球员统计"),tab) {tab=it}
                        if(tab==1 && tables.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                tables.forEachIndexed { index, table ->
                                    val color=teamInk(game,table.team)
                                    Row(Modifier.weight(1f).background(color.copy(alpha=if(selectedTeam==index) .14f else .025f),RoundedCornerShape(14.dp)).clickable {selectedTeam=index}.padding(9.dp),verticalAlignment=Alignment.CenterVertically) {
                                        Badge(table.team,25)
                                        Column(Modifier.weight(1f).padding(start=7.dp)) {
                                            Text(table.team.shortName+if(selectedTeam==index) " ✓" else "",color=color,fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                                            Text(table.team.english,color=muted,fontSize=9.sp,lineHeight=13.sp,maxLines=2)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth()) {
                                StatsCell("球员", nameWidth-8.dp,muted)
                                Row(Modifier.weight(1f).horizontalScroll(horizontal)) {playerColumns.forEach {StatsCell(it.second,cellWidth,muted)}}
                            }
                        }
                    }
                }
                if(game.state=="pre") item {Spacer(Modifier.height(12.dp));Empty("统计尚未开始", "比赛开始且来源提供数据后显示。")}
                else if(tab==0) item {Spacer(Modifier.height(12.dp));TeamComparison(detail)}
                else if(players==null || players.players.isEmpty()) item {Spacer(Modifier.height(12.dp));Empty("暂无球员统计", "来源尚未提供本场球员数据。")}
                else {
                    itemsIndexed(players.players,key={index,p -> p.id.ifBlank {"$index-${p.english}"}}) {index,p ->
                        val color=teamInk(game,players.team)
                        val active=p.onCourt && game.live && fresh
                        val shape=RoundedCornerShape(topStart=if(index==0) 22.dp else 0.dp,topEnd=if(index==0) 22.dp else 0.dp,bottomStart=if(index==players.players.lastIndex) 22.dp else 0.dp,bottomEnd=if(index==players.players.lastIndex) 22.dp else 0.dp)
                        Row(Modifier.fillMaxWidth().background(if(index%2==0) sheet else surface.copy(alpha=.55f),shape).padding(vertical=2.dp),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.width(nameWidth).padding(horizontal=9.dp,vertical=11.dp)) {
                                Text(p.name,color=if(active) (if(LocalDark.current) Color(0xFFFF9696) else Color(0xFFB62836)) else ink,fontSize=12.sp,lineHeight=17.sp,maxLines=2)
                                if(p.english.isNotBlank() && p.english!=p.name) Text(p.english,color=muted,fontSize=9.sp,lineHeight=13.sp,maxLines=2,modifier=Modifier.padding(top=3.dp))
                                if(active || p.dnp) Text(if(active) "● 在场" else "未上场",color=if(active) Color(0xFFCC5562) else muted,fontSize=9.sp,modifier=Modifier.padding(top=3.dp))
                            }
                            Row(Modifier.weight(1f).horizontalScroll(horizontal)) {
                                playerColumns.forEach { (key,_) ->
                                    PlayerCell(p,key,cellWidth,color,statHighlights(p,key,maxima[key].orEmpty()))
                                }
                            }
                        }
                        Rule()
                    }
                    item {Card(Modifier.fillMaxWidth(),cornerRadius=18.dp,insideMargin=PaddingValues(12.dp)) {
                        Text("浅色底＋加粗：本队该项最高值（并列均标记）\n红色姓名＋在场：最近更新时的在场球员\n左右滑动查看更多统计 · 姓名与表头固定",color=muted,fontSize=10.sp,lineHeight=17.sp)
                    }}
                }
            }
        }
    }
}

@Composable private fun MatchSummary(game: Game) {
    Card(Modifier.fillMaxWidth(),insideMargin=PaddingValues(18.dp)) {
        Text(game.kind+" · "+game.status,color=muted,fontSize=12.sp,modifier=Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            listOf(game.away to "客场",game.home to "主场").forEachIndexed {index,(t,role) ->
                if(index==1) Text("VS",color=muted,fontSize=12.sp,modifier=Modifier.padding(horizontal=6.dp))
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                    Badge(t,44)
                    Text(role,color=teamInk(game,t),fontSize=10.sp,modifier=Modifier.padding(top=6.dp))
                    Text(t.shortName,color=teamInk(game,t),fontSize=18.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=5.dp))
                    Text(t.english,color=muted,fontSize=10.sp,lineHeight=14.sp,textAlign=TextAlign.Center,modifier=Modifier.padding(top=3.dp))
                    Text(if(game.live||game.finished) (if(index==0) game.awayScore else game.homeScore) else "—",color=teamInk(game,t),fontSize=34.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=10.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp));Rule();Spacer(Modifier.height(12.dp))
        Text(game.instant?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("M月d日 HH:mm")) ?: "开赛时间待定",color=muted,fontSize=11.sp)
        Text(arenas[game.venue] ?: game.venue.ifBlank {"场馆信息暂未提供"},color=ink,fontSize=12.sp,modifier=Modifier.padding(top=6.dp))
        if(game.venue in arenas) Text(game.venue,color=muted,fontSize=10.sp,modifier=Modifier.padding(top=3.dp))
    }
}

@Composable private fun StatsCell(value: String,width: Dp,color: Color,strong: Boolean=false) {
    Text(value,color=color,fontSize=11.sp,lineHeight=16.sp,fontWeight=if(strong) FontWeight.Bold else FontWeight.Normal,textAlign=TextAlign.Center,
        modifier=Modifier.width(width).padding(horizontal=3.dp,vertical=11.dp),maxLines=2)
}
@Composable private fun PlayerCell(p: PlayerLine,key: String,width: Dp,color: Color,highlights: List<Boolean>) {
    val raw=if(p.dnp) "—" else p.cells[key] ?: "—"
    val value=if(key=="+/-") signedStat(raw) else raw
    val high=highlights.any {it}
    val text=if(key in listOf("FG","3PT","FT") && raw.split("-").size==2) {
        val parts=raw.split("-")
        AnnotatedString.Builder().apply {parts.forEachIndexed {i,s -> if(i>0) append("–");pushStyle(SpanStyle(fontWeight=if(highlights.getOrNull(i)==true) FontWeight.Bold else FontWeight.Normal));append(s);pop()}}.toAnnotatedString()
    } else AnnotatedString(value,SpanStyle(fontWeight=if(high) FontWeight.Bold else FontWeight.Normal))
    Box(Modifier.width(width).padding(horizontal=2.dp).background(if(high) color.copy(alpha=.12f) else Color.Transparent,RoundedCornerShape(8.dp)).padding(vertical=11.dp),contentAlignment=Alignment.Center) {
        Text(text,color=if(p.dnp) muted else color,fontSize=11.sp,lineHeight=16.sp,textAlign=TextAlign.Center,maxLines=2)
    }
}
@Composable private fun PeriodCard(game: Game) {
    Card(Modifier.fillMaxWidth(),insideMargin=PaddingValues(14.dp)) {
        Text("每节比分",color=ink,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
        val count=max(game.homePeriods.size,game.awayPeriods.size)
        if(count==0) Text("暂无分节数据",color=muted,fontSize=12.sp,modifier=Modifier.padding(top=14.dp))
        else Column(Modifier.padding(top=8.dp).horizontalScroll(rememberScrollState())) {
            Row {StatsCell("球队",76.dp,muted);repeat(count) {StatsCell(if(it<4) "Q${it+1}" else "OT${it-3}",44.dp,muted)};StatsCell("总分",48.dp,muted)}
            listOf(game.away to game.awayPeriods, game.home to game.homePeriods).forEach {(team,values) ->
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Row(Modifier.width(76.dp),verticalAlignment=Alignment.CenterVertically) {Badge(team,20);Text(team.shortName,color=teamInk(game,team),fontSize=11.sp,modifier=Modifier.padding(start=4.dp))}
                    repeat(count) {StatsCell(values.getOrElse(it) {"—"},44.dp,teamInk(game,team))}
                    StatsCell(if(team.id==game.home.id) game.homeScore else game.awayScore,48.dp,teamInk(game,team),true)
                }
            }
        }
    }
}

@Composable private fun TeamComparison(detail: Detail) {
    val game=detail.game
    Card(Modifier.fillMaxWidth(),insideMargin=PaddingValues(16.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf(game.away,game.home).forEachIndexed {index,t ->
                Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=if(index==0) Arrangement.Start else Arrangement.End) {
                    Badge(t,27);Column(Modifier.padding(start=7.dp)) {Text(t.shortName,color=teamInk(game,t),fontSize=13.sp,fontWeight=FontWeight.SemiBold);Text(t.english,color=muted,fontSize=8.sp,lineHeight=12.sp,maxLines=2)}
                }
            }
        }
        Spacer(Modifier.height(12.dp));Rule()
        detail.stats.forEachIndexed {index,s ->
            val away=s.away.substringBefore('-').toDoubleOrNull()
            val home=s.home.substringBefore('-').toDoubleOrNull()
            Row(Modifier.fillMaxWidth().padding(top=15.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(s.away,color=awayColor,fontSize=15.sp,fontWeight=if(away!=null && home!=null && away>=home) FontWeight.Bold else FontWeight.Normal,modifier=Modifier.weight(1f))
                Text(s.label,color=muted,fontSize=11.sp)
                Text(s.home,color=homeColor,fontSize=15.sp,fontWeight=if(home!=null && away!=null && home>=away) FontWeight.Bold else FontWeight.Normal,modifier=Modifier.weight(1f),textAlign=TextAlign.End)
            }
            if(away!=null && home!=null && away>=0 && home>=0 && away+home>0) {
                val a=awayColor;val h=homeColor
                Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                    val fraction=(away/(away+home)).toFloat()
                    drawLine(a.copy(alpha=.7f),Offset.Zero,Offset(size.width*fraction,0f),strokeWidth=size.height)
                    drawLine(h.copy(alpha=.7f),Offset(size.width*fraction,0f),Offset(size.width,0f),strokeWidth=size.height)
                }
            }
            Spacer(Modifier.height(12.dp));if(index<detail.stats.lastIndex) Rule()
        }
    }
}

@Composable private fun ScoreCurve(detail: Detail) {
    val game=detail.game
    val points=detail.points
    var touch by remember(game.id,points) {mutableStateOf<Float?>(null)}
    var canvasWidth by remember {mutableFloatStateOf(1f)}
    val density=LocalDensity.current
    val home=homeColor;val away=awayColor;val grid=line;val label=muted
    val maxX=max(2880,points.maxOfOrNull {p -> if(p.period<=4) p.period*720 else 2880+(p.period-4)*300} ?: 2880)
    val maxY=(ceil((points.maxOfOrNull {max(it.away,it.home)} ?: 0).coerceAtLeast(1)/20.0)*20).toFloat()
    val left=with(density) {30.dp.toPx()};val right=with(density) {10.dp.toPx()}
    val selected=touch?.let { fraction -> val second=(fraction*maxX).toInt(); points.lastOrNull {it.elapsed<=second} ?: points.firstOrNull() }
    Card(Modifier.fillMaxWidth(),insideMargin=PaddingValues(16.dp)) {
        Text("比分走势",color=ink,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().padding(top=12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("● ${game.away.shortName} · 客场",color=away,fontSize=11.sp)
            Text("● ${game.home.shortName} · 主场",color=home,fontSize=11.sp)
        }
        if(points.size<2) Text(if(game.state=="pre") "开赛后展示真实比分走势" else "来源暂未提供逐次比分",color=muted,fontSize=12.sp,modifier=Modifier.padding(vertical=24.dp))
        else {
            Canvas(Modifier.fillMaxWidth().height((190*density.fontScale.coerceAtMost(1.5f)).dp).padding(top=10.dp).onSizeChanged {canvasWidth=it.width.toFloat()}.semantics {contentDescription="${game.away.shortName}与${game.home.shortName}的累计得分走势，长按并拖动查看时刻比分"}.pointerInput(points,maxX) {
                detectDragGesturesAfterLongPress(onDragStart={p -> touch=((p.x-left)/(canvasWidth-left-right)).coerceIn(0f,1f)},onDragEnd={touch=null},onDragCancel={touch=null}) {change,_ ->
                    change.consume();touch=((change.position.x-left)/(canvasWidth-left-right)).coerceIn(0f,1f)
                }
            }) {
                val top=8.dp.toPx();val bottom=size.height-28.dp.toPx();val width=size.width-left-right;val height=bottom-top
                fun x(time:Int)=left+width*time/maxX
                fun y(score:Int)=bottom-height*score/maxY
                val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {color=android.graphics.Color.argb(210,(label.red*255).toInt(),(label.green*255).toInt(),(label.blue*255).toInt());textSize=10.sp.toPx()}
                repeat(5) {i -> val score=maxY*i/4; val yy=bottom-height*i/4;drawLine(grid,Offset(left,yy),Offset(size.width-right,yy),1.dp.toPx());drawContext.canvas.nativeCanvas.drawText(score.toInt().toString(),0f,yy+3.dp.toPx(),paint)}
                val periods=max(4,points.maxOf {it.period})
                repeat(periods) {index ->
                    val start=if(index<4) index*720 else 2880+(index-4)*300
                    val length=if(index<4) 720 else 300
                    drawLine(grid,Offset(x(start),top),Offset(x(start),bottom),1.dp.toPx())
                    val text=if(index<4) "第${index+1}节" else "OT${index-3}"
                    paint.textAlign=android.graphics.Paint.Align.CENTER
                    drawContext.canvas.nativeCanvas.drawText(text,x(start+length/2),size.height-4.dp.toPx(),paint)
                }
                fun curve(homeTeam:Boolean):Path {val path=Path();points.forEachIndexed {index,p -> val yy=y(if(homeTeam) p.home else p.away);if(index==0) path.moveTo(x(p.elapsed),yy) else {val previous=points[index-1];path.lineTo(x(p.elapsed),y(if(homeTeam) previous.home else previous.away));path.lineTo(x(p.elapsed),yy)}};return path}
                drawPath(curve(false),away,style=Stroke(width=2.5.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(),3.dp.toPx()))))
                drawPath(curve(true),home,style=Stroke(width=2.5.dp.toPx()))
                selected?.let { p ->
                    drawLine(label.copy(alpha=.55f),Offset(x(p.elapsed),top),Offset(x(p.elapsed),bottom),1.dp.toPx())
                    drawCircle(away,4.dp.toPx(),Offset(x(p.elapsed),y(p.away)));drawCircle(home,4.dp.toPx(),Offset(x(p.elapsed),y(p.home)))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top=10.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(selected?.let {"${if(it.period<=4) "第${it.period}节" else "加时${it.period-4}"} · ${it.clock}"} ?: "长按曲线并拖动查看比分",color=muted,fontSize=10.sp)
                if(selected!=null) {Text("${selected.away}",color=away,fontSize=12.sp,fontWeight=FontWeight.Bold);Text("${selected.home}",color=home,fontSize=12.sp,fontWeight=FontWeight.Bold)}
            }
            if(!detail.curveComplete) Text("逐次比分不完整，仅展示已获取的区间",color=muted,fontSize=10.sp,modifier=Modifier.padding(top=8.dp))
        }
    }
}
