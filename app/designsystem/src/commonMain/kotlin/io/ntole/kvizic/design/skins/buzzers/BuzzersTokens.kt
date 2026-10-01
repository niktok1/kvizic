package io.ntole.kvizic.design.skins.buzzers

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.ntole.kvizic.design.skin.AvatarPalette
import io.ntole.kvizic.design.skin.AvatarSizes
import io.ntole.kvizic.design.skin.ButtonSizes
import io.ntole.kvizic.design.skin.ChipSizes
import io.ntole.kvizic.design.skin.DepthDirection
import io.ntole.kvizic.design.skin.FlapSizes
import io.ntole.kvizic.design.skin.FontRole
import io.ntole.kvizic.design.skin.IconSizes
import io.ntole.kvizic.design.skin.LogoSizes
import io.ntole.kvizic.design.skin.PodiumSizes
import io.ntole.kvizic.design.skin.SeatSizes
import io.ntole.kvizic.design.skin.SkinColors
import io.ntole.kvizic.design.skin.SkinDepth
import io.ntole.kvizic.design.skin.SkinMotion
import io.ntole.kvizic.design.skin.SkinShapes
import io.ntole.kvizic.design.skin.SkinSpace
import io.ntole.kvizic.design.skin.SkinTypeScale
import io.ntole.kvizic.design.skin.TileSizes
import io.ntole.kvizic.design.skin.TimerSizes
import io.ntole.kvizic.design.skin.TypeSpec

/*
 * Buzzers' tokens: a game show's set. A dark stage under one warm spotlight, cream words, and the
 * answers as enamel buzzers edged in ink, each with a coloured bulb for its letter. Every literal of the
 * skin is here or in its parts and backdrop; `SkinContrastTest` holds each pair to AA.
 */

internal val Stage = Color(0xFF15121B)
internal val Ink = Color(0xFF0B0A0E)
internal val Cream = Color(0xFFF4EBDD)
internal val Amber = Color(0xFFF2A93B)
internal val Tomato = Color(0xFFE4572E)
internal val Teal = Color(0xFF25A99A)
internal val Cobalt = Color(0xFF5577F7)
internal val Green = Color(0xFF3DBE6E)

/** The spotlight's warm white, which its beam, its pool and the floor's halftone are drawn in at a few percent. */
internal val Spotlight = Color(0xFFFFDFA8)

internal val BuzzersColors =
    SkinColors(
        page = Stage,
        onPage = Cream,
        onPageMuted = Color(0xFFC2B8AD),
        onPageAccent = Amber,
        raised = Color(0xFF221D29),
        raisedSide = Color(0xFF0F0D13),
        onRaised = Cream,
        onRaisedMuted = Color(0xFFC2B8AD),
        outline = Ink,
        plate = Color(0xFFEFE4D0),
        plateSide = Color(0xFFB39F82),
        onPlate = Ink,
        onPlateMuted = Color(0xFF5B5148),
        primary = Amber,
        primarySide = Color(0xFFB06F14),
        onPrimary = Ink,
        lockedIn = Amber,
        lockedInSide = Color(0xFFB06F14),
        onLockedIn = Ink,
        correct = Green,
        correctSide = Color(0xFF23864A),
        onCorrect = Ink,
        wrong = Tomato,
        wrongSide = Color(0xFFA63A1B),
        onWrong = Ink,
        unlit = Color(0xFF2B2530),
        unlitSide = Color(0xFF19151D),
        onUnlit = Color(0xFFB5ABA0),
        letters = listOf(Amber, Tomato, Teal, Cobalt),
        onLetter = Ink,
        letterUnlit = Color(0xFF3A3340),
        onLetterUnlit = Color(0xFFB5ABA0),
        seats =
            listOf(
                Amber,
                Tomato,
                Teal,
                Cobalt,
                Color(0xFFEC6FA2),
                Color(0xFFA5CB45),
                Color(0xFF52B7EA),
                Color(0xFFA07CF0),
            ),
        flap = Color(0xFF29242E),
        flapShade = Color(0xFF1F1B23),
        onFlap = Cream,
        flapSplit = Ink,
        bulbOn = Color(0xFFFFE3A1),
        bulbOff = Color(0xFF3A3340),
        bulbWarn = Color(0xFFFF9478),
        gain = Color(0xFF5EDC8C),
        loss = Color(0xFFFF9478),
        focus = Color(0xFF52B7EA),
    )

