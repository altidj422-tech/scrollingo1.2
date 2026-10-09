package com.scrollingo.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the guarantees the quiz overlay relies on: the correct answer is
 * always present, [Quiz.correctIndex] always points at it, options never
 * repeat, and distractors prefer the same category so the wrong answers stay
 * plausible.
 */
class QuizGeneratorTest {

    private val colors = listOf(
        WordEntry("rojo", "red", "colors"),
        WordEntry("azul", "blue", "colors"),
        WordEntry("verde", "green", "colors"),
        WordEntry("negro", "black", "colors"),
        WordEntry("blanco", "white", "colors")
    )

    private val animals = listOf(
        WordEntry("perro", "dog", "animals"),
        WordEntry("gato", "cat", "animals"),
        WordEntry("caballo", "horse", "animals")
    )

    @Test
    fun `correctIndex points at the correct translation`() {
        val correct = colors[0]
        repeat(50) {
            val quiz = QuizGenerator.generate(colors, correct)
            assertEquals(correct.translation, quiz.options[quiz.correctIndex])
        }
    }

    @Test
    fun `quiz exposes the prompted word, not its translation`() {
        val quiz = QuizGenerator.generate(colors, colors[1])
        assertEquals("azul", quiz.word)
        assertTrue(quiz.prompt.isNotBlank())
    }

    @Test
    fun `builds four distinct options when the pack is large enough`() {
        val quiz = QuizGenerator.generate(colors, colors[2])
        assertEquals(4, quiz.options.size)
        assertEquals(4, quiz.options.distinct().size)
    }

    @Test
    fun `distractors are drawn from the same category first`() {
        val pack = colors + animals
        repeat(50) {
            val quiz = QuizGenerator.generate(pack, pack[0]) // "rojo" / colors
            val colorTranslations = colors.map { it.translation }.toSet()
            assertTrue(
                "expected all options to be colors, got ${quiz.options}",
                quiz.options.all { it in colorTranslations }
            )
        }
    }

    @Test
    fun `tops distractors up from other categories when the category is small`() {
        val pack = animals + colors
        val quiz = QuizGenerator.generate(pack, animals[0]) // only 2 other animals
        assertEquals(4, quiz.options.size)
        assertEquals(4, quiz.options.distinct().size)
        assertTrue(quiz.options.contains("dog"))
    }

    @Test
    fun `degrades gracefully on a two-word pack`() {
        val tiny = listOf(
            WordEntry("sí", "yes", "basics"),
            WordEntry("no", "no", "basics")
        )
        val quiz = QuizGenerator.generate(tiny, tiny[0])
        assertEquals(2, quiz.options.size)
        assertEquals("yes", quiz.options[quiz.correctIndex])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a pack too small to build a question`() {
        QuizGenerator.generate(listOf(WordEntry("sí", "yes")), WordEntry("sí", "yes"))
    }

    @Test
    fun `never offers a duplicate of the correct translation as a distractor`() {
        // Two entries can share an English meaning (synonyms in the target
        // language); the options list must still contain it exactly once.
        val withSynonym = colors + WordEntry("colorado", "red", "colors")
        repeat(50) {
            val quiz = QuizGenerator.generate(withSynonym, colors[0])
            assertEquals(1, quiz.options.count { it == "red" })
        }
    }

    @Test
    fun `shuffles the answer position across repeated draws`() {
        val positions = (1..100).map { QuizGenerator.generate(colors, colors[0]).correctIndex }
        assertNotEquals(
            "the correct answer should not always sit in the same slot",
            1,
            positions.distinct().size
        )
    }
}
