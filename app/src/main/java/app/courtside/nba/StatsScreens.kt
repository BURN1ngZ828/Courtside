@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package app.courtside.nba

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.max
import kotlinx.coroutines.delay

private val playerColumns=listOf("MIN" to "时间","PTS" to "得分","REB" to "篮板","AST" to "助攻","FG" to "投篮","3PT" to "三分","FT" to "罚球","STL" to "抢断","BLK" to "盖帽","TO" to "失误","OREB" to "前板","DREB" to "后板","PF" to "犯规","+/-" to "+/−")
private val arenas=mapOf("Madison Square Garden" to "麦迪逊广场花园","TD Garden" to "TD花园球馆","Chase Center" to "大通中心","United Center" to "联合中心","Barclays Center" to "巴克莱中心","American Airlines Center" to "美国航空中心","Toyota Center" to "丰田中心","Ball Arena" to "波尔球馆","Golden 1 Center" to "金色一号中心","Kaseya Center" to "卡西亚中心")
private fun statWidth(key:String,scale:Float):Dp=(when(key){"MIN","+/-" -> 40;"FG","3PT","FT" -> 48;else -> 34}).dp*scale
private fun venueName(game:Game)=arenas[game.venue] ?: game.venue.ifBlank {"场馆待更新"}

@Composable internal fun scoreInk(game:Game,team:Team):Color=if(leadingTeam(game)?.let {it!=team.id}==true) muted else ink

