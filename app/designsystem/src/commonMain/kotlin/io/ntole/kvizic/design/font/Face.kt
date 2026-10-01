package io.ntole.kvizic.design.font

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.ntole.kvizic.design.resources.Res
import io.ntole.kvizic.design.resources.fira_sans_compressed_bold
import io.ntole.kvizic.design.resources.fira_sans_compressed_heavy
import io.ntole.kvizic.design.resources.fira_sans_regular
import io.ntole.kvizic.design.resources.fira_sans_semibold
import io.ntole.kvizic.design.resources.nunito_variable
import io.ntole.kvizic.design.resources.oswald_bold
import io.ntole.kvizic.design.resources.oswald_semibold
import io.ntole.kvizic.design.resources.sofia_sans_extra_condensed_black
import io.ntole.kvizic.design.resources.sofia_sans_extra_condensed_bold
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.FontResource

/**
 * A typeface bundled with the game, the weights of it that ship, and how it sits beside the others:
 * [sizeScale] brings its letters to the size of the default display face's at one type size, so a skin
 * can swap faces without retuning every style, and [trackingShift] opens or closes its spacing, in em.
 *
 * Only this module makes one, since only this module bundles fonts.
 */
@Immutable
class Face internal constructor(
    val id: String,
    val name: String,
    internal val files: List<FaceFile>,
    val sizeScale: Float = 1f,
    val trackingShift: Float = 0f,
    /** Whether the face draws the Serbian forms of б (and of italic г д п т) for a Serbian locale. */
    val hasSerbianForms: Boolean,
    /** How tall its figures stand, in em: what centres a digit on its flap, which no common text API measures. */
    val figureHeight: Float,
) {
    override fun toString(): String = "Face($id)"
}

/**
 * One weight of a [Face], as the file it ships in: a file of its own, or, when [variable], one variable file
 * drawn at this [weight] on its `wght` axis.
 */
@Immutable
internal class FaceFile(
    val resource: FontResource,
    val weight: FontWeight,
    /** The file's name under `composeResources/font`, which the font tests read it by. */
    val fileName: String,
    val variable: Boolean = false,
)

/** Every face the game bundles. */
object Faces {
    /**
     * Nunito, the game's face since 2026-10-01, the owner's pick: rounded and friendly, set in Black where
     * Fira Compressed stood and at its normal weights for reading. One variable file serves every weight;
     * Android before 8.0 draws only its default, regular, instance. Wider than Fira Compressed, so a long
     * answer is set smaller.
     */
    val Nunito: Face =
        Face(
            id = "nunito",
            name = "Nunito",
            files =
                listOf(FontWeight.Black, FontWeight.ExtraBold, FontWeight.Bold, FontWeight.SemiBold, FontWeight.Normal)
                    .map { weight ->
                        FaceFile(Res.font.nunito_variable, weight, "nunito_variable.ttf", variable = true)
                    },
            sizeScale = NUNITO_SCALE,
            hasSerbianForms = NUNITO_SERBIAN_FORMS,
            figureHeight = NUNITO_FIGURES,
        )

    /**
     * Fira Sans Compressed, which Google Fonts calls Fira Sans Extra Condensed: the default display
     * face, heavy and narrow, so a long Serbian answer still fits a tile. The foundry's 4.301, which
     * carries the Serbian forms that Google Fonts' older copy lacks.
     */
    val FiraCompressed: Face =
        Face(
            id = "fira-compressed",
            name = "Fira Sans Compressed",
            files =
                listOf(
                    FaceFile(Res.font.fira_sans_compressed_heavy, FontWeight.Black, "fira_sans_compressed_heavy.ttf"),
                    FaceFile(Res.font.fira_sans_compressed_bold, FontWeight.Bold, "fira_sans_compressed_bold.ttf"),
                ),
            hasSerbianForms = true,
            figureHeight = FIRA_COMPRESSED_FIGURES,
        )

    /** Fira Sans, the text face beside it: the same design at its normal width, for reading. */
    val FiraSans: Face =
        Face(
            id = "fira-sans",
            name = "Fira Sans",
            files =
                listOf(
                    FaceFile(Res.font.fira_sans_regular, FontWeight.Normal, "fira_sans_regular.ttf"),
                    FaceFile(Res.font.fira_sans_semibold, FontWeight.SemiBold, "fira_sans_semibold.ttf"),
                ),
            hasSerbianForms = true,
            figureHeight = FIRA_SANS_FIGURES,
        )

    /** Oswald, a poster gothic, to compare: taller letters, so it is set smaller. No Serbian forms. */
    val Oswald: Face =
        Face(
            id = "oswald",
            name = "Oswald",
            files =
                listOf(
                    FaceFile(Res.font.oswald_bold, FontWeight.Bold, "oswald_bold.ttf"),
                    FaceFile(Res.font.oswald_semibold, FontWeight.SemiBold, "oswald_semibold.ttf"),
                ),
            sizeScale = OSWALD_SCALE,
            hasSerbianForms = false,
            figureHeight = OSWALD_FIGURES,
        )

    /**
     * Sofia Sans Extra Condensed, to compare: narrower still, with smaller letters, so it is set larger.
     * No Serbian forms; its Cyrillic defaults are the foundry's Bulgarian ones.
     */
    val SofiaExtraCondensed: Face =
        Face(
            id = "sofia-extra-condensed",
            name = "Sofia Sans Extra Condensed",
            files =
                listOf(
                    FaceFile(
                        Res.font.sofia_sans_extra_condensed_black,
                        FontWeight.Black,
                        "sofia_sans_extra_condensed_black.ttf",
                    ),
                    FaceFile(
                        Res.font.sofia_sans_extra_condensed_bold,
                        FontWeight.Bold,
                        "sofia_sans_extra_condensed_bold.ttf",
                    ),
                ),
            sizeScale = SOFIA_SCALE,
            trackingShift = SOFIA_TRACKING,
            hasSerbianForms = false,
            figureHeight = SOFIA_FIGURES,
        )

    /** The display faces a skin may be compared in, the default first. */
    val DISPLAY: List<Face> = listOf(Nunito, FiraCompressed, Oswald, SofiaExtraCondensed)

    val ALL: List<Face> = listOf(Nunito, FiraCompressed, FiraSans, Oswald, SofiaExtraCondensed)

    fun ofId(id: String): Face? = ALL.firstOrNull { it.id == id }
}

/** The face as a [FontFamily] of every weight it ships in, loaded from the bundled files. */
@Composable
internal fun Face.family(): FontFamily {
    val fonts =
        files.map {
            if (it.variable) {
                Font(
                    it.resource,
                    it.weight,
                    variationSettings = FontVariation.Settings(FontVariation.weight(it.weight.weight)),
                )
            } else {
                Font(it.resource, it.weight)
            }
        }
    return remember(fonts) { FontFamily(fonts) }
}

// Each face's figures, in em, as FontCoverageTest measures them from the files' outlines.
private const val NUNITO_FIGURES = 0.713f
private const val NUNITO_SCALE = 1f
private const val NUNITO_SERBIAN_FORMS = false
private const val FIRA_COMPRESSED_FIGURES = 0.68f
private const val FIRA_SANS_FIGURES = 0.67f
private const val OSWALD_FIGURES = 0.81f
private const val SOFIA_FIGURES = 0.66f

// Oswald's capitals and figures stand about a sixth taller than Fira Compressed's at one size.
private const val OSWALD_SCALE = 0.86f

// Sofia Extra Condensed's stand a little shorter, and its narrow letters want a touch more air.
private const val SOFIA_SCALE = 1.08f
private const val SOFIA_TRACKING = 0.01f
