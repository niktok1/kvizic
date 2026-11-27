package io.ntole.kvizic.language

/**
 * The words of a room's settings tiles, as [Strings.roomSettings]: the name of the one the game's words have no
 * short name for, what each tile's bubble says, and what an unnamed room goes by. `{0}` is the value in the game's own words (`10 питања`,
 * `15 с`, `8`).
 */
data class RoomSettingsStrings(
    /** The name of the tile that says who may come into the room. */
    val entry: String,
    val questionsSaid: String,
    val timeSaid: String,
    val veryEasySaid: String,
    val easySaid: String,
    val mediumSaid: String,
    val hardSaid: String,
    val playersSaid: String,
    val publicSaid: String,
    val privateSaid: String,
    val penaltyOnSaid: String,
    val penaltyOffSaid: String,
    /** What a screen reader says of a step key's halves, after the setting's name. */
    val less: String,
    val more: String,
    /** Under the room's name, while it has none: the room goes by its host's name. */
    val unnamed: String,
) {
    internal fun map(transform: (String) -> String): RoomSettingsStrings =
        RoomSettingsStrings(
            entry = transform(entry),
            questionsSaid = transform(questionsSaid),
            timeSaid = transform(timeSaid),
            veryEasySaid = transform(veryEasySaid),
            easySaid = transform(easySaid),
            mediumSaid = transform(mediumSaid),
            hardSaid = transform(hardSaid),
            playersSaid = transform(playersSaid),
            publicSaid = transform(publicSaid),
            privateSaid = transform(privateSaid),
            penaltyOnSaid = transform(penaltyOnSaid),
            penaltyOffSaid = transform(penaltyOffSaid),
            less = transform(less),
            more = transform(more),
            unnamed = transform(unnamed),
        )
}

internal val SerbianCyrillicRoomSettingsStrings: RoomSettingsStrings =
    RoomSettingsStrings(
        entry = "Улаз",
        questionsSaid = "{0} у игри",
        timeSaid = "{0} за сваки одговор",
        veryEasySaid = "Питања која скоро свако зна",
        easySaid = "Углавном лака, понеко теже",
        mediumSaid = "Мешавина за већину",
        hardSaid = "Углавном тешка питања",
        playersSaid = "До {0} играча",
        publicSaid = "Соба је на листи, свако може да уђе",
        privateSaid = "Улази се само са кодом",
        penaltyOnSaid = "Погрешан одговор одузима поене",
        penaltyOffSaid = "Погрешан одговор не одузима поене",
        less = "мање",
        more = "више",
        unnamed = "Без назива, соба се зове по теби",
    )

internal val EnglishRoomSettingsStrings: RoomSettingsStrings =
    RoomSettingsStrings(
        entry = "Entry",
        questionsSaid = "{0} in a game",
        timeSaid = "{0} for each answer",
        veryEasySaid = "Questions almost everyone knows",
        easySaid = "Mostly easy, a few harder",
        mediumSaid = "A mix for most players",
        hardSaid = "Mostly hard questions",
        playersSaid = "Up to {0} players",
        publicSaid = "The room is listed, anyone can join",
        privateSaid = "Join with the code only",
        penaltyOnSaid = "A wrong answer costs points",
        penaltyOffSaid = "A wrong answer costs nothing",
        less = "less",
        more = "more",
        unnamed = "With no name, the room goes by yours",
    )

internal val CroatianRoomSettingsStrings: RoomSettingsStrings =
    RoomSettingsStrings(
        entry = "Ulaz",
        questionsSaid = "{0} u igri",
        timeSaid = "{0} za svaki odgovor",
        veryEasySaid = "Pitanja koja gotovo svatko zna",
        easySaid = "Uglavnom laka, poneko teže",
        mediumSaid = "Mješavina za većinu",
        hardSaid = "Uglavnom teška pitanja",
        playersSaid = "Do {0} igrača",
        publicSaid = "Soba je na popisu, svatko može ući",
        privateSaid = "Ulazi se samo s kodom",
        penaltyOnSaid = "Netočan odgovor oduzima bodove",
        penaltyOffSaid = "Netočan odgovor ne oduzima bodove",
        less = "manje",
        more = "više",
        unnamed = "Bez naziva, soba se zove po tebi",
    )

internal val BosnianRoomSettingsStrings: RoomSettingsStrings =
    RoomSettingsStrings(
        entry = "Ulaz",
        questionsSaid = "{0} u igri",
        timeSaid = "{0} za svaki odgovor",
        veryEasySaid = "Pitanja koja skoro svako zna",
        easySaid = "Uglavnom laka, poneko teže",
        mediumSaid = "Mješavina za većinu",
        hardSaid = "Uglavnom teška pitanja",
        playersSaid = "Do {0} igrača",
        publicSaid = "Soba je na listi, svako može ući",
        privateSaid = "Ulazi se samo s kodom",
        penaltyOnSaid = "Netačan odgovor oduzima bodove",
        penaltyOffSaid = "Netačan odgovor ne oduzima bodove",
        less = "manje",
        more = "više",
        unnamed = "Bez naziva, soba se zove po tebi",
    )

internal val SlovenianRoomSettingsStrings: RoomSettingsStrings =
    RoomSettingsStrings(
        entry = "Vstop",
        questionsSaid = "{0} v igri",
        timeSaid = "{0} za vsak odgovor",
        veryEasySaid = "Vprašanja, ki jih ve skoraj vsak",
        easySaid = "Večinoma lahka, nekaj težjih",
        mediumSaid = "Mešanica za večino",
        hardSaid = "Večinoma težka vprašanja",
        playersSaid = "Do {0} igralcev",
        publicSaid = "Soba je na seznamu, vsak lahko vstopi",
        privateSaid = "Vstop samo s kodo",
        penaltyOnSaid = "Napačen odgovor odšteje točke",
        penaltyOffSaid = "Napačen odgovor ne odšteje točk",
        less = "manj",
        more = "več",
        unnamed = "Brez imena se soba imenuje po tebi",
    )

internal val MacedonianRoomSettingsStrings: RoomSettingsStrings =
    RoomSettingsStrings(
        entry = "Влез",
        questionsSaid = "{0} во игра",
        timeSaid = "{0} за секој одговор",
        veryEasySaid = "Прашања што речиси секој ги знае",
        easySaid = "Главно лесни, по некое потешко",
        mediumSaid = "Мешавина за повеќето",
        hardSaid = "Главно тешки прашања",
        playersSaid = "До {0} играчи",
        publicSaid = "Собата е на листата, секој може да влезе",
        privateSaid = "Се влегува само со код",
        penaltyOnSaid = "Погрешен одговор одзема поени",
        penaltyOffSaid = "Погрешен одговор не одзема поени",
        less = "помалку",
        more = "повеќе",
        unnamed = "Без име, собата се вика по тебе",
    )
