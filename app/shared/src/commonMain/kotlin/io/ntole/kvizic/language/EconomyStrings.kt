package io.ntole.kvizic.language

import io.ntole.kvizic.design.avatar.AvatarArt

/**
 * The words of the coins and the shop, as [Strings.economy]: made in each language as the rest of [Strings] is,
 * and checked by the same tests. A coin is a ducat, never "points", which are a game's score.
 */
data class EconomyStrings(
    /** The shop: its screen's title, and Home's way there to a screen reader. */
    val shop: String,
    /** A number of coins. */
    val coins: Plural,
    /** How coins are earned, under the shop's stage: words that stay true whatever a game pays. */
    val howToEarn: String,
    /** The shop's tabs: frames, host crowns and beasts, the predators it sells. */
    val frames: String,
    val crowns: String,
    val animals: String,
    /** The button that puts an item of the player's on, and the one that takes it off. */
    val wear: String,
    val takeOff: String,
    /** An item the player owns, and one they wear, to a screen reader. */
    val owned: String,
    val worn: String,
    /** Under a price the player cannot pay yet: how many coins they lack, `{0}`. */
    val missing: String,
    /** The plain frame and the plain crown, which everybody has. */
    val plainFrame: String,
    val plainCrown: String,
    /** The tiers of frames and crowns, from iron to diamond, in the order `FrameArt.DRAWN` and `CrownArt.DRAWN` draw them. */
    val tiers: List<String>,
    /** Each animal the shop sells, as `AvatarArt.SHOP`. */
    val animalNames: List<String>,
) {
    /** These strings with [transform] applied to every one of them, as [Strings.map] asks. */
    internal fun map(transform: (String) -> String): EconomyStrings =
        EconomyStrings(
            shop = transform(shop),
            coins = coins.map(transform),
            howToEarn = transform(howToEarn),
            frames = transform(frames),
            crowns = transform(crowns),
            animals = transform(animals),
            wear = transform(wear),
            takeOff = transform(takeOff),
            owned = transform(owned),
            worn = transform(worn),
            missing = transform(missing),
            plainFrame = transform(plainFrame),
            plainCrown = transform(plainCrown),
            tiers = tiers.map(transform),
            animalNames = animalNames.map(transform),
        )
}

/** The name of the avatar [id], free or the shop's, to a screen reader: the id itself for one this build cannot name. */
internal fun Strings.avatarName(id: String): String =
    if (id in AvatarArt.SHOP) {
        economy.animalNames.getOrNull(AvatarArt.SHOP.indexOf(id))
    } else {
        game.avatarNames.getOrNull(AvatarArt.DRAWN.indexOf(id))
    } ?: id

/** The source text, written by hand. */
internal val SerbianCyrillicEconomyStrings: EconomyStrings =
    EconomyStrings(
        shop = "Дућан",
        coins = Plural(one = "{0} дукат", two = "{0} дуката", few = "{0} дуката", many = "{0} дуката"),
        howToEarn = "Дукате зарађујеш у играма у соби.",
        frames = "Оквири",
        crowns = "Круне",
        animals = "Звери",
        wear = "Стави",
        takeOff = "Скини",
        owned = "Твоје",
        worn = "Носиш",
        missing = "Недостаје још {0}",
        plainFrame = "Обичан",
        plainCrown = "Обична",
        tiers = listOf("Гвожђе", "Бронза", "Сребро", "Злато", "Платина", "Дијамант"),
        animalNames = listOf("Орао", "Пантер", "Крокодил", "Ајкула", "Тигар", "Лав"),
    )

