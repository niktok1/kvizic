package io.ntole.kvizic.language

/**
 * The words of the How to play cards, a new install's first screen and Settings' way back to them, as
 * [Strings.howToPlay]: made in each language as the rest of [Strings] is, and checked by the same tests.
 */
data class HowToPlayStrings(
    /** The screen's name, on Settings' button to it. */
    val title: String,
    /** The button that leaves the cards unread. */
    val skip: String,
    /** The button to the next card. */
    val next: String,
    /** The last card's button, to Home. */
    val play: String,
    /** The first card: a question, read, then answered. */
    val questionTitle: String,
    val questionBody: String,
    /** The second card: the points and the minus. */
    val pointsTitle: String,
    val pointsBody: String,
    /** The third card: Quick play, a room of one's own, solo. */
    val roomsTitle: String,
    val roomsBody: String,
) {
    internal fun map(transform: (String) -> String): HowToPlayStrings =
        HowToPlayStrings(
            title = transform(title),
            skip = transform(skip),
            next = transform(next),
            play = transform(play),
            questionTitle = transform(questionTitle),
            questionBody = transform(questionBody),
            pointsTitle = transform(pointsTitle),
            pointsBody = transform(pointsBody),
            roomsTitle = transform(roomsTitle),
            roomsBody = transform(roomsBody),
        )
}

internal val SerbianCyrillicHowToPlayStrings: HowToPlayStrings =
    HowToPlayStrings(
        title = "Како се игра",
        skip = "Прескочи",
        next = "Даље",
        play = "Играј",
        questionTitle = "Исто питање за све",
        questionBody =
            "Прво прочитај, па бирај док траје време. Кад одабереш одговор, видиш шта су други изабрали.",
        pointsTitle = "Брзо и тачно",
        pointsBody = "Бржи одговор даје више поена, погрешан умањује поене. Ако не знаш, прескочи.",
        roomsTitle = "Где да играш",
        roomsBody =
            "Брза игра те одмах води у собу с другима. Своју собу направи за друштво и пошаљи им к\u043E\u0302д. " +
                "Соло је за вежбу. Нема других у соби? Можеш да почнеш и без њих.",
    )

internal val EnglishHowToPlayStrings: HowToPlayStrings =
    HowToPlayStrings(
        title = "How to play",
        skip = "Skip",
        next = "Next",
        play = "Play",
        questionTitle = "One question for all",
        questionBody =
            "Read first, then pick while the time runs. Once you pick an answer, you see what the others picked.",
        pointsTitle = "Fast and right",
        pointsBody = "A faster answer earns more points, a wrong one takes points away. If you do not know, skip it.",
        roomsTitle = "Where to play",
        roomsBody =
            "Quick play takes you straight to a room with others. Make your own room for friends and send them " +
                "the code. Solo is for practice. Alone in a room? You can start anyway.",
    )
