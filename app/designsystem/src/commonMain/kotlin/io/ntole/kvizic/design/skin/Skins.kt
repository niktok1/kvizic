package io.ntole.kvizic.design.skin

import io.ntole.kvizic.design.skins.buzzers.BuzzersSkin
import io.ntole.kvizic.design.skins.notebook.NotebookSkin

/** Every skin the game can wear. A new skin is its own package and one line in [ALL]. */
object Skins {
    /** The game-show set, the default: enamel buzzers on a dark stage under one spotlight. */
    val Buzzers: Skin = BuzzersSkin

    val Default: Skin = Buzzers

    val ALL: List<Skin> =
        listOf(
            BuzzersSkin,
            NotebookSkin,
        )

    /** The skin of [id], or null for one this build has not got. */
    fun ofId(id: String?): Skin? = ALL.firstOrNull { it.id == id }
}
