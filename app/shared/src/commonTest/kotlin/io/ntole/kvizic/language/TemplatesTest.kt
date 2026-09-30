package io.ntole.kvizic.language

import kotlin.test.Test
import kotlin.test.assertEquals

/** How a translated text holds numbers and names. */
class TemplatesTest {
    @Test
    fun `a template takes its values where its placeholders are`() {
        assertEquals("Игра 3 од 20: Ана", "Игра {0} од {1}: {2}".fill(3, 20, "Ана"))
        assertEquals("20 then 3", "{1} then {0}".fill(3, 20))
    }

    @Test
    fun `a value is put in as it is`() {
        assertEquals("Kicked: see {1} and {0}", "Kicked: {0}".fill("see {1} and {0}", "never"))
    }

    @Test
    fun `a placeholder with no value is left as it is`() {
        assertEquals("Wait {0} s.", "Wait {0} s.".fill())
    }

    /** Cut where its values go, so a screen can put a link where one is. */
    @Test
    fun `a template cuts into its text and the places of its values in order`() {
        assertEquals(
            listOf(
                TemplatePart.Text("Играјући прихваташ "),
                TemplatePart.Value(0),
                TemplatePart.Text(" и "),
                TemplatePart.Value(1),
                TemplatePart.Text("."),
            ),
            "Играјући прихваташ {0} и {1}.".parts(),
        )
        assertEquals(listOf(TemplatePart.Value(1), TemplatePart.Value(0)), "{1}{0}".parts())
        assertEquals(listOf(TemplatePart.Text("no values")), "no values".parts())
    }
}
