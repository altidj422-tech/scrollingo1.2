package com.scrollingo.app

import android.content.Context
import org.json.JSONObject
import java.io.File

data class WordEntry(val word: String, val translation: String, val category: String = "")

/**
 * Loads bundled word lists from assets/words_<code>.json. The known/base
 * language is English; [WordEntry.translation] is the English meaning.
 *
 * Words are offline-first: every language ships inside the APK so a quiz can
 * always be built with no network. If the WiFi sync ([WordPackSync]) has
 * downloaded a newer, larger pack into internal storage, that is used instead.
 */
object Dictionary {

    data class Lang(val code: String, val display: String, val asset: String)

    private val langs = listOf(
        Lang("es", "Spanish", "words_es.json"),
        Lang("fr", "French", "words_fr.json"),
        Lang("de", "German", "words_de.json"),
        Lang("it", "Italian", "words_it.json"),
        Lang("pt", "Portuguese", "words_pt.json")
    )

    val languages: List<String> = langs.map { it.display }

    val DEFAULT_LANGUAGE: String = languages.first()

    val codes: List<String> = langs.map { it.code }

    private val cache = HashMap<String, List<WordEntry>>()

    private fun byDisplay(display: String): Lang =
        langs.firstOrNull { it.display == display } ?: langs.first()

    fun displayForCode(code: String): String? =
        langs.firstOrNull { it.code == code }?.display

    /** Where a downloaded pack for [code] lives, once the sync has fetched it. */
    fun localPackFile(context: Context, code: String): File =
        File(File(context.filesDir, "wordpacks").apply { mkdirs() }, "words_$code.json")

    @Synchronized
    fun load(context: Context, language: String): List<WordEntry> {
        cache[language]?.let { return it }

        val lang = byDisplay(language)
        val raw = readDownloaded(context, lang.code) ?: readBundled(context, lang.asset)
        val list = parseWords(raw)
        cache[language] = list
        return list
    }

    /** Drop cached parses so the next [load] picks up freshly downloaded packs. */
    @Synchronized
    fun clearCache() = cache.clear()

    /** Parse + sanity-check a pack's JSON; returns null if it isn't usable. */
    fun parseOrNull(raw: String): List<WordEntry>? =
        runCatching { parseWords(raw) }.getOrNull()?.takeIf { it.size >= 2 }

    private fun readDownloaded(context: Context, code: String): String? {
        val file = localPackFile(context, code)
        if (!file.exists()) return null
        return runCatching { file.readText(Charsets.UTF_8) }
            .getOrNull()
            ?.takeIf { parseOrNull(it) != null }
    }

    private fun readBundled(context: Context, asset: String): String =
        context.assets.open(asset).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun parseWords(raw: String): List<WordEntry> {
        val array = JSONObject(raw).getJSONArray("words")
        val list = ArrayList<WordEntry>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                WordEntry(
                    obj.getString("word"),
                    obj.getString("translation"),
                    obj.optString("category", "")
                )
            )
        }
        return list
    }
}
