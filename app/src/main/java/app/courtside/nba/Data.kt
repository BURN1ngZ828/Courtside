package app.courtside.nba

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

data class Team(val id: String, val abbr: String, val english: String, val logo: String = "", val color: String = "385D99") {
    val name: String get() = chineseNames[id] ?: english
    val shortName: String get() = shortNames[id] ?: name
}
private val shortNames = listOf("老鹰", "凯尔特人", "鹈鹕", "公牛", "骑士", "独行侠", "掘金", "活塞", "勇士", "火箭", "步行者", "快船", "湖人", "热火", "雄鹿", "森林狼", "篮网", "尼克斯", "魔术", "76人", "太阳", "开拓者", "国王", "马刺", "雷霆", "爵士", "奇才", "猛龙", "灰熊", "黄蜂").mapIndexed { index, name -> (index+1).toString() to name }.toMap()
fun shortTeamName(id: String) = shortNames[id] ?: chineseNames[id] ?: id
val chineseNames = mapOf("1" to "亚特兰大老鹰", "2" to "波士顿凯尔特人", "3" to "新奥尔良鹈鹕", "4" to "芝加哥公牛", "5" to "克利夫兰骑士", "6" to "达拉斯独行侠", "7" to "丹佛掘金", "8" to "底特律活塞", "9" to "金州勇士", "10" to "休斯敦火箭", "11" to "印第安纳步行者", "12" to "洛杉矶快船", "13" to "洛杉矶湖人", "14" to "迈阿密热火", "15" to "密尔沃基雄鹿", "16" to "明尼苏达森林狼", "17" to "布鲁克林篮网", "18" to "纽约尼克斯", "19" to "奥兰多魔术", "20" to "费城76人", "21" to "菲尼克斯太阳", "22" to "波特兰开拓者", "23" to "萨克拉门托国王", "24" to "圣安东尼奥马刺", "25" to "俄克拉荷马城雷霆", "26" to "犹他爵士", "27" to "华盛顿奇才", "28" to "多伦多猛龙", "29" to "孟菲斯灰熊", "30" to "夏洛特黄蜂")
val westIds = setOf("6", "7", "9", "10", "12", "13", "16", "21", "22", "23", "24", "25", "26", "29", "3")
fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
fun JSONArray.strings(): List<String> = (0 until length()).map { optString(it, "—") }
fun JSONObject.array(key: String): JSONArray = optJSONArray(key) ?: JSONArray()
fun JSONObject.text(key: String, fallback: String = ""): String = if (isNull(key)) fallback else optString(key, fallback)
fun parseTeam(j: JSONObject): Team = Team(j.getString("id"), j.text("abbreviation"), j.text("displayName"), j.text("logo").ifBlank { j.array("logos").optJSONObject(0)?.text("href") ?: "" }, j.text("color", "385D99"))

