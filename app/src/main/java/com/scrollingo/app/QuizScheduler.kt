package com.scrollingo.app

import android.content.Context
import kotlin.random.Random

/**
 * Decides which word to quiz next and remembers how the learner did, giving the
 * app light spaced-repetition behavior:
 *  - brand-new words are introduced steadily,
 *  - words answered wrong come back more often,
 *  - well-known words fade into the background,
 *  - the word just shown is never shown twice in a row.
 *
 * All state is local (SharedPreferences) and per language; nothing leaves the
 * device.
 */
object QuizScheduler {

    private const val PREFS = "quiz_stats"

    /** A word shown within this many turns is downweighted so it doesn't repeat. */
    private const val COOLDOWN_TURNS = 5
    private const val RECENT_PENALTY = 0.25
    private const val NEW_WORD_WEIGHT = 5.0

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Pick the next word to quiz, biased toward new and not-yet-mastered words. */
    fun nextWord(context: Context, language: String, words: List<WordEntry>): WordEntry {
        require(words.isNotEmpty()) { "Need at least 1 word" }
        val p = prefs(context)
        val turn = p.getInt("turn:$language", 0) + 1

        val last = p.getString("last:$language", null)
        val candidates = if (words.size > 1) words.filter { it.word != last } else words

        val weights = DoubleArray(candidates.size)
        var total = 0.0
        for (i in candidates.indices) {
            val w = candidates[i]
            val seen = p.getInt("seen:$language:${w.word}", 0)
            var weight: Double
            if (seen == 0) {
                weight = NEW_WORD_WEIGHT
            } else {
                val correct = p.getInt("correct:$language:${w.word}", 0)
                val accuracy = correct.toDouble() / seen
                weight = 1.0 + (1.0 - accuracy) * 4.0 // 1.0 (mastered) .. 5.0 (always wrong)
                val shownTurn = p.getInt("shown:$language:${w.word}", 0)
                if (turn - shownTurn in 1 until COOLDOWN_TURNS) weight *= RECENT_PENALTY
            }
            weights[i] = weight
            total += weight
        }

        val chosen = pickWeighted(candidates, weights, total)

        p.edit()
            .putInt("turn:$language", turn)
            .putString("last:$language", chosen.word)
            .putInt("shown:$language:${chosen.word}", turn)
            .apply()
        return chosen
    }

    /** Record the outcome of the first answer for a quizzed word. */
    fun record(context: Context, language: String, word: String, correct: Boolean) {
        val p = prefs(context)
        val seen = p.getInt("seen:$language:$word", 0) + 1
        val ok = p.getInt("correct:$language:$word", 0) + if (correct) 1 else 0
        p.edit()
            .putInt("seen:$language:$word", seen)
            .putInt("correct:$language:$word", ok)
            .apply()
    }

    private fun pickWeighted(
        candidates: List<WordEntry>,
        weights: DoubleArray,
        total: Double
    ): WordEntry {
        if (total <= 0.0) return candidates.random()
        var r = Random.nextDouble(total)
        for (i in candidates.indices) {
            r -= weights[i]
            if (r < 0.0) return candidates[i]
        }
        return candidates.last()
    }
}
