package io.ntole.kvizic.design

import io.ntole.kvizic.design.font.Face
import io.ntole.kvizic.design.font.FaceFile
import io.ntole.kvizic.design.font.Faces
import org.jetbrains.skia.Data
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontHinting
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Typeface
import org.jetbrains.skia.paragraph.FontCollection
import org.jetbrains.skia.paragraph.ParagraphBuilder
import org.jetbrains.skia.paragraph.ParagraphStyle
import org.jetbrains.skia.paragraph.TextStyle
import org.jetbrains.skia.paragraph.TypefaceFontProvider
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The bundled faces, read from their files as the app ships them: every weight draws the whole Serbian
 * alphabet in both scripts, the digits and the punctuation the game sets, with no glyph missing; the
 * faces that claim the Serbian forms of б draw them for a Serbian locale, and the others do not; and
 * each face's figures stand as tall as its [Face.figureHeight] says, which centres a flap's digit.
 * With `KVIZIC_DESIGN_DIR` set it also writes a glyph sheet of each face there, shaped as Android shapes
 * it, for the Serbian locale.
 */
class FontCoverageTest {
    @Test
    fun `every weight of every face draws both Serbian alphabets`() {
        val missing =
            Faces.ALL.flatMap { face ->
                face.files.flatMap { file ->
                    val typeface = typefaceOf(file)
                    COVERED
                        .filter {
                            typeface
                                .getUTF32Glyph(
                                    it.code,
                                ).toInt() == 0
                        }.map { "${file.fileName} lacks '$it'" }
                }
            }
        assertTrue(missing.isEmpty(), missing.joinToString("\n"))
    }

    @Test
    fun `the faces that say so draw the Serbian forms`() {
        Faces.ALL.forEach { face ->
            face.files.forEach { file ->
                val typeface = typefaceOf(file)
                val serbian = shapedPixels(typeface, SERBIAN_FORMS, "sr")
                val russian = shapedPixels(typeface, SERBIAN_FORMS, "ru")
                assertEquals(face.hasSerbianForms, !serbian.contentEquals(russian), "${file.fileName}'s Serbian forms")
            }
        }
    }

    @Test
    fun `each face's figures stand as tall as it says`() {
        val off =
            Faces.ALL.flatMap { face ->
                face.files.mapNotNull { file ->
                    // A figure with no overshoot and no descent, as tall as the lining figures stand, from
                    // its outline as drawn: unhinted, since Linux's FreeType follows the faces' hinting
                    // instructions, which move heights by a few pixels, and macOS's CoreText ignores them.
                    val font =
                        Font(typefaceOf(file), MEASURE_SIZE).apply {
                            hinting = FontHinting.NONE
                            isSubpixel = true
                            isLinearMetrics = true
                        }
                    val bounds = font.measureText(FLAT_FIGURE)
                    val height = (bounds.bottom - bounds.top) / MEASURE_SIZE
                    if (abs(height - face.figureHeight) <= FIGURE_TOLERANCE) {
                        null
                    } else {
                        "${file.fileName}'s figures stand $height em, not ${face.figureHeight}"
                    }
                }
            }
        assertTrue(off.isEmpty(), off.joinToString("\n"))
    }

    @Test
    fun `a glyph sheet of each face`() {
        Faces.ALL.forEach { face ->
            val image = glyphSheet(face)
            assertTrue(image.width == SHEET_WIDTH && image.height > 0, face.id)
            writeDesign("glyphs-${face.id}", image)
        }
    }

    /** Every weight of [face], each alphabet in a row of its own, and its б for Serbian beside it for Russian. */
    private fun glyphSheet(face: Face): Image {
        val rows =
            buildList {
                add(
                    Row(
                        "${face.name} · Serbian forms: ${if (face.hasSerbianForms) "yes" else "no"}",
                        LABEL,
                        "en",
                        face.files.first(),
                        label = true,
                    ),
                )
                face.files.forEach { file ->
                    add(Row(file.fileName, LABEL, "en", file, label = true))
                    add(Row(CYRILLIC_UPPER, SIZE, "sr", file))
                    add(Row(CYRILLIC_LOWER, SIZE, "sr", file))
                    add(Row(LATIN_UPPER, SIZE, "sr-Latn", file))
                    add(Row(LATIN_LOWER, SIZE, "sr-Latn", file))
                    add(Row(FIGURES, SIZE, "sr", file))
                    add(Row(SAMPLE, SIZE, "sr", file))
                    add(Row("Serbian $SERBIAN_FORMS   ·   Russian", SIZE, "sr", file, russianTail = SERBIAN_FORMS))
                }
            }
        val height = rows.sumOf { (it.size * LINE).toInt() } + MARGIN * 2
        val surface = Surface.makeRasterN32Premul(SHEET_WIDTH, height)
        surface.canvas.clear(PAPER)
        var y = MARGIN.toFloat()
        rows.forEach { row ->
            val typeface = typefaceOf(row.file)
            paragraph(
                typeface,
                row.text,
                row.size,
                row.locale,
                if (row.label) MUTED else INK,
            ).paint(surface.canvas, MARGIN.toFloat(), y)
            val tail = row.russianTail
            if (tail != null) {
                val lead = paragraph(typeface, row.text, row.size, row.locale, INK).maxIntrinsicWidth
                paragraph(typeface, " $tail", row.size, "ru", INK).paint(surface.canvas, MARGIN + lead, y)
            }
            y += row.size * LINE
        }
        return surface.makeImageSnapshot()
    }

