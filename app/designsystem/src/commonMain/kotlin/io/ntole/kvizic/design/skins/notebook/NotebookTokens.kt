package io.ntole.kvizic.design.skins.notebook

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.ntole.kvizic.design.skin.AvatarPalette
import io.ntole.kvizic.design.skin.DepthDirection
import io.ntole.kvizic.design.skin.FontRole
import io.ntole.kvizic.design.skin.SkinColors
import io.ntole.kvizic.design.skin.SkinDepth
import io.ntole.kvizic.design.skin.SkinMotion
import io.ntole.kvizic.design.skin.SkinShapes
import io.ntole.kvizic.design.skin.SkinTypeScale
import io.ntole.kvizic.design.skin.TypeSpec
import io.ntole.kvizic.design.skins.buzzers.BuzzersSpace

/*
 * Notebook's tokens, a rough second skin to prove a skin swaps more than colours: a quiz scribbled in a
 * squared exercise book, in ballpoint and markers, every edge drawn by hand and every shadow pencilled.
 */

internal val Paper = Color(0xFFFBF8F0)
internal val Ballpoint = Color(0xFF1E2A4A)
internal val Pencil = Color(0xFF9AA3B5)
internal val Card = Color(0xFFFFFFFF)
internal val BlueMarker = Color(0xFF2F5FC4)
internal val Highlighter = Color(0xFFFFE45C)
internal val GreenHighlighter = Color(0xFF6CC58A)
internal val PinkHighlighter = Color(0xFFF2767B)
internal val RedPen = Color(0xFFA3222C)
internal val GridLine = Color(0xFFDCE6F2)
internal val MarginLine = Color(0xFFF8DADA)
internal val StickyNote = Color(0xFFFFE680)

internal val NotebookColors =
    SkinColors(
        page = Paper,
        onPage = Ballpoint,
        onPageMuted = Color(0xFF525C73),
        onPageAccent = RedPen,
        raised = Card,
        raisedSide = Pencil,
        onRaised = Ballpoint,
        onRaisedMuted = Color(0xFF525C73),
        outline = Ballpoint,
        plate = Card,
        plateSide = Pencil,
        onPlate = Ballpoint,
        onPlateMuted = Color(0xFF525C73),
        primary = BlueMarker,
        primarySide = Pencil,
        onPrimary = Card,
        // A locked answer is highlighted, not lit: the tile stays paper under a yellow swipe.
        lockedIn = Card,
        lockedInSide = Pencil,
        onLockedIn = Ballpoint,
        correct = Card,
        correctSide = Pencil,
        onCorrect = Ballpoint,
        wrong = Card,
        wrongSide = Pencil,
        onWrong = Ballpoint,
        unlit = Color(0xFFF1EEE6),
        unlitSide = Color(0xFFD5D9E1),
        onUnlit = Color(0xFF5E6579),
        letters = listOf(Color(0xFFF4A259), PinkHighlighter, GreenHighlighter, Color(0xFF7CA7F2)),
        onLetter = Ballpoint,
        letterUnlit = Color(0xFFE4E6EC),
        onLetterUnlit = Color(0xFF5E6579),
        seats =
            listOf(
                Color(0xFFD2641A),
                Color(0xFFD9434A),
                Color(0xFF2F9E5A),
                BlueMarker,
                Color(0xFFC0469A),
                Color(0xFF7A9A1E),
                Color(0xFF1C8DB8),
                Color(0xFF7B55C9),
            ),
        flap = Card,
        flapShade = Color(0xFFF3F0E8),
        onFlap = Ballpoint,
        flapSplit = GridLine,
        bulbOn = Color(0xFFFFD43B),
        bulbOff = Color(0xFFE4E6EC),
        bulbWarn = Color(0xFFE5484D),
        gain = Color(0xFF16602C),
        loss = RedPen,
        focus = BlueMarker,
    )