@Composable internal fun DetailPage(vm:AppViewModel,gameId:String) {
    var snapshot by remember(gameId){mutableStateOf(vm.detail)}
    LaunchedEffect(vm.detail,vm.detailId){if(vm.detailId==gameId) snapshot=vm.detail}
    val loaded=if(vm.detailId==gameId) vm.detail else snapshot
    val detail=loaded.data
    var tab by rememberSaveable(gameId){mutableIntStateOf(0)}
    var selectedTeam by rememberSaveable(gameId){mutableIntStateOf(0)}
    var expandedPlayer by remember {mutableStateOf<PlayerLine?>(null)}
    val overviewState=rememberLazyListState()
    val playerState=rememberLazyListState()
    val horizontal=rememberScrollState()
    val tables=detail?.let {d -> listOf(d.game.away,d.game.home).mapNotNull {t -> d.players.find {it.team.id==t.id}}}.orEmpty()
    val table=tables.getOrNull(selectedTeam.coerceAtMost((tables.size-1).coerceAtLeast(0)))
    // Preserve the first received player order so a refresh cannot move rows under the reader.
    val orders=remember(gameId){mutableMapOf<String,List<String>>()}
    fun identity(p:PlayerLine)=p.id.ifBlank {p.english.ifBlank {p.name}}
    val order=table?.let {orders[it.team.id] ?: it.players.map(::identity)}.orEmpty()
    SideEffect {if(table!=null && table.players.isNotEmpty()) orders.putIfAbsent(table.team.id,order)}
    val rows=table?.players.orEmpty().sortedBy {p -> order.indexOf(identity(p)).takeIf {it>=0} ?: Int.MAX_VALUE}
    val maxima=remember(rows){playerColumns.associate {it.first to statMaxima(rows,it.first)}}
    var expired by remember(loaded.updated){mutableStateOf(false)}
    LaunchedEffect(loaded.updated){delay((90000-(System.currentTimeMillis()-loaded.updated)).coerceAtLeast(0));expired=true}
    val fresh=!expired && loaded.error==null && loaded.updated>0 && System.currentTimeMillis()-loaded.updated<90000
    val collapsed by remember {derivedStateOf {overviewState.firstVisibleItemIndex>0 || overviewState.firstVisibleItemScrollOffset>80}}
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("‹",color=ink,fontSize=28.sp,modifier=Modifier.width(40.dp).heightIn(min=48.dp).clickable {vm.closeGame()}.wrapContentSize())
            Box(Modifier.weight(1f)) {
                if(detail!=null && (tab==1 || collapsed)) CompactScore(detail.game)
                else Text("比赛详情",color=ink,fontSize=19.sp,fontWeight=FontWeight.SemiBold)
            }
            Button({vm.openGame(gameId,true)},minWidth=48.dp,insideMargin=PaddingValues(horizontal=8.dp,vertical=8.dp)){Text("刷新",color=muted,fontSize=11.sp)}
        }
        Box(Modifier.padding(horizontal=12.dp,vertical=4.dp)){Segments(listOf("概览","球员"),tab){tab=it}}
        AnimatedContent(tab,modifier=Modifier.weight(1f),transitionSpec={fadeIn(tween(160)) togetherWith fadeOut(tween(100))},label="detail-tab") {shownTab ->
            if(shownTab==0) LazyColumn(Modifier.fillMaxSize(),state=overviewState,contentPadding=PaddingValues(start=12.dp,end=12.dp,bottom=20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(detail==null) item {Meta(loaded);Empty(if(loaded.loading) "正在获取比赛" else "比赛详情暂不可用","可刷新重试，未使用演示数据替代。")}
                else {
                    item(key="match"){MatchSummary(detail.game)}
                    item(key="periods"){PeriodCard(detail.game)}
                    item(key="curve"){ScoreCurve(detail)}
                    item(key="comparison"){TeamComparison(detail)}
                    item(key="updated"){Meta(loaded)}
                }
            } else BoxWithConstraints(Modifier.fillMaxSize()) {
                val scale=LocalDensity.current.fontScale.coerceAtLeast(1f)
                val nameWidth=((maxWidth-24.dp)*.27f).coerceIn(94.dp,110.dp)*scale.coerceAtMost(1.2f)
                val background=if(LocalDark.current) Color(0xFF151A24) else Color(0xFFF6F8FC)
                LazyColumn(Modifier.fillMaxSize(),state=playerState,contentPadding=PaddingValues(start=12.dp,end=12.dp,bottom=16.dp)) {
                    stickyHeader(key="player-header") {
                        Column(Modifier.fillMaxWidth().background(background,RoundedCornerShape(topStart=18.dp,topEnd=18.dp))) {
                            Row(Modifier.fillMaxWidth().padding(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                tables.forEachIndexed {index,t ->
                                    Row(Modifier.weight(1f).background(if(selectedTeam==index) accent.copy(alpha=.10f) else Color.Transparent,RoundedCornerShape(12.dp)).clickable {selectedTeam=index}.heightIn(min=48.dp).padding(horizontal=8.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {
                                        Badge(t.team,22)
                                        Column(Modifier.weight(1f).padding(start=6.dp)) {
                                            Text(t.team.shortName+if(selectedTeam==index) " ✓" else "",color=if(selectedTeam==index) accent else ink,fontSize=12.sp,lineHeight=16.sp,fontWeight=FontWeight.SemiBold)
                                            Text(t.team.english,color=muted,fontSize=9.sp,lineHeight=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth()) {
                                StatsCell("球员",nameWidth,muted)
                                Row(Modifier.weight(1f).horizontalScroll(horizontal)){playerColumns.forEach {(key,title)->StatsCell(title,statWidth(key,scale),muted)}}
                            }
                            Rule()
                        }
                    }
                    if(detail==null) item {Meta(loaded);Empty(if(loaded.loading) "正在获取统计" else "球员统计暂不可用","可刷新重试。")}
                    else if(detail.game.state=="pre") item {Empty("统计尚未开始","开赛且来源提供数据后显示。")}
                    else if(rows.isEmpty()) item {Empty("暂无球员统计","来源尚未提供本场球员数据。")}
                    else {
                        itemsIndexed(rows,key={i,p -> p.id.ifBlank {"$i-${p.english}"}}){index,p ->
                            val active=p.onCourt && detail.game.live && fresh
                            Row(Modifier.fillMaxWidth().background(if(index%2==0) background else background.copy(alpha=.62f)).heightIn(min=44.dp),verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.width(nameWidth).heightIn(min=44.dp).clickable {expandedPlayer=p}.padding(horizontal=6.dp,vertical=5.dp).semantics {stateDescription=if(active) "在场" else if(p.dnp) "未上场" else ""}) {
                                    Text(p.name+if(active) " ●" else "",color=if(active) (if(LocalDark.current) Color(0xFFFF9696) else Color(0xFFB62836)) else ink,fontSize=12.sp,lineHeight=16.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                                    Text((if(p.dnp) "未上场 · " else "")+p.english.ifBlank {p.name},color=muted,fontSize=10.sp,lineHeight=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                                }
                                Row(Modifier.weight(1f).horizontalScroll(horizontal)){playerColumns.forEach {(key,_) -> PlayerCell(p,key,statWidth(key,scale),statHighlights(p,key,maxima[key].orEmpty()))}}
                            }
                            Rule()
                        }
                        item {Text("蓝色：本队最高值 · 红点：在场 · 左右滑动查看更多",color=muted,fontSize=10.sp,lineHeight=15.sp,modifier=Modifier.padding(top=8.dp));Meta(loaded)}
                    }
                }
            }
        }
    }
    expandedPlayer?.let {p -> Dialog(onDismissRequest={expandedPlayer=null}) {
        Card(Modifier.fillMaxWidth(),insideMargin=PaddingValues(20.dp)) {
            Text(p.name,color=ink,fontSize=18.sp,fontWeight=FontWeight.SemiBold)
            Text(p.english,color=muted,fontSize=14.sp,modifier=Modifier.padding(top=6.dp))
            if(p.onCourt && detail?.game?.live==true && fresh) Text("当前在场",color=accent,fontSize=12.sp,modifier=Modifier.padding(top=6.dp))
            if(p.dnp) Text("本场未上场",color=muted,fontSize=12.sp,modifier=Modifier.padding(top=6.dp))
            Button({expandedPlayer=null},modifier=Modifier.padding(top=16.dp)){Text("关闭",color=ink)}
        }
    }}
}

@Composable private fun CompactScore(game:Game) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val showAbbreviations=maxWidth>=220.dp*LocalDensity.current.fontScale.coerceAtLeast(1f)
        Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            Badge(game.away,20);if(showAbbreviations) Text(game.away.abbr,color=muted,fontSize=10.sp)
            Text(if(game.live||game.finished) game.awayScore else "—",color=scoreInk(game,game.away),fontSize=18.sp,fontWeight=if(leadingTeam(game)==game.away.id) FontWeight.Bold else FontWeight.Medium,style=numericStyle)
            Text("–",color=muted,fontSize=12.sp)
            Text(if(game.live||game.finished) game.homeScore else "—",color=scoreInk(game,game.home),fontSize=18.sp,fontWeight=if(leadingTeam(game)==game.home.id) FontWeight.Bold else FontWeight.Medium,style=numericStyle)
            if(showAbbreviations) Text(game.home.abbr,color=muted,fontSize=10.sp);Badge(game.home,20)
        }
        Text(game.status,color=muted,fontSize=10.sp,lineHeight=13.sp,maxLines=1)
        }
    }
}

@Composable private fun MatchSummary(game:Game) {
    Card(Modifier.fillMaxWidth(),insideMargin=PaddingValues(12.dp)) {
        Text(venueName(game)+" · "+game.kind,color=muted,fontSize=10.sp,lineHeight=14.sp)
        Row(Modifier.fillMaxWidth().padding(vertical=9.dp),verticalAlignment=Alignment.CenterVertically) {
            @Composable fun TeamTitle(team:Team,role:String) {
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                    Badge(team,30)
                    Text(team.shortName+" · "+role,color=ink,fontSize=12.sp,lineHeight=17.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=3.dp))
                    Text(team.english,color=muted,fontSize=9.sp,lineHeight=12.sp,maxLines=2,textAlign=TextAlign.Center)
                }
            }
            TeamTitle(game.away,"客")
            Column(Modifier.padding(horizontal=8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(if(game.live||game.finished) game.awayScore else "—",color=scoreInk(game,game.away),fontSize=29.sp,fontWeight=if(leadingTeam(game)==game.away.id) FontWeight.Bold else FontWeight.Medium,style=numericStyle)
                    Text("–",color=muted,fontSize=18.sp)
                    Text(if(game.live||game.finished) game.homeScore else "—",color=scoreInk(game,game.home),fontSize=29.sp,fontWeight=if(leadingTeam(game)==game.home.id) FontWeight.Bold else FontWeight.Medium,style=numericStyle)
                }
                val lead=leadLabel(game)
                if(lead.isNotBlank()) Text(lead,color=ink,fontSize=10.sp,lineHeight=14.sp)
            }
            TeamTitle(game.home,"主")
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(game.status,color=if(game.live) accent else muted,fontSize=11.sp)
            Text(game.instant?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("M月d日 HH:mm")) ?: "时间待定",color=muted,fontSize=10.sp)
        }
    }
}