/** The podium's third step: copper, beside the winner's amber and the second's enamel. */
internal val Copper = Color(0xFFD08A5C)
internal val CopperSide = Color(0xFF8E5533)

/** The question's screen on the set: a shade off the dark of a panel. */
internal val ScreenFace = Color(0xFF1B1720)

/** A well sunk in the stage: darker than the page. */
internal val WellFace = Color(0xFF0E0C12)

internal val BuzzersType =
    SkinTypeScale(
        logo = TypeSpec(FontRole.DISPLAY, FontWeight.Black, 60.sp, 60.sp, 0.02.em, caps = true),
        hero = TypeSpec(FontRole.DISPLAY, FontWeight.Black, 40.sp, 42.sp, 0.03.em, caps = true),
        headline = TypeSpec(FontRole.DISPLAY, FontWeight.Black, 30.sp, 32.sp, 0.02.em, caps = true),
        questionReading = TypeSpec(FontRole.DISPLAY, FontWeight.Bold, 36.sp, 1.08.em),
        question = TypeSpec(FontRole.DISPLAY, FontWeight.Bold, 27.sp, 1.11.em),
        questionMin = TypeSpec(FontRole.DISPLAY, FontWeight.Bold, 16.sp, 1.15.em),
        answer = TypeSpec(FontRole.DISPLAY, FontWeight.Bold, 30.sp, 1.07.em),
        answerMin = TypeSpec(FontRole.DISPLAY, FontWeight.Bold, 14.sp, 1.1.em),
        letter = TypeSpec(FontRole.DISPLAY, FontWeight.Black, 22.sp, 22.sp),
        button = TypeSpec(FontRole.DISPLAY, FontWeight.Black, 21.sp, 23.sp, 0.04.em, caps = true),
        buttonSmall = TypeSpec(FontRole.DISPLAY, FontWeight.Bold, 17.sp, 19.sp, 0.05.em, caps = true),
        label = TypeSpec(FontRole.BODY, FontWeight.SemiBold, 12.sp, 16.sp, 0.14.em, caps = true),
        chip = TypeSpec(FontRole.BODY, FontWeight.SemiBold, 13.sp, 16.sp),
        body = TypeSpec(FontRole.BODY, FontWeight.Normal, 15.sp, 21.sp),
        bodyStrong = TypeSpec(FontRole.BODY, FontWeight.SemiBold, 15.sp, 21.sp),
        caption = TypeSpec(FontRole.BODY, FontWeight.Normal, 12.sp, 16.sp),
        name = TypeSpec(FontRole.DISPLAY, FontWeight.Bold, 16.sp, 18.sp),
        delta = TypeSpec(FontRole.DISPLAY, FontWeight.Black, 18.sp, 20.sp, features = "tnum"),
        digits = TypeSpec(FontRole.DISPLAY, FontWeight.Black, 24.sp, 24.sp, features = "tnum"),
        badge = TypeSpec(FontRole.DISPLAY, FontWeight.Black, 12.sp, 14.sp, features = "tnum"),
    )

