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
}
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
data class PlayerLine(val name: String, val dnp: Boolean, val cells: Map<String, String>)
data class PlayerTable(val team: Team, val players: List<PlayerLine>)
data class TeamStat(val label: String, val away: String, val home: String)
data class Detail(val game: Game, val players: List<PlayerTable>, val stats: List<TeamStat>)
fun parseDetail(j: JSONObject): Detail {
    val game = parseGame(j.getJSONObject("header"))
    val box = j.optJSONObject("boxscore") ?: JSONObject()
    val players = box.array("players").objects().map { block ->
        val group = block.array("statistics").optJSONObject(0) ?: JSONObject()
        val labels = group.array("labels").strings()
        PlayerTable(parseTeam(block.getJSONObject("team")), group.array("athletes").objects().map { p ->
            PlayerLine(p.getJSONObject("athlete").text("displayName"), p.optBoolean("didNotPlay"), labels.zip(p.array("stats").strings()).toMap())
        })
    }
    val teams = box.array("teams").objects().associate { it.getJSONObject("team").getString("id") to it.array("statistics").objects().associate { s -> s.text("name") to s.text("displayValue", "—") } }
    val names = linkedMapOf("fieldGoalsMade-fieldGoalsAttempted" to "投篮", "threePointFieldGoalsMade-threePointFieldGoalsAttempted" to "三分", "freeThrowsMade-freeThrowsAttempted" to "罚球", "totalRebounds" to "篮板", "assists" to "助攻", "turnovers" to "失误", "steals" to "抢断", "blocks" to "盖帽")
    return Detail(game, players, names.map { (k, v) -> TeamStat(v, teams[game.away.id]?.get(k) ?: "—", teams[game.home.id]?.get(k) ?: "—") })
}

data class Loaded<T>(val data: T? = null, val updated: Long = 0, val error: String? = null, val loading: Boolean = false)
private data class Response(val json: JSONObject, val updated: Long, val error: String? = null)
class NbaRepository(context: Context) {
    val bundledTeams: List<Team> = context.assets.open("teams.json").bufferedReader().use { JSONArray(it.readText()).objects().map(::parseTeam) }
    private val cache = File(context.filesDir, "espn-v1").apply { mkdirs() }
    private val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(18, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build()
    private val base = "https://site.api.espn.com/apis/site/v2/sports/basketball/nba/"
    private val locks = ConcurrentHashMap<String, Mutex>()
    private suspend fun request(key: String, url: String, ttl: Long, force: Boolean, validate: (JSONObject) -> Unit): Response = withContext(Dispatchers.IO) {
        locks.getOrPut(key) { Mutex() }.withLock {
        val file = File(cache, "$key.json")
        val old = runCatching { JSONObject(file.readText()) }.getOrNull()
        if(!force && old != null && System.currentTimeMillis() - old.getLong("at") < ttl) return@withLock Response(old.getJSONObject("body"), old.getLong("at"))
        try {
            val json = client.newCall(Request.Builder().url(url).header("User-Agent", "Courtside-Android/0.1").build()).execute().use { response ->
                if(!response.isSuccessful) error("HTTP ${response.code}")
                JSONObject(response.body?.string() ?: error("空响应"))
            }
            validate(json)
            val now = System.currentTimeMillis()
            val temp = File(cache, "$key.tmp")
            temp.writeText(JSONObject().put("at", now).put("body", json).toString())
            if(!temp.renameTo(file)) { file.writeText(temp.readText()); temp.delete() }
            Response(json, now)
        } catch(e: Exception) {
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
    private suspend fun <T> load(block: suspend () -> Loaded<T>): Loaded<T> = try { block() } catch(e: kotlinx.coroutines.CancellationException) { throw e } catch(e: Exception) { Loaded(error = e.message ?: "数据暂不可用") }
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
        Loaded(parseDetail(r.json), r.updated, r.error)
    }
}