    private class Row(
        val text: String,
        val size: Float,
        val locale: String,
        val file: FaceFile,
        val label: Boolean = false,
        val russianTail: String? = null,
    )

    /** [text] set in [typeface] at [size] for [locale], as a platform that passes the locale to the shaper sets it. */
    private fun paragraph(
        typeface: Typeface,
        text: String,
        size: Float,
        locale: String,
        color: Int,
    ): org.jetbrains.skia.paragraph.Paragraph {
        val fonts =
            FontCollection().apply {
                setDefaultFontManager(TypefaceFontProvider().apply { registerTypeface(typeface, FAMILY) })
            }
        val style =
            TextStyle().apply {
                fontFamilies = arrayOf(FAMILY)
                fontSize = size
                setLocale(locale)
                this.color = color
            }
        return ParagraphBuilder(ParagraphStyle(), fonts).pushStyle(style).addText(text).build().apply {
            layout(SHEET_WIDTH.toFloat())
        }
    }

    /** The pixels of [text] shaped for [locale]: two locales that draw alike draw the same pixels. */
    private fun shapedPixels(
        typeface: Typeface,
        text: String,
        locale: String,
    ): ByteArray {
        val surface = Surface.makeRasterN32Premul(PROBE, PROBE)
        surface.canvas.clear(PAPER)
        paragraph(typeface, text, MEASURE_SIZE, locale, INK).paint(surface.canvas, 0f, 0f)
        return checkNotNull(surface.makeImageSnapshot().encodeToData()).bytes
    }

    private fun typefaceOf(file: FaceFile): Typeface =
        typefaces.getOrPut(file.fileName) {
            val bytes = File(FONTS, file.fileName).readBytes()
            checkNotNull(FontMgr.default.makeFromData(Data.makeFromBytes(bytes))) { "${file.fileName} is no font" }
        }

    private companion object {
        val typefaces = mutableMapOf<String, Typeface>()
        const val FONTS = "src/commonMain/composeResources/font"
        const val FAMILY = "face"

        const val CYRILLIC_UPPER = "А Б В Г Д Ђ Е Ж З И Ј К Л Љ М Н Њ О П Р С Т Ћ У Ф Х Ц Ч Џ Ш"
        const val CYRILLIC_LOWER = "а б в г д ђ е ж з и ј к л љ м н њ о п р с т ћ у ф х ц ч џ ш"
        const val LATIN_UPPER = "A B C Č Ć D Dž Đ E F G H I J K L Lj M N Nj O P R S Š T U V Z Ž Q W X Y"
        const val LATIN_LOWER = "a b c č ć d dž đ e f g h i j k l lj m n nj o p r s š t u v z ž q w x y"
        const val FIGURES = "0123456789  +−×%  „“” ’ – — … ? ! : ; , ."
        const val SAMPLE = "Која река протиче кроз Нови Сад? Дунав · Ђурђевак · Љиљана · Џеп"

        /** The letters Serbian draws differently from Russian: б upright; г д п т only in italic, which is not bundled. */
        const val SERBIAN_FORMS = "б"

        val COVERED: List<Char> =
            (CYRILLIC_UPPER + CYRILLIC_LOWER + LATIN_UPPER + LATIN_LOWER + "0123456789" + "+−×%„“”’–—…?!:;,.")
                .filterNot { it == ' ' }
                .toSet()
                .toList()

        const val MEASURE_SIZE = 100f
        const val FLAT_FIGURE = "1"
        const val FIGURE_TOLERANCE = 0.02f
        const val PROBE = 200

        const val SHEET_WIDTH = 1600
        const val SIZE = 56f
        const val LABEL = 26f
        const val LINE = 1.45f
        const val MARGIN = 40
        const val PAPER = 0xFFFBF8F0.toInt()
        const val INK = 0xFF15121B.toInt()
        const val MUTED = 0xFF6B6570.toInt()
    }
}