internal val BuzzersSpace =
    SkinSpace(
        xxs = 2.dp,
        xs = 4.dp,
        sm = 8.dp,
        md = 12.dp,
        lg = 16.dp,
        xl = 24.dp,
        xxl = 32.dp,
        screen = 20.dp,
        touchTarget = 48.dp,
        contentWidth = 560.dp,
        dialogWidth = 400.dp,
        stroke = 3.dp,
        strokeThin = 2.dp,
        icon = IconSizes(small = 16.dp, medium = 22.dp, large = 30.dp),
        tile =
            TileSizes(
                gridMinHeight = 118.dp,
                rowMinHeight = 74.dp,
                tallMinHeight = 128.dp,
                gap = 12.dp,
                rowGap = 18.dp,
                padding = 14.dp,
                rowPaddingVertical = 9.dp,
                letterMark = 40.dp,
                stamp = 26.dp,
                pickersPeek = 14.dp,
            ),
        button =
            ButtonSizes(
                hero = 108.dp,
                regular = 64.dp,
                small = 48.dp,
                paddingHorizontal = 18.dp,
                round = 52.dp,
                roundSmall = 44.dp,
            ),
        chip = ChipSizes(height = 30.dp, paddingHorizontal = 11.dp),
        avatar =
            AvatarSizes(
                xs = 26.dp,
                sm = 34.dp,
                md = 50.dp,
                lg = 60.dp,
                xl = 78.dp,
                ring = 3.dp,
                badgeFraction = 0.44f,
                stackOverlap = 0.3f,
                crowdOverlap = 0.7f,
            ),
        flap =
            FlapSizes(
                small = DpSize(17.dp, 25.dp),
                medium = DpSize(24.dp, 36.dp),
                large = DpSize(38.dp, 56.dp),
                gap = 3.dp,
                groupGap = 12.dp,
                digitFraction = 0.74f,
            ),
        timer = TimerSizes(regular = 112.dp, small = 76.dp, bulbs = 24, bulbFraction = 0.085f),
        podium = PodiumSizes(first = 116.dp, second = 84.dp, third = 60.dp, stepGap = 10.dp),
        burst = 64.dp,
        spinner = 44.dp,
        seat = SeatSizes(height = 112.dp, columns = 4),
        logo = LogoSizes(signHeight = 124.dp, signBulbsAcross = 11),
    )

internal val BuzzersShapes =
    SkinShapes(
        tile = RoundedCornerShape(16.dp),
        button = RoundedCornerShape(18.dp),
        heroButton = RoundedCornerShape(26.dp),
        roundButton = CircleShape,
        panel = RoundedCornerShape(18.dp),
        chip = RoundedCornerShape(percent = 50),
        flap = RoundedCornerShape(5.dp),
        letterMark = CircleShape,
        avatar = CircleShape,
        podium = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
        seat = RoundedCornerShape(16.dp),
    )

/** The spotlight hangs right overhead, so every shadow on the set falls straight down, as a key's side. */
internal val BuzzersDepth =
    SkinDepth.HardOffset(
        lift = 6.dp,
        pressedLift = 1.dp,
        lockedLift = 2.dp,
        outline = 3.dp,
        direction = DepthDirection.Down,
    )

internal val BuzzersMotion =
    SkinMotion(
        press = 70,
        releaseDamping = 0.42f,
        releaseStiffness = 900f,
        settle = 180,
        reveal = 320,
        flap = 90,
        flapsPerDigit = 3,
        flapStagger = 55,
        bulbFlicker = 280,
        warnSeconds = 5,
        burst = 1_400,
        spinnerTurn = 1_100,
        stage = 320,
        tileAppear = 260,
        tileStagger = 70,
    )

internal val BuzzersAvatars =
    AvatarPalette(
        ink = Ink,
        cream = Cream,
        blush = Color(0xFFF08A7E),
        orange = Color(0xFFEF7A2D),
        orangeDark = Color(0xFF9E4412),
        brown = Color(0xFF93603E),
        brownDark = Color(0xFF5B3522),
        tan = Color(0xFFD9A66B),
        tanDark = Color(0xFF9C6B3C),
        grey = Color(0xFF8C8794),
        greyDark = Color(0xFF55505D),
        gold = Color(0xFFF5C234),
        green = Color(0xFF6DBE45),
        greenDark = Color(0xFF3E7A23),
        disc = Color(0xFF2B2530),
        silhouette = Color(0xFF6A6372),
    )
