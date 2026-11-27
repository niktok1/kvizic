package io.ntole.kvizic.server.shop

import io.ntole.kvizic.core.player.Avatars
import io.ntole.kvizic.core.player.ShopItemDto
import io.ntole.kvizic.core.player.ShopItemKind
import io.ntole.kvizic.core.region.ContentLanguages

/**
 * What the shop sells, in the order it shows them: frames and host crowns, each a ladder from iron to diamond,
 * and the predators, all drawn by the client, which hides an item it has no drawing for, so a new one goes on
 * sale once a release draws it. `SHOP_PRICES` sets any price on a server ([priced]).
 */
object Shop {
    private val TITLE_LANGUAGES = ContentLanguages.ALL + "en"
    private val TIERS = listOf("iron", "bronze", "silver", "gold", "platinum", "diamond")

    val ITEMS: List<ShopItemDto> =
        TIERS.zip(listOf(100, 250, 500, 1_000, 2_000, 4_000)) { tier, price ->
            ShopItemDto("frame.$tier", ShopItemKind.FRAME, price)
        } +
            TIERS.zip(listOf(80, 200, 400, 800, 1_600, 3_200)) { tier, price ->
                ShopItemDto("crown.$tier", ShopItemKind.CROWN, price)
            } +
            Avatars.SHOP.zip(listOf(300, 400, 500, 600, 700, 800)) { id, price ->
                ShopItemDto(id, ShopItemKind.AVATAR, price)
            }

    /** The titles, no longer sold (the owner, 2026-11-27: earned, not bought); one bought before is still worn. */
    val TITLES: List<ShopItemDto> =
        listOf(
            title(
                "title.lucky",
                30,
                "Срећна рука",
                "Sretna ruka",
                "Sretna ruka",
                "Srečna roka",
                "Среќна рака",
                "Lucky hand",
            ),
            title("title.finger", 40, "Брзи прст", "Brzi prst", "Brzi prst", "Hitri prst", "Брз прст", "Fast finger"),
            title(
                "title.bookworm",
                50,
                "Књишки мољац",
                "Knjiški moljac",
                "Knjiški moljac",
                "Knjižni molj",
                "Книжен молец",
                "Bookworm",
            ),
            title("title.brain", 60, "Мозак", "Mozak", "Mozak", "Možgani", "Мозок", "Brain"),
            title("title.knowall", 80, "Свезналица", "Sveznalica", "Sveznalica", "Vseved", "Сезнајко", "Know-it-all"),
            title("title.star", 100, "Звезда", "Zvijezda", "Zvijezda", "Zvezda", "Ѕвезда", "Star"),
            title(
                "title.encyclopedia",
                120,
                "Енциклопедија",
                "Enciklopedija",
                "Enciklopedija",
                "Enciklopedija",
                "Енциклопедија",
                "Encyclopedia",
            ),
            title("title.legend", 150, "Легенда", "Legenda", "Legenda", "Legenda", "Легенда", "Legend"),
        )

    private val byId = (ITEMS + TITLES).associateBy { it.id }

    /** The item [id], sold or a title, or null for none. */
    fun item(id: String): ShopItemDto? = byId[id]

    /** [ITEMS] with [prices] in place of their own, by id. An id the shop does not sell fails at boot. */
    fun priced(prices: Map<String, Int>): List<ShopItemDto> {
        val unknown = prices.keys - ITEMS.map { it.id }.toSet()
        require(unknown.isEmpty()) { "SHOP_PRICES names what the shop does not sell: $unknown" }
        return ITEMS.map { item -> prices[item.id]?.let { item.copy(price = it) } ?: item }
    }

    /** The words of the title [id] for a reader of the content language [lang], the nearest there are; none for none. */
    fun titleIn(
        id: String,
        lang: String,
    ): String {
        val names = item(id)?.takeIf { it.kind == ShopItemKind.TITLE }?.names ?: return ""
        return ContentLanguages.chain(lang).firstNotNullOfOrNull { names[it] }.orEmpty()
    }

    private fun title(
        id: String,
        price: Int,
        vararg names: String,
    ) = ShopItemDto(id, ShopItemKind.TITLE, price, TITLE_LANGUAGES.zip(names).toMap())
}
