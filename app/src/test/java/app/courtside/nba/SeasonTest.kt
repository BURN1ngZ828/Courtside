package app.courtside.nba

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SeasonTest {
    private fun fixture(name: String) = JSONObject(javaClass.getResource("/$name.json")!!.readText())
    @Test fun seasonTotalsComeFromActualTotalsAndPercentFromAttempts() {
        val row=parseLeague(fixture("league"),2026,2).rows.first()
        assertEquals(2143.0,row.value("PTS",true)!!,0.0)
        assertEquals(33.484375,row.value("PTS",false)!!,0.0001)
        assertEquals(693.0/1457.0*100,row.value("FG%",false)!!,0.0001)
        assertEquals("2143",seasonNumber(row.value("PTS",true),"PTS",true))
        assertEquals("33.5",seasonNumber(row.value("PTS",false),"PTS",false))
    }
    @Test fun wrongSeasonAndDuplicatePlayersCannotMasqueradeAsFullList() {
        val j=fixture("league")
        assertThrows(IllegalArgumentException::class.java) {parseLeague(j,2027,2)}
        j.getJSONArray("athletes").put(j.getJSONArray("athletes").getJSONObject(0))
        assertThrows(IllegalArgumentException::class.java) {parseLeague(j,2026,2)}
    }
    @Test fun preseasonHasExplicitEmptyRegularSeasonAndUnknownPayloadFails() {
        assertTrue(parseLeague(JSONObject("{currentSeason:{year:2027}}"),2027,2).rows.isEmpty())
        assertThrows(IllegalArgumentException::class.java) {parseLeague(JSONObject("{}"),2027,2)}
    }
    @Test fun nullAndZeroAttemptsStayMissing() {
        val j=fixture("league")
        val row=j.getJSONArray("athletes").getJSONObject(0)
        val schemas=j.getJSONArray("categories").objects().associate {it.text("name") to it.array("names").strings()}
        val offensive=row.getJSONArray("categories").objects().first {it.text("name")=="offensive"}
        offensive.getJSONArray("values").put(schemas["offensive"]!!.indexOf("fieldGoalsAttempted"),0)
        assertNull(parseLeague(j,2026,2).rows.first().value("FG%",true))
        assertEquals("—",seasonNumber(null,"PTS",false))
    }
    @Test fun recentGamesDoNotMixPlayoffsAndRegularSeason() {
        val regular=parsePlayerGames(fixture("gamelog"),2026,2)
        val playoffs=parsePlayerGames(fixture("gamelog"),2026,3)
        assertEquals(5,regular.size)
        assertTrue(playoffs.isNotEmpty())
        assertTrue(regular.map {it.id}.intersect(playoffs.map {it.id}.toSet()).isEmpty())
        assertTrue(regular.zipWithNext().all {(a,b)->a.instant!!>=b.instant!!})
        assertThrows(IllegalArgumentException::class.java) {parsePlayerGames(fixture("gamelog"),2027,2)}
    }
    @Test fun boxscoreTotalsAreProviderTotalsNotSumOfRoundedPlayerLines() {
        val table=parseDetail(fixture("summary")).players.first()
        assertEquals("132",table.totals["PTS"])
        assertEquals("41-85",table.totals["FG"])
    }
    @Test fun gamelogAcceptsExplicitOffsetsUsedByAndroidDesugaring() {
        val j=fixture("gamelog")
        j.getJSONObject("events").keys().forEach {id -> j.getJSONObject("events").getJSONObject(id).put("gameDate","2026-04-03T09:30:00.000+08:00")}
        val rows=parsePlayerGames(j,2026,2)
        assertTrue(rows.isNotEmpty())
        assertTrue(rows.all {it.instant==java.time.Instant.parse("2026-04-03T01:30:00Z")})
    }
}