internal val CroatianEconomyStrings: EconomyStrings =
    EconomyStrings(
        shop = "Dućan",
        coins = Plural(one = "{0} dukat", two = "{0} dukata", few = "{0} dukata", many = "{0} dukata"),
        howToEarn = "Dukate zarađuješ u igrama u sobi.",
        frames = "Okviri",
        crowns = "Krune",
        animals = "Zvijeri",
        wear = "Stavi",
        takeOff = "Skini",
        owned = "Tvoje",
        worn = "Nosiš",
        missing = "Nedostaje još {0}",
        plainFrame = "Običan",
        plainCrown = "Obična",
        tiers = listOf("Željezo", "Bronca", "Srebro", "Zlato", "Platina", "Dijamant"),
        animalNames = listOf("Orao", "Pantera", "Krokodil", "Morski pas", "Tigar", "Lav"),
    )

internal val BosnianEconomyStrings: EconomyStrings =
    EconomyStrings(
        shop = "Dućan",
        coins = Plural(one = "{0} dukat", two = "{0} dukata", few = "{0} dukata", many = "{0} dukata"),
        howToEarn = "Dukate zarađuješ u igrama u sobi.",
        frames = "Okviri",
        crowns = "Krune",
        animals = "Zvijeri",
        wear = "Stavi",
        takeOff = "Skini",
        owned = "Tvoje",
        worn = "Nosiš",
        missing = "Nedostaje još {0}",
        plainFrame = "Običan",
        plainCrown = "Obična",
        tiers = listOf("Željezo", "Bronza", "Srebro", "Zlato", "Platina", "Dijamant"),
        animalNames = listOf("Orao", "Panter", "Krokodil", "Ajkula", "Tigar", "Lav"),
    )

internal val SlovenianEconomyStrings: EconomyStrings =
    EconomyStrings(
        shop = "Trgovina",
        coins = Plural(one = "{0} cekin", two = "{0} cekina", few = "{0} cekini", many = "{0} cekinov"),
        howToEarn = "Cekine zaslužiš v igrah v sobi.",
        frames = "Okvirji",
        crowns = "Krone",
        animals = "Zveri",
        wear = "Nadeni",
        takeOff = "Snemi",
        owned = "Tvoje",
        worn = "Nosiš",
        missing = "Manjka še {0}",
        plainFrame = "Navaden",
        plainCrown = "Navadna",
        tiers = listOf("Železo", "Bron", "Srebro", "Zlato", "Platina", "Diamant"),
        animalNames = listOf("Orel", "Panter", "Krokodil", "Morski pes", "Tiger", "Lev"),
    )

internal val MacedonianEconomyStrings: EconomyStrings =
    EconomyStrings(
        shop = "Дуќан",
        coins = Plural(one = "{0} дукат", two = "{0} дукати", few = "{0} дукати", many = "{0} дукати"),
        howToEarn = "Дукати заработуваш во игри во соба.",
        frames = "Рамки",
        crowns = "Круни",
        animals = "Ѕверови",
        wear = "Стави",
        takeOff = "Симни",
        owned = "Твое",
        worn = "Носиш",
        missing = "Недостасуваат уште {0}",
        plainFrame = "Обична",
        plainCrown = "Обична",
        tiers = listOf("Железо", "Бронза", "Сребро", "Злато", "Платина", "Дијамант"),
        animalNames = listOf("Орел", "Пантер", "Крокодил", "Ајкула", "Тигар", "Лав"),
    )

internal val EnglishEconomyStrings: EconomyStrings =
    EconomyStrings(
        shop = "Shop",
        coins = Plural(one = "{0} ducat", two = "{0} ducats", few = "{0} ducats", many = "{0} ducats"),
        howToEarn = "You earn ducats in room games.",
        frames = "Frames",
        crowns = "Crowns",
        animals = "Beasts",
        wear = "Wear",
        takeOff = "Take off",
        owned = "Yours",
        worn = "Wearing",
        missing = "{0} more needed",
        plainFrame = "Plain",
        plainCrown = "Plain",
        tiers = listOf("Iron", "Bronze", "Silver", "Gold", "Platinum", "Diamond"),
        animalNames = listOf("Eagle", "Panther", "Crocodile", "Shark", "Tiger", "Lion"),
    )
