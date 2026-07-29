package com.example.multiplicationtrainer.data

import android.content.Context
import com.example.multiplicationtrainer.model.SpellingWord
import org.json.JSONObject

class SpellingContentRepository(private val context: Context) {
    private var cache: List<SpellingWord>? = null

    fun loadWords(): List<SpellingWord> {
        cache?.let { return it }
        val raw = context.assets.open("spelling/catalog.json").bufferedReader().use { it.readText() }
        val json = JSONObject(raw)
        val array = json.getJSONArray("words")
        val words = buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    SpellingWord(
                        id = item.getString("id"),
                        word = item.getString("word"),
                        image = item.getString("image"),
                        grade = item.optInt("grade", 1),
                    )
                )
            }
        }
        cache = words
        return words
    }

    fun generateSession(count: Int): List<SpellingWord> {
        val pool = loadWords()
        require(count <= pool.size) { "Not enough words in catalog" }
        return pool.shuffled().take(count)
    }
}
