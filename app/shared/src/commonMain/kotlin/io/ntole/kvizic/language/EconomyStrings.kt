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
    /** How coins are earned, under the shop's balance: words that stay true whatever a game pays. */
    val howToEarn: String,
    /** The headings of the shop's kinds of items. */
    val frames: String,
    val titles: String,
    val animals: String,
    /** The button that puts an item of the player's on, and the one that takes it off. */
    val wear: String,
    val takeOff: String,
    /** An item the player owns, and one they wear, to a screen reader. */
    val owned: String,
    val worn: String,
    /** Under a price the player cannot pay yet: how many coins they lack, `{0}`. */
    val missing: String,
    /** Each frame's name, in the order `FrameArt.DRAWN` draws them, and each animal the shop sells, as `Avatars.SHOP`. */
    val frameNames: List<String>,
    val animalNames: List<String>,
) {
    /** These strings with [transform] applied to every one of them, as [Strings.map] asks. */
    internal fun map(transform: (String) -> String): EconomyStrings =
        EconomyStrings(
            shop = transform(shop),
            coins = coins.map(transform),
            howToEarn = transform(howToEarn),
            frames = transform(frames),
            titles = transform(titles),
            animals = transform(animals),
            wear = transform(wear),
            takeOff = transform(takeOff),
            owned = transform(owned),
            worn = transform(worn),
            missing = transform(missing),
            frameNames = frameNames.map(transform),
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
        frames = "Рамови",
        titles = "Титуле",
        animals = "Животиње",
        wear = "Стави",
        takeOff = "Скини",
        owned = "Твоје",
        worn = "Носиш",
        missing = "Недостаје још {0}",
        frameNames = listOf("Двоструки рам", "Испрекидани рам", "Сијалице", "Звездице", "Зраци", "Ловоров венац"),
        animalNames = listOf("Орао", "Вепар", "Дабар", "Шакал"),
    )

internal val CroatianEconomyStrings: EconomyStrings =
    EconomyStrings(
        shop = "Dućan",
        coins = Plural(one = "{0} dukat", two = "{0} dukata", few = "{0} dukata", many = "{0} dukata"),
        howToEarn = "Dukate zarađuješ u igrama u sobi.",
        frames = "Okviri",
        titles = "Titule",
        animals = "Životinje",
        wear = "Stavi",
        takeOff = "Skini",
        owned = "Tvoje",
        worn = "Nosiš",
        missing = "Nedostaje još {0}",
        frameNames =
            listOf("Dvostruki okvir", "Isprekidani okvir", "Žarulje", "Zvjezdice", "Zrake", "Lovorov vijenac"),
        animalNames = listOf("Orao", "Vepar", "Dabar", "Čagalj"),
    )

internal val BosnianEconomyStrings: EconomyStrings =
    EconomyStrings(
        shop = "Dućan",
        coins = Plural(one = "{0} dukat", two = "{0} dukata", few = "{0} dukata", many = "{0} dukata"),
        howToEarn = "Dukate zarađuješ u igrama u sobi.",
        frames = "Okviri",
        titles = "Titule",
        animals = "Životinje",
        wear = "Stavi",
        takeOff = "Skini",
        owned = "Tvoje",
        worn = "Nosiš",
        missing = "Nedostaje još {0}",
        frameNames =
            listOf("Dvostruki okvir", "Isprekidani okvir", "Sijalice", "Zvjezdice", "Zrake", "Lovorov vijenac"),
        animalNames = listOf("Orao", "Vepar", "Dabar", "Šakal"),
    )

internal val SlovenianEconomyStrings: EconomyStrings =
    EconomyStrings(
        shop = "Trgovina",
        coins = Plural(one = "{0} cekin", two = "{0} cekina", few = "{0} cekini", many = "{0} cekinov"),
        howToEarn = "Cekine zaslužiš v igrah v sobi.",
        frames = "Okvirji",
        titles = "Nazivi",
        animals = "Živali",
        wear = "Nadeni",
        takeOff = "Snemi",
        owned = "Tvoje",
        worn = "Nosiš",
        missing = "Manjka še {0}",
        frameNames = listOf("Dvojni okvir", "Črtkani okvir", "Žarnice", "Zvezdice", "Žarki", "Lovorov venec"),
        animalNames = listOf("Orel", "Merjasec", "Bober", "Šakal"),
    )

internal val MacedonianEconomyStrings: EconomyStrings =
    EconomyStrings(
        shop = "Дуќан",
        coins = Plural(one = "{0} дукат", two = "{0} дукати", few = "{0} дукати", many = "{0} дукати"),
        howToEarn = "Дукати заработуваш во игри во соба.",
        frames = "Рамки",
        titles = "Титули",
        animals = "Животни",
        wear = "Стави",
        takeOff = "Симни",
        owned = "Твое",
        worn = "Носиш",
        missing = "Недостасуваат уште {0}",
        frameNames = listOf("Двојна рамка", "Испрекината рамка", "Сијалички", "Ѕвездички", "Зраци", "Ловоров венец"),
        animalNames = listOf("Орел", "Дива свиња", "Дабар", "Шакал"),
    )

internal val EnglishEconomyStrings: EconomyStrings =
    EconomyStrings(
        shop = "Shop",
        coins = Plural(one = "{0} ducat", two = "{0} ducats", few = "{0} ducats", many = "{0} ducats"),
        howToEarn = "You earn ducats in room games.",
        frames = "Frames",
        titles = "Titles",
        animals = "Animals",
        wear = "Wear",
        takeOff = "Take off",
        owned = "Yours",
        worn = "Wearing",
        missing = "{0} more needed",
        frameNames = listOf("Double frame", "Dashed frame", "Bulbs", "Stars", "Rays", "Laurel wreath"),
        animalNames = listOf("Eagle", "Wild boar", "Beaver", "Jackal"),
    )
