package io.ntole.kvizic.design.skin

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize

/**
 * Every length a skin lays out with: the spacing scale, then each component's own sizes. Screens and
 * components read these and write no dp of their own, so a skin that wants roomier tiles or a bigger
 * code board changes it here.
 */
@Immutable
data class SkinSpace(
    val xxs: Dp,
    val xs: Dp,
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp,
    val xxl: Dp,
    /** The screen's own padding from its edges. */
    val screen: Dp,
    /** The least a finger's target is, whatever the thing tapped draws. */
    val touchTarget: Dp,
    /** The outline drawn round a raised thing, and the thinner one of small things. */
    val stroke: Dp,
    val strokeThin: Dp,
    val icon: IconSizes,
    val tile: TileSizes,
    val button: ButtonSizes,
    val chip: ChipSizes,
    val avatar: AvatarSizes,
    val flap: FlapSizes,
    val timer: TimerSizes,
    val podium: PodiumSizes,
    val burst: Dp,
    val spinner: Dp,
    val seat: SeatSizes,
    val logo: LogoSizes,
)

@Immutable
data class IconSizes(
    val small: Dp,
    val medium: Dp,
    val large: Dp,
)

@Immutable
data class TileSizes(
    /** A tile in a grid of two columns, its letter above its answer. */
    val gridMinHeight: Dp,
    /** A tile across the width, its letter beside its answer: three answers in a column. */
    val rowMinHeight: Dp,
    /** A tile across the width of two, stacked tall: a true or false question. */
    val tallMinHeight: Dp,
    val gap: Dp,
    val padding: Dp,
    /** The mark a tile's letter stands in, and how far past the tile's corner a result's stamp reaches. */
    val letterMark: Dp,
    val stamp: Dp,
)

@Immutable
data class ButtonSizes(
    val hero: Dp,
    val regular: Dp,
    val small: Dp,
    val paddingHorizontal: Dp,
    /** A round button of one icon: a reaction, back, share. */
    val round: Dp,
    val roundSmall: Dp,
)

@Immutable
data class ChipSizes(
    val height: Dp,
    val paddingHorizontal: Dp,
)

@Immutable
data class AvatarSizes(
    val xs: Dp,
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp,
    /** The ring in the seat's colour, and the host's badge, as a share of the avatar's size (1 is whole). */
    val ring: Dp,
    val badgeFraction: Float,
    /** How far each avatar of a stack covers the one before it, as a share of its size. */
    val stackOverlap: Float,
)

@Immutable
data class FlapSizes(
    val small: DpSize,
    val medium: DpSize,
    val large: DpSize,
    /** Between two cells, and between two groups of digits. */
    val gap: Dp,
    val groupGap: Dp,
    /** The digit's size as a share of the cell's height. */
    val digitFraction: Float,
)

@Immutable
data class TimerSizes(
    val regular: Dp,
    val small: Dp,
    /** How many bulbs run round the timer. */
    val bulbs: Int,
    /** A bulb's size as a share of the timer's. */
    val bulbFraction: Float,
)

@Immutable
data class PodiumSizes(
    val first: Dp,
    val second: Dp,
    val third: Dp,
    val stepGap: Dp,
)

@Immutable
data class SeatSizes(
    /** A lobby seat's card, the avatar on it and its name under it. */
    val height: Dp,
    val columns: Int,
)

@Immutable
data class LogoSizes(
    /** The wordmark's sign on Home, and how many bulbs run along each of its long edges. */
    val signHeight: Dp,
    val signBulbsAcross: Int,
)
