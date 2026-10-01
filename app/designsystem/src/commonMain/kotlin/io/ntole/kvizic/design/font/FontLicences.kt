package io.ntole.kvizic.design.font

import io.ntole.kvizic.design.resources.Res

/**
 * The licence each bundled face ships under: the SIL Open Font License, whose text must travel with the
 * fonts, so it is bundled beside them for the About screen to show.
 */
object FontLicences {
    /** The licence of the [faces] named, as the file bundled under `composeResources/files`. */
    class Licence internal constructor(
        val faces: List<Face>,
        internal val path: String,
    )

    val ALL: List<Licence> =
        listOf(
            Licence(listOf(Faces.Nunito), "files/licenses/nunito_ofl.txt"),
            Licence(listOf(Faces.FiraCompressed, Faces.FiraSans), "files/licenses/fira_sans_ofl.txt"),
            Licence(listOf(Faces.Oswald), "files/licenses/oswald_ofl.txt"),
            Licence(listOf(Faces.SofiaExtraCondensed), "files/licenses/sofia_sans_ofl.txt"),
        )

    /** The licence's whole text. */
    suspend fun text(licence: Licence): String = Res.readBytes(licence.path).decodeToString()
}
