package com.scrollingo.app

data class Quiz(
    val word: String,
    val prompt: String,
    val options: List<String>,
    val correctIndex: Int
)

/** Builds a multiple-choice quiz around a chosen word. */
object QuizGenerator {

    private const val DISTRACTOR_COUNT = 3

    /**
     * Builds the quiz for [correct]. Distractors are drawn from the same
     * category first (so the wrong answers are plausibly confusable, e.g. all
     * colors), then topped up from the rest of the pack if needed.
     */
    fun generate(words: List<WordEntry>, correct: WordEntry): Quiz {
        require(words.size >= 2) { "Need at least 2 words to build a quiz" }

        val pool = words.filter { it.translation != correct.translation }

        val sameCategory = pool
            .filter { correct.category.isNotEmpty() && it.category == correct.category }
            .map { it.translation }
            .distinct()
            .shuffled()

        val fallback = pool
            .map { it.translation }
            .distinct()
            .filter { it !in sameCategory }
            .shuffled()

        val distractors = (sameCategory + fallback).take(DISTRACTOR_COUNT)
        val options = (distractors + correct.translation).shuffled()

        return Quiz(
            word = correct.word,
            prompt = "What does this mean?",
            options = options,
            correctIndex = options.indexOf(correct.translation)
        )
    }
}
