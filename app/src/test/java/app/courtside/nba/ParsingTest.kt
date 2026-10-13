package app.courtside.nba

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ParsingTest {
    private fun fixture(name: String) = JSONObject(javaClass.getResource("/$name.json")!!.readText())
    @Test fun realScoreboardParsesAndDeduplicatesAcrossProviderDates() {
        val events = parseEvents(fixture("scoreboard"))
        assertTrue(events.isNotEmpty())
        val g = events.first()
        val zone = ZoneId.of("Asia/Hong_Kong")
        val day = g.instant!!.atZone(zone).toLocalDate()
        assertEquals(1, gamesForDay(listOf(g, g), day, zone).size)
        assertTrue(g.home.id != g.away.id)
    }
    @Test fun localDateUsesInstantInsteadOfAmericanDate() {
        val g = parseEvents(fixture("scoreboard")).first().copy(instant = Instant.parse("2026-10-11T23:30:00Z"))
        assertEquals(1, gamesForDay(listOf(g), LocalDate.parse("2026-10-12"), ZoneId.of("Asia/Hong_Kong")).size)
        assertEquals(0, gamesForDay(listOf(g), LocalDate.parse("2026-10-11"), ZoneId.of("Asia/Hong_Kong")).size)
        assertEquals(1, gamesForDay(listOf(g), LocalDate.parse("2026-10-11"), ZoneId.of("America/New_York")).size)
    }
    @Test fun scheduleObjectScoresParseWithoutJsonLeakingIntoUi() {
        val g = parseEvents(fixture("schedule")).first()
        assertTrue(g.homeScore.toIntOrNull() != null)
        assertTrue(g.awayScore.toIntOrNull() != null)
        assertEquals("季前赛", g.kind)
    }
    @Test fun summaryHasPlayersAndActualTeamStatistics() {
        val detail = parseDetail(fixture("summary"))
        assertEquals("401898400", detail.game.id)
        assertEquals(2, detail.players.size)
        assertTrue(detail.players.first().players.first().cells.containsKey("PTS"))
        assertTrue(detail.stats.first().home != "—")
    }
    @Test fun regularSeasonDoesNotInventRanksBeforeSeasonStarts() {
        val table = parseStandings(fixture("standings"))
        assertEquals(2027, table.season)
        assertEquals(30, table.rows.size)
        assertTrue(table.rows.all { it.rank == 0 && it.wins == 0 && it.losses == 0 })
    }
    @Test fun postponementDoesNotBecomeGenericPregame() {
        val g = parseEvents(fixture("scoreboard")).first().copy(state = "pre", statusName = "STATUS_POSTPONED")
        assertEquals("已延期", g.status)
    }
}