data class Game(val id: String, val instant: Instant?, val home: Team, val away: Team,
    val homeScore: String, val awayScore: String, val state: String, val statusName: String,
    val period: Int, val clock: String, val seasonType: Int, val homePeriods: List<String>, val awayPeriods: List<String>, val venue: String = "") {
    val live get() = state == "in"
    val finished get() = state == "post"
    val kind get() = when(seasonType) { 1 -> "季前赛"; 2 -> "常规赛"; 3 -> "季后赛"; else -> "NBA" }
    val status: String get() = when {
        statusName.contains("POSTPON") -> "已延期"
        statusName.contains("CANCEL") -> "已取消"
        statusName.contains("DELAY") -> "比赛延迟"
        statusName.contains("SUSPEND") -> "比赛暂停"
        finished -> "已结束"
        statusName.contains("HALFTIME") -> "中场休息"
        live -> (if(period > 4) "加时 ${period - 4}" else "第 $period 节") + " · $clock"
        state == "pre" -> if(instant == null) "时间待定" else "未开始"
        else -> "状态待更新"
    }
    fun involves(id: String) = home.id == id || away.id == id
}
fun parseGame(j: JSONObject): Game {
    val c = j.array("competitions").getJSONObject(0)
    val people = c.array("competitors").objects()
    val h = people.first { it.text("homeAway") == "home" }
    val a = people.first { it.text("homeAway") == "away" }
    val s = c.optJSONObject("status") ?: j.optJSONObject("status") ?: JSONObject()
    val t = s.optJSONObject("type") ?: JSONObject()
    fun score(x: JSONObject): String = x.optJSONObject("score")?.text("displayValue") ?: x.text("score", "—")
    fun periods(x: JSONObject) = x.array("linescores").objects().map { it.text("displayValue").ifBlank { if(it.has("value")) it.optDouble("value").toInt().toString() else "—" } }
    val time = if(!c.optBoolean("timeValid", j.optBoolean("timeValid", true))) null else runCatching { OffsetDateTime.parse(j.text("date").ifBlank { c.text("date") }).toInstant() }.getOrNull()
    return Game(j.getString("id"), time, parseTeam(h.getJSONObject("team")), parseTeam(a.getJSONObject("team")), score(h), score(a), t.text("state"), t.text("name"), s.optInt("period"), s.text("displayClock"), j.optJSONObject("seasonType")?.optInt("type") ?: j.optJSONObject("season")?.optInt("type") ?: 0, periods(h), periods(a), c.optJSONObject("venue")?.text("fullName") ?: "")
}
fun parseEvents(j: JSONObject): List<Game> = j.getJSONArray("events").objects().map(::parseGame)
fun gamesForDay(games: List<Game>, day: LocalDate, zone: ZoneId): List<Game> = games.distinctBy { it.id }.filter { it.instant?.atZone(zone)?.toLocalDate() == day }.sortedBy { it.instant }
fun isWinner(game: Game, team: String): Boolean {
    if(!game.finished) return false
    val away = game.awayScore.toIntOrNull() ?: return false
    val home = game.homeScore.toIntOrNull() ?: return false
    return (team == game.away.id && away > home) || (team == game.home.id && home > away)
}
fun leadingTeam(game: Game): String? {
    if(!game.live && !game.finished) return null
    val away=game.awayScore.toIntOrNull()?.takeIf {it>=0} ?: return null
    val home=game.homeScore.toIntOrNull()?.takeIf {it>=0} ?: return null
    return when {away>home -> game.away.id;home>away -> game.home.id;else -> null}
}
fun leadLabel(game: Game): String {
    if(!game.live && !game.finished) return ""
    val away=game.awayScore.toIntOrNull()?.takeIf {it>=0} ?: return ""
    val home=game.homeScore.toIntOrNull()?.takeIf {it>=0} ?: return ""
    if(away==home) return "比分持平"
    val team=if(away>home) game.away else game.home
    return if(game.finished) "${team.shortName}获胜 · 领先${kotlin.math.abs(away-home)}分" else "${team.shortName}领先${kotlin.math.abs(away-home)}分"
}
fun homeGames(today: List<Game>, schedule: List<Game>, team: String, now: Instant): List<Game> {
    if(team.isBlank()) return emptyList()
    val games = (today+schedule).filter { it.involves(team) }.distinctBy { it.id }
    val live = games.firstOrNull { it.live }
    val upcoming = games.filter { it.state=="pre" && it.instant?.let { time -> time>now } == true && !it.statusName.contains("POSTPON") && !it.statusName.contains("CANCEL") }.sortedBy { it.instant }
    val recent = games.filter { it.finished }.maxByOrNull { it.instant ?: Instant.MIN }
    return (listOfNotNull(live ?: upcoming.firstOrNull(), recent)+upcoming).distinctBy { it.id }.take(3)
}
data class Standing(val team: Team, val east: Boolean, val rank: Int, val wins: Int, val losses: Int, val pct: String)
data class Table(val season: Int, val rows: List<Standing>)
fun parseStandings(j: JSONObject): Table {
    val groups = j.getJSONArray("children").objects()
    val season = groups.first().getJSONObject("standings").getInt("season")
    val rows = groups.flatMap { group ->
        val table = group.getJSONObject("standings")
        require(table.optInt("seasonType") == 2) { "返回的不是常规赛排名" }
        table.getJSONArray("entries").objects().map { entry ->
            val stats = entry.array("stats").objects().associateBy { it.text("name") }
            fun n(key: String) = stats[key]?.optDouble("value", 0.0)?.toInt() ?: 0
            Standing(parseTeam(entry.getJSONObject("team")), group.text("name").contains("Eastern"), n("playoffSeed"), n("wins"), n("losses"), stats["winPercent"]?.text("displayValue", "—") ?: "—")
        }
    }
    return Table(season, rows)
}
data class PlayerLine(val name: String, val dnp: Boolean, val cells: Map<String, String>, val id: String = "", val english: String = "", val onCourt: Boolean = false, val starter: Boolean = false)
data class PlayerTable(val team: Team, val players: List<PlayerLine>, val totals: Map<String, String> = emptyMap())
data class TeamStat(val label: String, val away: String, val home: String)
data class ScorePoint(val elapsed: Int, val period: Int, val clock: String, val away: Int, val home: Int)
data class Detail(val game: Game, val players: List<PlayerTable>, val stats: List<TeamStat>, val points: List<ScorePoint> = emptyList(), val curveComplete: Boolean = false)
fun parseDetail(j: JSONObject, playerNames: PlayerNames = PlayerNames()): Detail {
    val parsed = parseGame(j.getJSONObject("header"))
    val game = parsed.copy(venue = parsed.venue.ifBlank { j.optJSONObject("gameInfo")?.optJSONObject("venue")?.text("fullName") ?: "" })
    val box = j.optJSONObject("boxscore") ?: JSONObject()
    val players = box.array("players").objects().map { block ->
        val group = block.array("statistics").optJSONObject(0) ?: JSONObject()
        val labels = group.array("labels").strings()
        PlayerTable(parseTeam(block.getJSONObject("team")), group.array("athletes").objects().map { p ->
            val athlete = p.getJSONObject("athlete")
            PlayerLine(playerNames.matchName(athlete.text("id"), athlete.text("displayName")), p.optBoolean("didNotPlay"), labels.zip(p.array("stats").strings()).toMap(), athlete.text("id"), athlete.text("displayName"), game.live && p.optBoolean("active"), p.optBoolean("starter"))
        }, labels.zip(group.array("totals").strings()).toMap())
    }
    val teams = box.array("teams").objects().associate { it.getJSONObject("team").getString("id") to it.array("statistics").objects().associate { s -> s.text("name") to s.text("displayValue", "—") } }
    val names = linkedMapOf("fieldGoalsMade-fieldGoalsAttempted" to "投篮", "threePointFieldGoalsMade-threePointFieldGoalsAttempted" to "三分", "freeThrowsMade-freeThrowsAttempted" to "罚球", "totalRebounds" to "篮板", "assists" to "助攻", "turnovers" to "失误", "steals" to "抢断", "blocks" to "盖帽", "offensiveRebounds" to "前场篮板", "defensiveRebounds" to "后场篮板", "fouls" to "犯规")
    val points = parseScorePoints(j.array("plays"))
    val last = points.lastOrNull()
    // A missing beginning or missing tail must never be presented as a complete game trace.
    val complete = points.firstOrNull()?.let { it.elapsed == 0 && it.away == 0 && it.home == 0 } == true && last?.away == game.awayScore.toIntOrNull() && last?.home == game.homeScore.toIntOrNull()
    return Detail(game, players, names.map { (k, v) -> TeamStat(v, teams[game.away.id]?.get(k) ?: "—", teams[game.home.id]?.get(k) ?: "—") }, points, complete)
}