internal val NotebookType =
    SkinTypeScale(
        logo = TypeSpec(FontRole.DISPLAY, FontWeight.Black, 64.sp, 64.sp),
        hero = TypeSpec(FontRole.DISPLAY, FontWeight.Black, 40.sp, 42.sp),
        headline = TypeSpec(FontRole.DISPLAY, FontWeight.Black, 32.sp, 34.sp),
        question = TypeSpec(FontRole.DISPLAY, FontWeight.Bold, 29.sp, 1.07.em),
        questionMin = TypeSpec(FontRole.DISPLAY, FontWeight.Bold, 17.sp, 1.12.em),
        answer = TypeSpec(FontRole.DISPLAY, FontWeight.Bold, 32.sp, 1.06.em),
        answerMin = TypeSpec(FontRole.DISPLAY, FontWeight.Bold, 14.sp, 1.1.em),
        letter = TypeSpec(FontRole.DISPLAY, FontWeight.Black, 24.sp, 24.sp),
        button = TypeSpec(FontRole.DISPLAY, FontWeight.Black, 24.sp, 26.sp, 0.01.em),
        buttonSmall = TypeSpec(FontRole.DISPLAY, FontWeight.Bold, 20.sp, 22.sp),
        label = TypeSpec(FontRole.BODY, FontWeight.SemiBold, 13.sp, 17.sp, 0.02.em),
        chip = TypeSpec(FontRole.BODY, FontWeight.SemiBold, 13.sp, 16.sp),
        body = TypeSpec(FontRole.BODY, FontWeight.Normal, 15.sp, 21.sp),
        bodyStrong = TypeSpec(FontRole.BODY, FontWeight.SemiBold, 15.sp, 21.sp),
        caption = TypeSpec(FontRole.BODY, FontWeight.Normal, 12.sp, 16.sp),
        name = TypeSpec(FontRole.DISPLAY, FontWeight.Bold, 17.sp, 19.sp),
        delta = TypeSpec(FontRole.DISPLAY, FontWeight.Black, 19.sp, 21.sp, features = "tnum"),
        digits = TypeSpec(FontRole.DISPLAY, FontWeight.Black, 24.sp, 24.sp, features = "tnum"),
        badge = TypeSpec(FontRole.DISPLAY, FontWeight.Black, 13.sp, 15.sp, features = "tnum"),
    )

/**
 * The same room as the stage's, the tiles a touch roomier for the hand-drawn edge; those who picked one rise a
 * little higher over its edge, since its answer comes nearer it.
 */
internal val NotebookSpace =
    BuzzersSpace.copy(
        stroke = 2.2.dp,
        tile = BuzzersSpace.tile.copy(padding = 15.dp, rowPaddingVertical = 5.dp, pickersPeek = 13.dp),
        timer = BuzzersSpace.timer.copy(bulbs = 12),
    )

internal val NotebookShapes =
    SkinShapes(
        tile = RoundedCornerShape(8.dp),
        button = RoundedCornerShape(12.dp),
        heroButton = RoundedCornerShape(14.dp),
        roundButton = CircleShape,
        panel = RoundedCornerShape(6.dp),
        chip = RoundedCornerShape(percent = 50),
        flap = RoundedCornerShape(4.dp),
        letterMark = CircleShape,
        avatar = CircleShape,
        podium = RoundedCornerShape(6.dp),
        seat = RoundedCornerShape(10.dp),
    )

/** A desk lamp over the left shoulder: shadows fall down and to the right, pencilled in. */
internal val NotebookDepth =
    SkinDepth.Sketched(
        lift = 5.dp,
        pressedLift = 1.dp,
        lockedLift = 2.dp,
        stroke = 2.2.dp,
        wobble = 1.3.dp,
        hatchGap = 4.dp,
        direction = DepthDirection.DownRight,
    )

internal val NotebookMotion =
    SkinMotion(
        press = 90,
        releaseDamping = 0.6f,
        releaseStiffness = 600f,
        settle = 220,
        reveal = 380,
        flap = 140,
        flapsPerDigit = 2,
        flapStagger = 70,
        bulbFlicker = 200,
        warnSeconds = 5,
        burst = 1_400,
        spinnerTurn = 1_300,
        stage = 360,
        tileAppear = 300,
        tileStagger = 80,
    )

internal val NotebookAvatars =
    AvatarPalette(
        ink = Ballpoint,
        cream = Card,
        blush = Color(0xFFF7A1A6),
        orange = Color(0xFFF4A259),
        orangeDark = Color(0xFFC46A1E),
        brown = Color(0xFFB98A65),
        brownDark = Color(0xFF7A5337),
        tan = Color(0xFFE9C79E),
        tanDark = Color(0xFFB9895C),
        grey = Color(0xFFB4B9C6),
        greyDark = Color(0xFF7C8396),
        gold = Color(0xFFFFD43B),
        green = Color(0xFF9BD27A),
        greenDark = Color(0xFF5E9442),
        disc = Card,
        silhouette = Color(0xFFCDD2DC),
    )
