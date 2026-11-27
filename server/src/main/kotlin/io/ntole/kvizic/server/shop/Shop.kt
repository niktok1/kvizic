package io.ntole.kvizic.server.shop

import io.ntole.kvizic.core.player.Avatars
import io.ntole.kvizic.core.player.ShopItemDto
import io.ntole.kvizic.core.player.ShopItemKind
import io.ntole.kvizic.core.region.ContentLanguages

/**
 * What the shop sells, in the order it shows them: frames and avatars, which the client draws, and titles,
 * words in every content language and English. A price or a title changes here with no client release; a
 * client hides an item it has no drawing for, so a new frame or avatar goes on sale once a release draws it.
 */
object Shop {
    private val TITLE_LANGUAGES = ContentLanguages.ALL + "en"

    val ITEMS: List<ShopItemDto> =
        listOf(
            frame("frame.double", 50),
            frame("frame.dashes", 100),
            frame("frame.bulbs", 200),
            frame("frame.stars", 300),
            frame("frame.rays", 500),
            frame("frame.laurel", 800),
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
        ) + Avatars.SHOP.zip(listOf(300, 350, 400, 500)) { id, price -> ShopItemDto(id, ShopItemKind.AVATAR, price) }

    private val byId = ITEMS.associateBy { it.id }

    /** The item [id], or null for one the shop does not sell. */
    fun item(id: String): ShopItemDto? = byId[id]

    /** The words of the title [id] for a reader of the content language [lang], the nearest there are; none for none. */
    fun titleIn(
        id: String,
        lang: String,
    ): String {
        val names = item(id)?.takeIf { it.kind == ShopItemKind.TITLE }?.names ?: return ""
        return ContentLanguages.chain(lang).firstNotNullOfOrNull { names[it] }.orEmpty()
    }

    private fun frame(
        id: String,
        price: Int,
    ) = ShopItemDto(id, ShopItemKind.FRAME, price)

    private fun title(
        id: String,
        price: Int,
        vararg names: String,
    ) = ShopItemDto(id, ShopItemKind.TITLE, price, TITLE_LANGUAGES.zip(names).toMap())
}