fun elapsedSeconds(period: Int, clock: String): Int? {
    if(period < 1) return null
    val bits = clock.split(":")
    if(bits.size != 2) return null
    val minutes = bits[0].toIntOrNull() ?: return null
    val seconds = bits[1].toIntOrNull() ?: return null
    val length = if(period <= 4) 720 else 300
    val remaining = minutes * 60 + seconds
    if(minutes < 0 || seconds !in 0..59 || remaining !in 0..length) return null
    return (if(period <= 4) (period - 1) * 720 else 2880 + (period - 5) * 300) + length - remaining
}

fun parseScorePoints(plays: JSONArray): List<ScorePoint> = plays.objects().mapIndexedNotNull { index, p ->
    val period = p.optJSONObject("period")?.optInt("number") ?: return@mapIndexedNotNull null
    val clock = p.optJSONObject("clock")?.text("displayValue") ?: return@mapIndexedNotNull null
    val elapsed = elapsedSeconds(period, clock) ?: return@mapIndexedNotNull null
    if(p.isNull("homeScore") || p.isNull("awayScore")) return@mapIndexedNotNull null
    val home = p.text("homeScore").toIntOrNull() ?: return@mapIndexedNotNull null
    val away = p.text("awayScore").toIntOrNull() ?: return@mapIndexedNotNull null
    if(home < 0 || away < 0) return@mapIndexedNotNull null
    Triple(elapsed, p.text("sequenceNumber").toLongOrNull() ?: index.toLong(), ScorePoint(elapsed, period, clock, away, home))
}.sortedWith(compareBy({ it.first }, { it.second })).map { it.third }.distinct()

