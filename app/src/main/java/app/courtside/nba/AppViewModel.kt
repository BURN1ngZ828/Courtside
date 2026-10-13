package app.courtside.nba

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

private val Context.settings by preferencesDataStore("settings")
data class Preferences(val team: String = "", val theme: String = "dark", val spoiler: Boolean = false)
class AppViewModel(app: Application): AndroidViewModel(app) {
    private val store = app.settings
    private val teamKey = stringPreferencesKey("team")
    private val themeKey = stringPreferencesKey("theme")
    private val spoilerKey = booleanPreferencesKey("spoiler")
    val preferences = store.data.map { Preferences(it[teamKey] ?: "", it[themeKey] ?: "dark", it[spoilerKey] ?: false) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Preferences())
    private val repo = NbaRepository(app)
    var teams by mutableStateOf(Loaded(data = repo.bundledTeams)); private set
    var today by mutableStateOf(Loaded<List<Game>>()); private set
    var games by mutableStateOf(Loaded<List<Game>>()); private set
    var schedule by mutableStateOf(Loaded<List<Game>>()); private set
    var table by mutableStateOf(Loaded<Table>()); private set
    var detail by mutableStateOf(Loaded<Detail>()); private set
    var date by mutableStateOf(LocalDate.now()); private set
    var currentDate by mutableStateOf(LocalDate.now()); private set
    var season by mutableStateOf(seasonFor(LocalDate.now())); private set
    var detailId by mutableStateOf(""); private set
    private var dayJob: Job? = null
    private var scheduleJob: Job? = null
    private var detailJob: Job? = null
    private var loadedTeam = ""
    private var zone = ZoneId.systemDefault()
    init {
        viewModelScope.launch { teams = teams.copy(loading = true); teams = repo.teams() }
        viewModelScope.launch { preferences.collect { if(it.team != loadedTeam) { loadedTeam = it.team; loadSchedule() } } }
        refreshHome()
        loadDate(date)
        loadTable()
    }
    fun setTeam(id: String) { viewModelScope.launch { store.edit { it[teamKey] = id } } }
    fun setTheme(value: String) { viewModelScope.launch { store.edit { it[themeKey] = value } } }
    fun setSpoiler(value: Boolean) { viewModelScope.launch { store.edit { it[spoilerKey] = value } } }
    fun foreground() {
        val newDay = LocalDate.now()
        val newZone = ZoneId.systemDefault()
        if(newDay != currentDate || newZone != zone) {
            if(date == currentDate) loadDate(newDay) else if(newZone != zone) loadDate(date)
            currentDate = newDay; zone = newZone; season = seasonFor(newDay)
            loadSchedule(); loadTable()
        }
        refreshHome()
    }
    fun refreshHome(force: Boolean = false, extras: Boolean = true) {
        if(today.loading) return
        viewModelScope.launch { today = today.copy(loading = true); today = repo.day(currentDate, ZoneId.systemDefault(), force) }
        if(force && extras) { loadSchedule(true); loadTable(true); viewModelScope.launch { teams = repo.teams(true) } }
    }
    fun loadDate(value: LocalDate, force: Boolean = false) {
        dayJob?.cancel()
        date = value
        games = if(force) games.copy(loading = true) else Loaded(loading = true)
        dayJob = viewModelScope.launch { games = repo.day(value, ZoneId.systemDefault(), force) }
    }
    fun loadSchedule(force: Boolean = false) {
        scheduleJob?.cancel()
        if(loadedTeam.isBlank()) { schedule = Loaded(); return }
        schedule = Loaded(loading = true)
        val id = loadedTeam
        scheduleJob = viewModelScope.launch { schedule = repo.schedule(id, season, force) }
    }
    fun loadTable(force: Boolean = false) {
        if(table.loading) return
        viewModelScope.launch { table = table.copy(loading = true); table = repo.standings(season, force) }
    }
    fun openGame(id: String, force: Boolean = false) {
        detailJob?.cancel(); detailId = id
        detail = if(force) detail.copy(loading = true) else Loaded(loading = true)
        detailJob = viewModelScope.launch { detail = repo.detail(id, force) }
    }
    fun closeGame() { detailJob?.cancel(); detailId = ""; detail = Loaded() }
    fun poll(page: Int) {
        if(LocalDate.now() != currentDate || ZoneId.systemDefault() != zone) foreground()
        fun due(g: Game) = g.live || (g.state == "pre" && g.instant?.let { it <= java.time.Instant.now() && it > java.time.Instant.now().minusSeconds(21600) } == true)
        if(detailId.isNotEmpty()) {
            if(detail.error == null && detail.data?.game?.let(::due) == true) openGame(detailId, true)
        } else when(page) {
            0 -> if(today.error == null && today.data.orEmpty().any(::due)) refreshHome(true, false)
            1 -> if(games.error == null && games.data.orEmpty().any(::due)) loadDate(date, true)
        }
    }
    companion object { fun seasonFor(day: LocalDate) = if(day.monthValue >= 9) day.year + 1 else day.year }
}
