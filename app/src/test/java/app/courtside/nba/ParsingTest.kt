package app.courtside.nba

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ParsingTest {
    @Test fun homeCarouselShowsFollowedUpcomingThenRecentWithoutDuplicates() {
        val base = parseEvents(fixture("scoreboard")).first()
        val now = Instant.parse("2026-10-12T08:00:00Z")
        val past = base.copy(id="past", state="post", instant=now.minusSeconds(86400))
        val next = base.copy(id="next", state="pre", statusName="STATUS_SCHEDULED", instant=now.plusSeconds(86400))
        val postponed = next.copy(id="postponed", statusName="STATUS_POSTPONED", instant=now.plusSeconds(100))
        val other = next.copy(id="other", home=next.home.copy(id="foreign-home"), away=next.away.copy(id="foreign-away"))
        assertEquals(listOf("next", "past"), homeGames(listOf(past), listOf(other,postponed,next,past,next), base.home.id, now).map {it.id})
        assertTrue(homeGames(listOf(past), listOf(next), "", now).isEmpty())
        val live = base.copy(id="live", state="in", instant=now.minusSeconds(100))
        assertEquals("live", homeGames(listOf(live),listOf(next,past),base.home.id,now).first().id)
    }
    @Test fun winnerHighlightOnlyUsesNumericFinalScores() {
        val base = parseEvents(fixture("scoreboard")).first().copy(state="post", homeScore="119", awayScore="105")
        assertTrue(isWinner(base,base.home.id))
        assertFalse(isWinner(base,base.away.id))
        assertFalse(isWinner(base.copy(state="in"),base.home.id))
        assertFalse(isWinner(base.copy(awayScore="—"),base.home.id))
        assertFalse(isWinner(base.copy(awayScore="119"),base.home.id))
    }
    private fun names() = PlayerNames(org.json.JSONArray(java.io.File("src/main/assets/players_zh.json").readText().removePrefix("\uFEFF")))
    @Test fun playerTranslationWorksOnExistingEnglishSummaryCache() {
        val detail = parseDetail(fixture("summary"), names())
        assertEquals("丹尼尔·加福德", detail.players.first().players.first().name)
        assertEquals(parseDetail(fixture("summary")).players.first().players.first().cells, detail.players.first().players.first().cells)
    }
    @Test fun stableIdsAvoidGuessingUnknownPlayersWithSimilarNames() {
        assertEquals("勒布朗·詹姆斯", names().translate("1966", "LeBron James"))
        assertEquals("LeBron James", names().translate("unknown-id", "LeBron James"))
        assertEquals("Future Player", names().translate("", "Future Player"))
        assertEquals("卢卡·东契奇", names().translate("", "Luka Dončić"))
    }
    @Test fun allBundledTeamsHaveLocalDecodableLogosWithoutRemoteUrl() {
        val teams = org.json.JSONArray(java.io.File("src/main/assets/teams.json").readText().removePrefix("\uFEFF"))
        assertEquals(30, teams.length())
        teams.objects().forEach {
            val team = parseTeam(it).copy(logo = "")
            assertNotNull(teamLogoResource(team.id))
            val image = javax.imageio.ImageIO.read(java.io.File("src/main/res/drawable-nodpi/team_${team.id}.png"))
            assertTrue(image.width > 0 && image.height > 0)
        }
    }
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