/** Shooting columns contain two independent quantities; signed +/- is never compared by magnitude. */
fun statNumbers(key: String, value: String): List<Double?> = when(key) {
    "FG", "3PT", "FT" -> value.split("-").let { if(it.size == 2) it.map(String::toDoubleOrNull) else listOf(null, null) }
    "MIN" -> listOf(if(value.contains(":")) value.split(":").let { p -> if(p.size==2) p[0].toDoubleOrNull()?.let { m -> p[1].toDoubleOrNull()?.let { s -> m*60+s } } else null } else value.toDoubleOrNull()?.times(60))
    else -> listOf(value.replace("−", "-").toDoubleOrNull())
}
fun statMaxima(players: List<PlayerLine>, key: String): List<Double?> {
    val values = players.filterNot { it.dnp }.map { statNumbers(key, it.cells[key] ?: "—") }
    val count = if(key in listOf("FG", "3PT", "FT")) 2 else 1
    return (0 until count).map { i -> values.mapNotNull { it.getOrNull(i) }.maxOrNull()?.takeIf { max -> max != 0.0 || key == "+/-" && values.any { (it.getOrNull(i) ?: 0.0) < 0 } } }
}
fun statHighlights(p: PlayerLine, key: String, maxima: List<Double?>): List<Boolean> =
    statNumbers(key, p.cells[key] ?: "—").mapIndexed { i, value -> !p.dnp && value != null && maxima.getOrNull(i) != null && value == maxima[i] }
fun signedStat(value: String): String = value.replace("−", "-").toIntOrNull()?.let { if(it > 0) "+$it" else it.toString() } ?: value

