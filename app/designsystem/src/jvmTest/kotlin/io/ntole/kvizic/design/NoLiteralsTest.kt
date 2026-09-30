package io.ntole.kvizic.design

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The single-source rule, read from the sources: no component, no part of the skin engine and no screen
 * writes a colour, a length or a type size of its own. Those live in a skin's own package, its tokens
 * and its parts, so a new skin changes them in one place and every screen follows. A zero is no design
 * decision and may be written.
 */
class NoLiteralsTest {
    @Test
    fun `components, the engine and screens write no colour, length or type size`() {
        val sources =
            listOf(File(COMPONENTS), File(ENGINE), File(SCREENS))
                .flatMap { it.walkTopDown().filter { file -> file.isFile && file.extension == "kt" }.toList() }
        assertTrue(sources.size > MANY_SOURCES, "found only ${sources.size} sources to read")
        val found =
            sources.flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    val code = line.substringBefore("//")
                    val length = LENGTH.findAll(code).firstOrNull { it.groupValues[1].toDouble() != 0.0 }?.value
                    val colour = COLOUR.find(code)?.value
                    (length ?: colour)?.let { "${file.path}:${i + 1}: $it" }
                }
            }
        assertTrue(found.isEmpty(), "literals outside a skin's own files:\n" + found.joinToString("\n"))
    }

    private companion object {
        const val COMPONENTS = "src/commonMain/kotlin/io/ntole/kvizic/design/component"
        const val ENGINE = "src/commonMain/kotlin/io/ntole/kvizic/design/skin"
        const val SCREENS = "src/jvmTest/kotlin/io/ntole/kvizic/design/Mockups.kt"
        const val MANY_SOURCES = 20

        /** A length or a type size written as a number: 12.dp, 1.5.sp, 0.04.em. */
        val LENGTH = Regex("""\b(\d+(?:\.\d+)?)\.(?:dp|sp|em)\b""")

        /** A colour written out. */
        val COLOUR = Regex("""Color\(0x[0-9A-Fa-f]+""")
    }
}
