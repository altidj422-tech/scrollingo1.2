package com.scrollingo.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [Dictionary.parseOrNull] is the gate that decides whether a word pack
 * downloaded over WiFi is allowed to replace the bundled one. If it ever
 * accepted a truncated or malformed pack, the app would ship a broken quiz to
 * a device that was previously fine — so the rejection cases matter more than
 * the happy path.
 */
class DictionaryParseTest {

    private fun pack(vararg entries: String) =
        """{"words":[${entries.joinToString(",")}]}"""

    private val es = pack(
        """{"word":"rojo","translation":"red","category":"colors"}""",
        """{"word":"azul","translation":"blue","category":"colors"}""",
        """{"word":"perro","translation":"dog","category":"animals"}"""
    )

    @Test
    fun `parses a well-formed pack in order`() {
        val words = Dictionary.parseOrNull(es)
        assertNotNull(words)
        assertEquals(3, words!!.size)
        assertEquals(WordEntry("rojo", "red", "colors"), words[0])
        assertEquals("perro", words[2].word)
    }

    @Test
    fun `category is optional and defaults to empty`() {
        val words = Dictionary.parseOrNull(
            pack(
                """{"word":"sí","translation":"yes"}""",
                """{"word":"no","translation":"no"}"""
            )
        )
        assertNotNull(words)
        assertEquals("", words!![0].category)
    }

    @Test
    fun `keeps non-ascii words intact`() {
        val words = Dictionary.parseOrNull(
            pack(
                """{"word":"über","translation":"over"}""",
                """{"word":"groß","translation":"big"}"""
            )
        )
        assertEquals("über", words!![0].word)
        assertEquals("groß", words[1].word)
    }

    @Test
    fun `rejects a pack too small to build a quiz`() {
        assertNull(Dictionary.parseOrNull(pack("""{"word":"sí","translation":"yes"}""")))
        assertNull(Dictionary.parseOrNull("""{"words":[]}"""))
    }

    @Test
    fun `rejects malformed json`() {
        assertNull(Dictionary.parseOrNull(""))
        assertNull(Dictionary.parseOrNull("not json"))
        assertNull(Dictionary.parseOrNull("""{"words":[{"word":"rojo",""")) // truncated
    }

    @Test
    fun `rejects a pack missing the words array`() {
        assertNull(Dictionary.parseOrNull("""{"version":3}"""))
        assertNull(Dictionary.parseOrNull("""{"words":{"word":"rojo"}}"""))
    }

    @Test
    fun `rejects an entry missing a required field`() {
        assertNull(
            Dictionary.parseOrNull(
                pack(
                    """{"word":"rojo","translation":"red"}""",
                    """{"word":"azul"}""" // no translation
                )
            )
        )
    }

    @Test
    fun `language metadata is self-consistent`() {
        assertEquals(Dictionary.codes.size, Dictionary.languages.size)
        assertEquals(Dictionary.languages.toSet().size, Dictionary.languages.size)
        assertEquals("Spanish", Dictionary.DEFAULT_LANGUAGE)
        Dictionary.codes.forEach { assertNotNull(Dictionary.displayForCode(it)) }
        assertNull(Dictionary.displayForCode("zz"))
    }
}
