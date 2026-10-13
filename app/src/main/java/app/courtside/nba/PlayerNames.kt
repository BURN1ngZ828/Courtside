package app.courtside.nba

import org.json.JSONArray
import java.text.Normalizer
import java.util.Locale

/** Stable provider IDs prevent similarly named players from sharing a translation. */
class PlayerNames(json: JSONArray = JSONArray()) {
    private val byId = json.objects().associate { it.getString("id") to it.getString("chinese") }
    private val byName = json.objects().groupBy { key(it.getString("english")) }
        .filterValues { rows -> rows.map { it.getString("chinese") }.distinct().size == 1 }
        .mapValues { (_, rows) -> rows.first().getString("chinese") }

    fun translate(id: String, english: String): String =
        byId[id] ?: if (id.isBlank()) byName[key(english)] ?: english else english

    private fun key(name: String) = Normalizer.normalize(name, Normalizer.Form.NFD)
        .lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
}