@Composable private fun StatsCell(value:String,width:Dp,color:Color,strong:Boolean=false) {
    Text(value,color=color,fontSize=11.sp,lineHeight=16.sp,fontWeight=if(strong) FontWeight.Bold else FontWeight.Normal,textAlign=TextAlign.Center,style=numericStyle,
        modifier=Modifier.width(width).padding(horizontal=2.dp,vertical=6.dp),maxLines=1)
}
@Composable private fun PlayerCell(p:PlayerLine,key:String,width:Dp,highlights:List<Boolean>) {
    val raw=if(p.dnp) "—" else p.cells[key] ?: "—"
    val value=if(key=="+/-") signedStat(raw) else raw
    val high=highlights.any {it}
    val text=if(key in listOf("FG","3PT","FT") && raw.split("-").size==2) {
        AnnotatedString.Builder().apply {raw.split("-").forEachIndexed {i,s -> if(i>0) append("–");pushStyle(SpanStyle(color=if(highlights.getOrNull(i)==true) accent else ink,fontWeight=if(highlights.getOrNull(i)==true) FontWeight.Bold else FontWeight.Normal));append(s);pop()}}.toAnnotatedString()
    } else AnnotatedString(value,SpanStyle(fontWeight=if(high) FontWeight.Bold else FontWeight.Normal))
    Text(text,color=if(p.dnp) muted else if(high) accent else ink,fontSize=11.sp,lineHeight=16.sp,textAlign=TextAlign.Center,style=numericStyle,maxLines=1,
        modifier=Modifier.width(width).padding(horizontal=2.dp,vertical=5.dp))
}
@Composable private fun PeriodCard(game:Game) {
    Card(Modifier.fillMaxWidth(),insideMargin=PaddingValues(10.dp)) {
        Text("每节比分",color=ink,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
        val count=max(game.homePeriods.size,game.awayPeriods.size)
        if(count==0) Text("暂无分节数据",color=muted,fontSize=11.sp,modifier=Modifier.padding(top=8.dp))
        else Column(Modifier.padding(top=4.dp).horizontalScroll(rememberScrollState())) {
            Row {StatsCell("球队",70.dp,muted);repeat(count){StatsCell(if(it<4) "Q${it+1}" else "OT${it-3}",38.dp,muted)};StatsCell("总分",44.dp,muted)}
            listOf(game.away to game.awayPeriods,game.home to game.homePeriods).forEach {(team,values) ->
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Row(Modifier.width(70.dp),verticalAlignment=Alignment.CenterVertically){Badge(team,18);Text(team.shortName,color=ink,fontSize=11.sp,modifier=Modifier.padding(start=4.dp))}
                    repeat(count){StatsCell(values.getOrElse(it){"—"},38.dp,ink)}
                    StatsCell(if(team.id==game.home.id) game.homeScore else game.awayScore,44.dp,scoreInk(game,team),leadingTeam(game)==team.id)
                }
            }
        }
    }
}
@Composable private fun TeamComparison(detail:Detail) {
    val game=detail.game
    Card(Modifier.fillMaxWidth(),insideMargin=PaddingValues(12.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Badge(game.away,22);Text(game.away.shortName,color=ink,fontSize=12.sp,modifier=Modifier.weight(1f).padding(start=5.dp))
            Text("球队统计",color=muted,fontSize=11.sp)
            Text(game.home.shortName,color=ink,fontSize=12.sp,modifier=Modifier.weight(1f),textAlign=TextAlign.End);Spacer(Modifier.width(5.dp));Badge(game.home,22)
        }
        Spacer(Modifier.height(5.dp));Rule()
        detail.stats.forEachIndexed {index,s ->
            val away=s.away.substringBefore('-').toDoubleOrNull();val home=s.home.substringBefore('-').toDoubleOrNull()
            Row(Modifier.fillMaxWidth().padding(top=8.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(s.away,color=ink,fontSize=13.sp,fontWeight=if(away!=null && home!=null && away>=home) FontWeight.SemiBold else FontWeight.Normal,style=numericStyle,modifier=Modifier.weight(1f))
                Text(s.label,color=muted,fontSize=11.sp)
                Text(s.home,color=ink,fontSize=13.sp,fontWeight=if(home!=null && away!=null && home>=away) FontWeight.SemiBold else FontWeight.Normal,style=numericStyle,modifier=Modifier.weight(1f),textAlign=TextAlign.End)
            }
            if(away!=null && home!=null && away>=0 && home>=0 && away+home>0) {
                val a=awayColor;val h=homeColor
                Canvas(Modifier.fillMaxWidth().height(2.dp)) {val fraction=(away/(away+home)).toFloat();drawLine(a.copy(alpha=.6f),Offset.Zero,Offset(size.width*fraction,0f),strokeWidth=size.height);drawLine(h.copy(alpha=.6f),Offset(size.width*fraction,0f),Offset(size.width,0f),strokeWidth=size.height)}
            }
            Spacer(Modifier.height(7.dp));if(index<detail.stats.lastIndex) Rule()
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
    Card(Modifier.fillMaxWidth(),insideMargin=PaddingValues(12.dp)) {
        Text("比分走势",color=ink,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("● ${game.away.shortName} · 客场",color=away,fontSize=11.sp)
            Text("● ${game.home.shortName} · 主场",color=home,fontSize=11.sp)
        }
        if(points.size<2) Text(if(game.state=="pre") "开赛后展示真实比分走势" else "来源暂未提供逐次比分",color=muted,fontSize=12.sp,modifier=Modifier.padding(vertical=24.dp))
        else {
            Canvas(Modifier.fillMaxWidth().height((170*density.fontScale.coerceAtMost(1.5f)).dp).padding(top=10.dp).onSizeChanged {canvasWidth=it.width.toFloat()}.semantics {contentDescription="${game.away.shortName}与${game.home.shortName}的累计得分走势，长按并拖动查看时刻比分"}.pointerInput(points,maxX) {
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
