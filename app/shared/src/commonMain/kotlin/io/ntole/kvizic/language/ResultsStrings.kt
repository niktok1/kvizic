package io.ntole.kvizic.language

/**
 * The words of a game's results, as [Strings.results]: made in each language as the rest of [Strings] is,
 * and checked by the same tests.
 */
data class ResultsStrings(
    /** The way back to the room, for its next game. */
    val playAgain: String,
    /** The two views of the results: the standings, and the player's own answers. */
    val standings: String,
    val answers: String,
    /** What sets a number's fraction apart, as in 3,2. */
    val decimalSign: String,
    /** The average time of the player's right answers, `{0}` seconds, for a screen reader. */
    val averageTime: String,
    /** The most right answers in a row, `{0}`, on a chip, and for a screen reader. */
    val streak: String,
    val streakSaid: String,
    /** How many a player got right, `{0}`, beside a check mark, for a screen reader. */
    val rightCount: String,
    /** For a screen reader, of a player who left before the end. */
    val outOfGame: String,
    /** A question the player did not answer. */
    val noAnswer: String,
    /** The right answer, `{0}`, under the player's wrong one. */
    val answerWas: String,
    /** How many of the game's players, `{1}`, had a question right, `{0}`. */
    val roomRight: String,
) {
    /** These strings with [transform] applied to every one of them, as [Strings.map] asks. */
    internal fun map(transform: (String) -> String): ResultsStrings =
        ResultsStrings(
            playAgain = transform(playAgain),
            standings = transform(standings),
            answers = transform(answers),
            decimalSign = decimalSign,
            averageTime = transform(averageTime),
            streak = transform(streak),
            streakSaid = transform(streakSaid),
            rightCount = transform(rightCount),
            outOfGame = transform(outOfGame),
            noAnswer = transform(noAnswer),
            answerWas = transform(answerWas),
            roomRight = transform(roomRight),
        )
}

/** The source text, written by hand. */
internal val SerbianCyrillicResultsStrings: ResultsStrings =
    ResultsStrings(
        playAgain = "Играј поново",
        standings = "Табела",
        answers = "Одговори",
        decimalSign = ",",
        averageTime = "Просечно време тачног одговора: {0} секунде",
        streak = "{0} заредом",
        streakSaid = "Најдужи низ тачних одговора: {0}",
        rightCount = "Тачних одговора: {0}",
        outOfGame = "Ван игре",
        noAnswer = "Без одговора",
        answerWas = "Тачно: {0}",
        roomRight = "{0} од {1} тачно",
    )

internal val EnglishResultsStrings: ResultsStrings =
    ResultsStrings(
        playAgain = "Play again",
        standings = "Standings",
        answers = "Answers",
        decimalSign = ".",
        averageTime = "Average time to a right answer: {0} seconds",
        streak = "{0} in a row",
        streakSaid = "Longest run of right answers: {0}",
        rightCount = "Right answers: {0}",
        outOfGame = "Left the game",
        noAnswer = "No answer",
        answerWas = "Right: {0}",
        roomRight = "{0} of {1} right",
    )

internal val CroatianResultsStrings: ResultsStrings =
    ResultsStrings(
        playAgain = "Igraj ponovno",
        standings = "Poredak",
        answers = "Odgovori",
        decimalSign = ",",
        averageTime = "Prosječno vrijeme točnog odgovora: {0} sekunde",
        streak = "{0} zaredom",
        streakSaid = "Najdulji niz točnih odgovora: {0}",
        rightCount = "Točnih odgovora: {0}",
        outOfGame = "Izvan igre",
        noAnswer = "Bez odgovora",
        answerWas = "Točno: {0}",
        roomRight = "{0} od {1} točno",
    )

internal val BosnianResultsStrings: ResultsStrings =
    ResultsStrings(
        playAgain = "Igraj ponovo",
        standings = "Tabela",
        answers = "Odgovori",
        decimalSign = ",",
        averageTime = "Prosječno vrijeme tačnog odgovora: {0} sekunde",
        streak = "{0} zaredom",
        streakSaid = "Najduži niz tačnih odgovora: {0}",
        rightCount = "Tačnih odgovora: {0}",
        outOfGame = "Van igre",
        noAnswer = "Bez odgovora",
        answerWas = "Tačno: {0}",
        roomRight = "{0} od {1} tačno",
    )

internal val SlovenianResultsStrings: ResultsStrings =
    ResultsStrings(
        playAgain = "Igraj znova",
        standings = "Lestvica",
        answers = "Odgovori",
        decimalSign = ",",
        averageTime = "Povprečni čas do pravilnega odgovora: {0} sekunde",
        streak = "{0} zapored",
        streakSaid = "Najdaljši niz pravilnih odgovorov: {0}",
        rightCount = "Pravilnih odgovorov: {0}",
        outOfGame = "Izven igre",
        noAnswer = "Brez odgovora",
        answerWas = "Pravilno: {0}",
        roomRight = "{0} od {1} pravilno",
    )

internal val MacedonianResultsStrings: ResultsStrings =
    ResultsStrings(
        playAgain = "Играј повторно",
        standings = "Табела",
        answers = "Одговори",
        decimalSign = ",",
        averageTime = "Просечно време на точен одговор: {0} секунди",
        streak = "{0} по ред",
        streakSaid = "Најдолга низа точни одговори: {0}",
        rightCount = "Точни одговори: {0}",
        outOfGame = "Надвор од играта",
        noAnswer = "Без одговор",
        answerWas = "Точно: {0}",
        roomRight = "{0} од {1} точно",
    )
