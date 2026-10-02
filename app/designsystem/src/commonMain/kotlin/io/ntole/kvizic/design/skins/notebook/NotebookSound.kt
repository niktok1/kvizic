package io.ntole.kvizic.design.skins.notebook

import io.ntole.kvizic.design.skin.SkinSound
import io.ntole.kvizic.design.sound.Cue

/**
 * The exercise book's voice: pencil ticks, wooden blocks, paper and soft mallets (`files/sound/notebook`,
 * rendered by `tools/sound/render.py`), quieter than the game show's, as a notebook is.
 */
internal val NotebookSound =
    SkinSound(
        bank = "notebook",
        level = 0.75f,
        jitter = 0.05f,
        trim =
            mapOf(
                Cue.TICK to 0.8f,
                Cue.TILE to 0.8f,
            ),
    )
