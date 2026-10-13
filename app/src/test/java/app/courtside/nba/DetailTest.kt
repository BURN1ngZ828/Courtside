package app.courtside.nba

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DetailTest {
    private fun summary()=JSONObject(javaClass.getResource("/summary.json")!!.readText())
    @Test fun venueFallsBackToGameInfoAndEnglishAndIdentityAreRetained() {
        val json=summary()
        json.put("gameInfo",JSONObject().put("venue",JSONObject().put("fullName","Madison Square Garden")))
        json.getJSONObject("header").getJSONArray("competitions").getJSONObject(0).remove("venue")
        val detail=parseDetail(json)
        assertEquals("Madison Square Garden",detail.game.venue)
        assertEquals("Daniel Gafford",detail.players.first().players.first().english)
        assertEquals("4278049",detail.players.first().players.first().id)
    }
    @Test fun onlyLiveActivePlayersAreOnCourtAndStarterIsSeparate() {
        val json=summary()
        val status=json.getJSONObject("header").getJSONArray("competitions").getJSONObject(0).getJSONObject("status").getJSONObject("type")
        status.put("state","in")
        val athletes=json.getJSONObject("boxscore").getJSONArray("players").getJSONObject(0).getJSONArray("statistics").getJSONObject(0).getJSONArray("athletes")
        athletes.getJSONObject(0).put("active",true).put("starter",false)
        athletes.getJSONObject(1).put("active",false).put("starter",true)
        val live=parseDetail(json).players.first().players
        assertTrue(live[0].onCourt);assertFalse(live[0].starter)
        assertFalse(live[1].onCourt);assertTrue(live[1].starter)
        status.put("state","post")
        assertFalse(parseDetail(json).players.first().players[0].onCourt)
    }
    @Test fun regulationOvertimeAndInvalidClocksAreHandled() {
        assertEquals(0,elapsedSeconds(1,"12:00"))
        assertEquals(2880,elapsedSeconds(4,"0:00"))
        assertEquals(2880,elapsedSeconds(5,"5:00"))
        assertEquals(3480,elapsedSeconds(6,"0:00"))
        assertNull(elapsedSeconds(5,"12:00"));assertNull(elapsedSeconds(1,"11:61"));assertNull(elapsedSeconds(0,"1:00"))
    }
    @Test fun pointsAreSortedByTimeAndSequenceWithoutInventingOrigin() {
        fun play(sequence:Int,clock:String,home:Int)=JSONObject().put("sequenceNumber",sequence).put("period",JSONObject().put("number",1)).put("clock",JSONObject().put("displayValue",clock)).put("awayScore",0).put("homeScore",home)
        val points=parseScorePoints(JSONArray().put(play(3,"11:00",4)).put(play(1,"11:30",2)).put(play(2,"11:00",2)))
        assertEquals(listOf(30,60,60),points.map {it.elapsed})
        assertEquals(listOf(2,2,4),points.map {it.home})
        assertEquals(3,points.size)
        val json=summary().put("plays",JSONArray().put(play(3,"11:00",4)))
        assertFalse(parseDetail(json).curveComplete)
    }
    @Test fun signedMaximumTiesMissingAndDnpAreHandled() {
        val lines=listOf(PlayerLine("A",false,mapOf("+/-" to "-2")),PlayerLine("B",false,mapOf("+/-" to "-9")),PlayerLine("C",false,mapOf("+/-" to "-2")),PlayerLine("D",true,mapOf("+/-" to "20")))
        val maxima=statMaxima(lines,"+/-")
        assertEquals(listOf(-2.0),maxima)
        assertEquals(listOf(true),statHighlights(lines[0],"+/-",maxima))
        assertEquals(listOf(true),statHighlights(lines[2],"+/-",maxima))
        assertEquals(listOf(false),statHighlights(lines[3],"+/-",maxima))
        assertEquals("+8",signedStat("8"));assertEquals("-12",signedStat("−12"));assertEquals("—",signedStat("—"))
    }
    @Test fun shootingValuesAreIndependentAndAllZeroColumnsStayQuiet() {
        val lines=listOf(PlayerLine("A",false,mapOf("FG" to "4-6","PTS" to "0","MIN" to "9:49")),PlayerLine("B",false,mapOf("FG" to "3-8","PTS" to "0","MIN" to "10")))
        val max=statMaxima(lines,"FG")
        assertEquals(listOf(4.0,8.0),max)
        assertEquals(listOf(true,false),statHighlights(lines[0],"FG",max))
        assertEquals(listOf(false,true),statHighlights(lines[1],"FG",max))
        assertEquals(listOf<Double?>(null),statMaxima(lines,"PTS"))
        assertEquals(listOf(600.0),statMaxima(lines,"MIN"))
    }
    @Test fun legacyTeamMigratesAndExplicitEmptyListDoesNotResurrectIt() {
        assertEquals(listOf("2"),normalizePreferences(null,"2","light").teams)
        assertEquals("2",normalizePreferences(null,"2","light").team)
        assertEquals("",normalizePreferences(emptyList(),"2","light").team)
        assertEquals("13",normalizePreferences(listOf("13","9","13","invalid"),"2","unknown").team)
        assertEquals(listOf("13","9"),normalizePreferences(listOf("13","9","13","invalid"),"2","unknown").teams)
        assertEquals("dark",normalizePreferences(emptyList(),"","unknown").theme)
    }
    @Test fun matchNamesUseHupuLabelsWithoutReplacingChineseWithEnglishPlaceholders() {
        val json=JSONArray(java.io.File("src/main/assets/players_zh.json").readText())
        val names=PlayerNames(json)
        assertEquals("爱德华兹",names.matchName("4594268","Anthony Edwards"))
        assertEquals("麦克丹尼尔斯",names.matchName("4431671","Jaden McDaniels"))
        assertEquals("叶戈尔·杰明",names.matchName("5175643","Egor Demin"))
        assertEquals("Unknown Player",names.matchName("unknown","Unknown Player"))
        assertTrue(json.objects().filter {it.text("matchName").isNotBlank()}.all {it.text("matchName").any {ch -> ch in '\u4E00'..'\u9FFF'}})
    }
}
