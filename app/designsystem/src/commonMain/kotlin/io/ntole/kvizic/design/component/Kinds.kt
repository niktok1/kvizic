package io.ntole.kvizic.design.component

/** Where an answer tile stands in a question's life. A press is no state: the tile sinks under the finger by itself. */
enum class AnswerTileState {
    /** Up and waiting, to be tapped while the question takes answers. */
    IDLE,

    /** The player's answer, locked in: the tile stays down, as a buzzer does once hit. */
    LOCKED_IN,

    /** The right answer, lit, once the answer is revealed. */
    CORRECT,

    /** The player's answer, shown wrong. */
    WRONG,

    /** An answer that no longer matters: another was locked in, or the reveal passed it by. */
    DIMMED,
}

/** How a tile lays out its letter and answer: [ROW] across a whole width, [STACK] in a grid's cell. */
enum class TileArrangement { ROW, STACK }

enum class ButtonKind {
    /** The one thing a screen is for: Брза игра, Почни игру. */
    PRIMARY,

    /** Another way on, on a plate. */
    SECONDARY,

    /** A way on that stands back, on the dark. */
    DARK,

    /** A word with no surface, for the smallest of choices: Соло. */
    QUIET,
}

enum class ButtonSize { HERO, REGULAR, SMALL }

enum class PanelKind {
    /** A raised dark card: a seat, a scoreboard. */
    PLAIN,

    /** Where the question is shown, framed as a screen on the set. */
    SCREEN,

    /** Sunk into the page: the board a room's code is shown on. */
    WELL,

    /** A place nothing fills yet: an empty seat, outlined and no more. */
    EMPTY,
}

/** What a chip says of itself by its colour. */
enum class ChipTone { NEUTRAL, ACCENT, GAIN, LOSS }

enum class FlapSize { SMALL, MEDIUM, LARGE }

enum class AvatarSize { XS, SM, MD, LG, XL }

enum class TimerSize { SMALL, REGULAR }
