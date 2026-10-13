package app.courtside.nba

import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale

data class SeasonPlayer(val id: String, val name: String, val english: String, val teams: List<Team>, val values: Map<String, Double>) {
    fun value(key: String, totals: Boolean): Double? = values[seasonColumns.firstOrNull { it.key == key }?.let { if(totals) it.total else it.average } ?: key]
}
data class SeasonColumn(val key: String, val label: String, val average: String, val total: String, val percent: Boolean = false)
val seasonColumns = listOf(
    SeasonColumn("GP", "场次", "gamesPlayed", "gamesPlayed"), SeasonColumn("MIN", "时间", "avgMinutes", "minutes"),
    SeasonColumn("PTS", "得分", "avgPoints", "points"), SeasonColumn("REB", "篮板", "avgRebounds", "rebounds"),
    SeasonColumn("AST", "助攻", "avgAssists", "assists"), SeasonColumn("STL", "抢断", "avgSteals", "steals"),
    SeasonColumn("BLK", "盖帽", "avgBlocks", "blocks"), SeasonColumn("TO", "失误", "avgTurnovers", "turnovers"),
    SeasonColumn("FGM", "命中", "avgFieldGoalsMade", "fieldGoalsMade"), SeasonColumn("FGA", "出手", "avgFieldGoalsAttempted", "fieldGoalsAttempted"),
    SeasonColumn("FG%", "投篮%", "fieldGoalPct", "fieldGoalPct", true),
    SeasonColumn("3PM", "三分", "avgThreePointFieldGoalsMade", "threePointFieldGoalsMade"),
    SeasonColumn("3PA", "三分出手", "avgThreePointFieldGoalsAttempted", "threePointFieldGoalsAttempted"),
    SeasonColumn("3P%", "三分%", "threePointFieldGoalPct", "threePointFieldGoalPct", true),
    SeasonColumn("FTM", "罚球", "avgFreeThrowsMade", "freeThrowsMade"), SeasonColumn("FTA", "罚球出手", "avgFreeThrowsAttempted", "freeThrowsAttempted"),
    SeasonColumn("FT%", "罚球%", "freeThrowPct", "freeThrowPct", true), SeasonColumn("PF", "犯规", "avgFouls", "fouls")
)
val defaultSeasonColumns = listOf("GP", "MIN", "PTS", "REB", "AST", "STL", "BLK", "FG%", "3P%", "FT%", "TO")
data class LeagueStats(val season: Int, val type: Int, val rows: List<SeasonPlayer>)
data class PlayerGame(val id: String, val instant: Instant?, val opponent: Team, val away: Boolean, val result: String, val cells: Map<String, String>)
data class PlayerSelection(val row: SeasonPlayer, val season: Int, val type: Int)

/** The endpoint includes both accumulated and per-game fields. Never reconstruct totals from rounded averages. */
fun parseLeague(j: JSONObject, season: Int, type: Int, names: PlayerNames = PlayerNames()): LeagueStats {
    if(!j.has("athletes")) {
        require(j.optJSONObject("currentSeason")?.optInt("year") == season && !j.has("pagination")) { "球员数据返回的赛季不符" }
        return LeagueStats(season, type, emptyList())
    }
    val requested = j.getJSONObject("requestedSeason")
    require(requested.getInt("year") == season && requested.getJSONObject("type").getInt("type") == type) { "球员数据返回的赛季或阶段不符" }
    val schemas = j.array("categories").objects().associate { it.text("name") to it.array("names").strings() }
    val rows = j.array("athletes").objects().map { row ->
        val a = row.getJSONObject("athlete")
        val values = row.array("categories").objects().flatMap { category ->
            val numbers = category.array("values")
            schemas[category.text("name")].orEmpty().mapIndexedNotNull { i, key ->
                numbers.optDouble(i, Double.NaN).takeIf { it.isFinite() }?.let { key to it }
            }
        }.toMap().toMutableMap()
        // Zero attempts have no meaningful shooting percentage.
        listOf(Triple("fieldGoalsMade", "fieldGoalsAttempted", "fieldGoalPct"), Triple("threePointFieldGoalsMade", "threePointFieldGoalsAttempted", "threePointFieldGoalPct"), Triple("freeThrowsMade", "freeThrowsAttempted", "freeThrowPct")).forEach { (made, attempts, pct) ->
            val n = values[attempts]; val m = values[made]
            if(n != null && m != null) { if(n > 0) values[pct] = m / n * 100 else values.remove(pct) }
        }
        SeasonPlayer(a.getString("id"), names.matchName(a.text("id"), a.text("displayName")), a.text("displayName"), a.array("teams").objects().map(::parseTeam).distinctBy { it.id }, values)
    }
    require(rows.map { it.id }.distinct().size == rows.size) { "来源球员重复，未展示不完整数据" }
    return LeagueStats(season, type, rows)
}
fun seasonNumber(value: Double?, key: String, totals: Boolean): String {
    if(value == null || !value.isFinite()) return "—"
    val pct = seasonColumns.firstOrNull { it.key == key }?.percent == true
    return if(key == "GP" || totals && !pct) String.format(Locale.US, "%.0f", value)
    else String.format(Locale.US, "%.1f%s", value, if(pct) "%" else "")
}
fun parsePlayerGames(j: JSONObject, season: Int, type: Int): List<PlayerGame> {
    require(j.array("filters").objects().firstOrNull { it.text("name") == "season" }?.text("value") == season.toString()) { "比赛记录赛季不符" }
    val keys = j.array("names").strings()
    val events = j.optJSONObject("events") ?: JSONObject()
    return j.array("seasonTypes").objects().filter { it.text("displayName").contains(if(type == 3) "Postseason" else "Regular Season") }
        .flatMap { it.array("categories").objects() }.flatMap { it.array("events").objects() }.mapNotNull { row ->
            val e = events.optJSONObject(row.text("eventId")) ?: return@mapNotNull null
            val opponent = e.optJSONObject("opponent") ?: return@mapNotNull null
            PlayerGame(e.text("id"), runCatching { OffsetDateTime.parse(e.text("gameDate")).toInstant() }.getOrNull(), parseTeam(opponent), e.text("atVs") == "@", e.text("gameResult"), keys.zip(row.array("stats").strings()).toMap())
        }.distinctBy { it.id }.sortedByDescending { it.instant }.take(5)
}
