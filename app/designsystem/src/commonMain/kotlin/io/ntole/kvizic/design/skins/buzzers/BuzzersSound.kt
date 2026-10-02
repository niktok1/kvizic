package io.ntole.kvizic.design.skins.buzzers

import io.ntole.kvizic.design.skin.SkinSound
import io.ntole.kvizic.design.sound.Cue

/**
 * The game show's voice: buzzers, bells and bright synth stabs, loud and clear (`files/sound/buzzers`,
 * rendered by `tools/sound/render.py`). The ticks and the tiles are trimmed, since they come in runs.
 */
internal val BuzzersSound =
    SkinSound(
        bank = "buzzers",
        level = 0.9f,
        jitter = 0.04f,
        trim =
            mapOf(
                Cue.TICK to 0.7f,
                Cue.TILE to 0.7f,
                Cue.COUNT to 0.8f,
            ),
    )
