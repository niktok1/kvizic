package io.ntole.kvizic.design.skins.buzzers

import io.ntole.kvizic.design.font.Faces
import io.ntole.kvizic.design.skin.Skin
import io.ntole.kvizic.design.skin.SkinFonts

/**
 * Skin #1, the game show: the answers are chunky enamel buzzers with hard, straight-down sides that sink
 * under the finger and stay down once an answer is locked in; the stage is dark under one spotlight;
 * scores, the timer and a room's code flip on split flaps; and the type is a heavy condensed face.
 */
internal val BuzzersSkin: Skin =
    Skin(
        id = "buzzers",
        name = "Buzzers",
        colors = BuzzersColors,
        fonts = SkinFonts(display = Faces.FiraCompressed, body = Faces.FiraSans),
        type = BuzzersType,
        shapes = BuzzersShapes,
        depth = BuzzersDepth,
        space = BuzzersSpace,
        motion = BuzzersMotion,
        parts = BuzzersParts,
        backdrop = BuzzersBackdrop,
        avatarPalette = BuzzersAvatars,
    )