data class Loaded<T>(val data: T? = null, val updated: Long = 0, val error: String? = null, val loading: Boolean = false)
private data class Response(val json: JSONObject, val updated: Long, val error: String? = null)
class NbaRepository(context: Context) {
    private val playerNames = context.assets.open("players_zh.json").bufferedReader().use { PlayerNames(JSONArray(it.readText().removePrefix("\uFEFF"))) }
    val bundledTeams: List<Team> = context.assets.open("teams.json").bufferedReader().use { JSONArray(it.readText()).objects().map(::parseTeam) }
    private val cache = File(context.filesDir, "espn-v1").apply { mkdirs() }
    private val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(18, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build()
    private val base = "https://site.api.espn.com/apis/site/v2/sports/basketball/nba/"
    private val locks = ConcurrentHashMap<String, Mutex>()
    private fun fetchJson(url: String): JSONObject = client.newCall(Request.Builder().url(url).header("User-Agent", "Courtside-Android/1.0").build()).execute().use { response ->
        if(!response.isSuccessful) error("HTTP ${response.code}")
        JSONObject(response.body?.string() ?: error("空响应"))
    }
    private suspend fun request(key: String, url: String, ttl: Long, force: Boolean, fetch: (suspend () -> JSONObject)? = null, validate: (JSONObject) -> Unit): Response = withContext(Dispatchers.IO) {
        locks.getOrPut(key) { Mutex() }.withLock {
        val file = File(cache, "$key.json")
        val old = runCatching { JSONObject(file.readText()) }.getOrNull()
        if(!force && old != null && System.currentTimeMillis() - old.getLong("at") < ttl) return@withLock Response(old.getJSONObject("body"), old.getLong("at"))
        try {
            val json = fetch?.invoke() ?: fetchJson(url)
            validate(json)
            val now = System.currentTimeMillis()
            val temp = File(cache, "$key.tmp")
            temp.writeText(JSONObject().put("at", now).put("body", json).toString())
            if(!temp.renameTo(file)) { file.writeText(temp.readText()); temp.delete() }
            Response(json, now)
        } catch(e: Exception) {
            if(e is kotlinx.coroutines.CancellationException) throw e
            val message = when {
                e.message?.contains("403") == true -> "来源拒绝访问（403）"
                e.message?.contains("429") == true -> "请求受限，请稍后手动重试"
                e is java.net.UnknownHostException -> "无法连接 ESPN，请检查网络"
                e is java.io.InterruptedIOException -> "请求超时，请稍后重试"
                else -> "数据暂不可用，请稍后重试"
            }
            if(old != null) Response(old.getJSONObject("body"), old.getLong("at"), message) else throw java.io.IOException(message)
        }
        }
    }
    private suspend fun <T> load(block: suspend () -> Loaded<T>): Loaded<T> = withContext(Dispatchers.IO) { try { block() } catch(e: kotlinx.coroutines.CancellationException) { throw e } catch(e: Exception) { Loaded(error = e.message ?: "数据暂不可用") } }
    suspend fun teams(force: Boolean = false): Loaded<List<Team>> = load {
        fun parse(j: JSONObject) = j.getJSONArray("sports").getJSONObject(0).getJSONArray("leagues").getJSONObject(0).getJSONArray("teams").objects().map { parseTeam(it.getJSONObject("team")) }.filter { it.id in chineseNames }
        val r = request("teams", base + "teams", 86400000, force) { require(parse(it).size == 30) }
        Loaded(parse(r.json), r.updated, r.error)
    }.let { if(it.data == null) it.copy(data = bundledTeams, error = "使用内置球队资料 · " + it.error) else it }
    suspend fun day(day: LocalDate, zone: ZoneId, force: Boolean = false): Loaded<List<Game>> = load {
        // Query adjacent provider dates, then filter by the actual instant in the device timezone.
        val responses = coroutineScope {
            (-1L..1L).map { offset -> async {
                val date = day.plusDays(offset).format(DateTimeFormatter.BASIC_ISO_DATE)
                runCatching { request("day-$date", base + "scoreboard?dates=$date&limit=100", 60000, force) { parseEvents(it) } }
            } }.map { it.await() }
        }
        val good = responses.mapNotNull { it.getOrNull() }
        require(good.isNotEmpty()) { responses.first().exceptionOrNull()?.message ?: "赛程暂不可用" }
        val errors = good.mapNotNull { it.error } + responses.mapNotNull { it.exceptionOrNull()?.message }
        Loaded(gamesForDay(good.flatMap { parseEvents(it.json) }, day, zone), good.minOf { it.updated }, errors.distinct().joinToString("；").ifBlank { null })
    }
    suspend fun schedule(team: String, season: Int, force: Boolean = false): Loaded<List<Game>> = load {
        val results = coroutineScope { (1..3).map { type -> async {
            runCatching { request("schedule-$team-$season-$type", base + "teams/$team/schedule?season=$season&seasontype=$type", 600000, force) { parseEvents(it) } }
        } }.map { it.await() } }
        val good = results.mapNotNull { it.getOrNull() }
        require(good.isNotEmpty()) { "主队赛程暂不可用" }
        val errors = good.mapNotNull { it.error } + results.mapNotNull { it.exceptionOrNull()?.message }
        Loaded(good.flatMap { parseEvents(it.json) }.distinctBy { it.id }.sortedBy { it.instant }, good.minOf { it.updated }, errors.distinct().joinToString("；").ifBlank { null })
    }
    suspend fun standings(season: Int, force: Boolean = false): Loaded<Table> = load {
        val r = request("standings-$season", "https://site.web.api.espn.com/apis/v2/sports/basketball/nba/standings?season=$season&seasontype=2", 3600000, force) { parseStandings(it) }
        val table = parseStandings(r.json)
        require(table.season == season) { "来源返回了其他赛季，暂不展示" }
        Loaded(table, r.updated, r.error)
    }
    suspend fun detail(id: String, force: Boolean = false): Loaded<Detail> = load {
        val r = request("game-$id", base + "summary?event=$id", 60000, force) { parseDetail(it) }
        Loaded(parseDetail(r.json, playerNames), r.updated, r.error)
    }
    suspend fun league(season: Int, type: Int, force: Boolean = false): Loaded<LeagueStats> = load {
        val url = "https://site.web.api.espn.com/apis/common/v3/sports/basketball/nba/statistics/byathlete?season=$season&seasontype=$type&limit=100&sort=offensive.avgPoints:desc&page="
        val r = request("league-$season-$type", url + "1", 10800000, force, fetch = {
            val first = fetchJson(url + "1")
            parseLeague(first, season, type)
            val pagination = first.optJSONObject("pagination")
            if(pagination != null) {
                val count = pagination.getInt("count")
                val pages = pagination.getInt("pages")
                require(pages in 1..20 && count in 0..2000) { "分页数量异常" }
                val all = first.getJSONArray("athletes")
                // Fetch a bounded group of pages; only commit the cache after every page validates.
                (2..pages).toList().chunked(3).forEach { batch ->
                    val results = coroutineScope { batch.map { page -> async { fetchJson(url + page).also { j ->
                        parseLeague(j, season, type)
                        require(j.getJSONObject("pagination").getInt("count") == count && j.getJSONObject("pagination").getInt("pages") == pages && j.getJSONObject("pagination").getInt("page") == page) { "分页数据已变化，请重试" }
                    } } }.map { it.await() } }
                    results.forEach { it.array("athletes").objects().forEach(all::put) }
                }
                require(all.length() == count) { "全联盟数据未完整获取" }
            }
            first
        }) { parseLeague(it, season, type) }
        Loaded(parseLeague(r.json, season, type, playerNames), r.updated, r.error)
    }
    suspend fun playerGames(id: String, season: Int, type: Int, force: Boolean = false): Loaded<List<PlayerGame>> = load {
        require(id.matches(Regex("[0-9]+")))
        val url = "https://site.web.api.espn.com/apis/common/v3/sports/basketball/nba/athletes/$id/gamelog?season=$season&seasontype=$type"
        val r = request("playergames-$id-$season-$type", url, 3600000, force) { parsePlayerGames(it, season, type) }
        Loaded(parsePlayerGames(r.json, season, type), r.updated, r.error)
    }
}
