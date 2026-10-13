package io.ntole.kvizic.server.player

import io.ntole.kvizic.core.api.KvizicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DisplayNamesTest {
    @Test
    fun `Macedonian letters fold to Latin, capitals too`() {
        assertEquals("gkdz gkdz", DisplayNames.fold("ѓќѕ ЃЌЅ"))
        assertEquals("ei ei", DisplayNames.fold("ѐѝ ЀЍ"))
        assertEquals("gk", DisplayNames.fold("ѓќ"), "decomposed, as a keyboard may type them")
        assertEquals("jljnjdz", DisplayNames.fold("јљњџ"), "as in Serbian")
    }

    @Test
    fun `Croatian, Bosnian, Slovenian and Montenegrin diacritics drop`() {
        assertEquals("cc cc ss zz djdj dzdz ss zz", DisplayNames.fold("Čč Ćć Šš Žž Đđ Dždž Śś Źź"))
        assertEquals("s z", DisplayNames.fold("С́ з́"), "Montenegrin Cyrillic's two")
    }

    @Test
    fun `a name folds alike in either script`() {
        listOf("Ђорђе Ђокић", "Đorđe Đokić", "Djordje Djokic", "DJORDJE ĐOKIĆ").forEach { name ->
            assertEquals("djordje djokic", DisplayNames.fold(name), name)
        }
        assertEquals(DisplayNames.fold("Џеко Љиљана Њего"), DisplayNames.fold("Džeko Ljiljana Njego"))
    }

    @Test
    fun `compatibility forms read as their letters`() {
        assertEquals("kviz ljubav dzep", DisplayNames.fold("ＫＶＩＺ ǈubav ǅep"))
    }

    @Test
    fun `a service's name is cleaned of what could disguise it and held to its length`() {
        assertEquals("Marko Petrović", DisplayNames.clean("  Marko \t\n Petrović  "))
        assertEquals("admin", DisplayNames.clean("ad​min"), "zero-width characters go")
        assertEquals("Ana", DisplayNames.clean("‮Ana‬"), "bidi overrides go")
        assertEquals("Ана", DisplayNames.clean("Ана\u0000"), "control characters go")
        assertEquals(KvizicApi.Limits.MAX_DISPLAY_NAME_LENGTH, DisplayNames.clean("я".repeat(40))?.length)
        assertEquals("Ćevapčić 🔥", DisplayNames.clean("Ćevapčić 🔥"))
        assertEquals("é", DisplayNames.clean("é"), "composed")
        assertNull(DisplayNames.clean("   "))
        assertNull(DisplayNames.clean(null))
    }

    @Test
    fun `a name with a blocked word keeps the nickname, in either script or disguised`() {
        listOf("Kurac123", "КУРАЦ", "fuck_you", "sh1t", "Ј3бем те", "Pička", "Hitler88").forEach { name ->
            assertNull(DisplayNames.clean(name), name)
        }
        listOf("Kura", "Bitola", "Jelena", "Шампион", "Pikachu").forEach { name ->
            assertEquals(name, DisplayNames.clean(name), name)
        }
    }

    @Test
    fun `slurs are caught in Croatian, Bosnian, Slovenian and Macedonian as in Serbian`() {
        val slurs =
            mapOf(
                "hr" to names("Šupak, šupčino, Seronja, Buzerant, Kurba, Pizdun, Jebač, Pederčina, Kurčina"),
                "bs" to names("Balija, balije, Pezevenk, Jebem ti, Pička ti materina, Čifut, Kurvo"),
                "sl" to names("Fukač, fukam, Fuk, Kurec, Drek, Buzi, Čefur, čefurji, Kurbo, Pizda, Jebemti"),
                "mk" to names("Ебам ти, Ебач, Ебење, Ебига, Курот, Пичка, Курва, Кучка, Шупак, Пезевенк, Педер"),
                "ethnic" to names("Šiptar, Шиптари, Čivut, Crnčuga, Балија, balijetina, Четник, Ustaša"),
                "Nazism" to names("Nazi, Nazisti, Nacista, nacizam, Нацист, nacizem, Sieg Heil, SiegHeil, Hitler"),
            )
        val missed = slurs.mapValues { (_, words) -> words.filterNot(DisplayNames::isBlocked) }
        assertEquals(emptyMap(), missed.filterValues { it.isNotEmpty() })
    }

    @Test
    fun `a slur is caught disguised`() {
        names(
            "Š1ptar, ＰＩＺＤＡ, Droǉa, Буз1, Kurb4, N4z1, nazi88, ЕБАМ_ТЕ, П1чка, Balija1, Kurba5, Nazi14, Fuk1, Drek3",
        ).forEach { name ->
            assertNull(DisplayNames.clean(name), name)
        }
    }

    @Test
    fun `no common name from the six countries is blocked`() {
        val everyone = SERBIA + CROATIA + BOSNIA + SLOVENIA + NORTH_MACEDONIA + MONTENEGRO
        assertEquals(emptyList(), everyone.filter(DisplayNames::isBlocked))
    }

    @Test
    fun `no country or capital of the six is blocked, in any of their languages`() {
        assertEquals(emptyList(), PLACES.filter(DisplayNames::isBlocked))
    }

    @Test
    fun `a word that begins like a slur but is not one stays`() {
        assertEquals(emptyList(), NEAR_MISSES.filter(DisplayNames::isBlocked))
    }

    @Test
    fun `no generated nickname is blocked`() {
        assertEquals(emptyList(), Nicknames.all().filter(DisplayNames::isBlocked))
    }

    private companion object {
        fun names(list: String): List<String> = list.split(',').map { it.trim() }.filter { it.isNotEmpty() }

        // About fifty first names and surnames each, as their owners write them: Serbs in Cyrillic, Croats,
        // Bosniaks, Slovenes and Montenegrins in Latin, Macedonians in Macedonian, Albanians in Albanian.
        val SERBIA =
            names(
                """
                Никола, Марко, Стефан, Лука, Милош, Душан, Немања, Александар, Јован, Петар, Урош, Вук, Огњен,
                Ђорђе, Драган, Небојша, Срђан, Милица, Јелена, Марија, Тијана, Невена, Мирјана, Теодора, Анђела,
                Љиљана, Јовановић, Петровић, Николић, Марковић, Ђорђевић, Стојановић, Илић, Станковић, Павловић,
                Милошевић, Поповић, Ђурић, Пешић, Ђокић, Тодоровић, Митровић, Живковић, Савић, Ристић, Лазић,
                Јанковић, Обрадовић, Ћирић, Пантелић
                """,
            )
        val CROATIA =
            names(
                """
                Ivan, Luka, Josip, Ante, Tomislav, Stjepan, Franjo, Hrvoje, Krešimir, Domagoj, Jakov, Dražen,
                Željko, Ivica, Duje, Zvonimir, Kata, Ljubica, Snježana, Lucija, Dora, Josipa, Antonija, Nikolina,
                Horvat, Kovačević, Babić, Marić, Jurić, Kovačić, Knežević, Božić, Blažević, Grgić, Pavić, Radić,
                Šarić, Lovrić, Perković, Bošnjak, Jukić, Barišić, Vrdoljak, Šimić, Ćosić, Lončar, Šimunović,
                Modrić, Mandžukić, Đurđević, Picula
                """,
            )
        val BOSNIA =
            names(
                """
                Amar, Adnan, Emir, Haris, Kenan, Tarik, Edin, Mirza, Admir, Nermin, Senad, Mehmed, Alija, Nedžad,
                Safet, Zlatan, Dženan, Harun, Faruk, Nazif, Nazim, Amina, Lejla, Emina, Aida, Ajla, Selma, Merima,
                Azra, Lamija, Ajša, Hatidža, Nejra, Nazifa, Hodžić, Hadžić, Čengić, Delić, Demirović, Mehmedović,
                Salihović, Begić, Muratović, Halilović, Ibrahimović, Imamović, Avdić, Smajić, Selimović, Mešić,
                Husić, Mustafić, Softić, Kurtović, Kurbegović, Kurić, Džeko, Pjanić, Spahić, Младен, Жељка,
                Остојић, Шкорић
                """,
            )
        val SLOVENIA =
            names(
                """
                Franc, Janez, Jože, Andrej, Tomaž, Aleš, Rok, Boštjan, Matjaž, Gregor, Miha, Nejc, Žiga, Jure,
                Primož, Blaž, Klemen, Tilen, Gašper, Anže, Jaka, Mojca, Špela, Urška, Tjaša, Ajda, Zala, Neža,
                Živa, Metka, Polona, Novak, Kovačič, Krajnc, Zupančič, Potočnik, Mlakar, Kos, Vidmar, Golob,
                Kralj, Korošec, Bizjak, Hribar, Kavčič, Rozman, Oblak, Žagar, Hočevar, Košir, Klemenčič, Zajc,
                Pirc, Kuhar, Erjavec, Jereb, Pušnik, Pogačnik, Dončić, Prevc, Kurnik, Šinkovec, Čeh
                """,
            )
        val NORTH_MACEDONIA =
            names(
                """
                Александар, Христијан, Ѓорѓи, Ѓорге, Ѓоко, Благоја, Трајче, Кире, Ристе, Методија, Кирил, Горан,
                Дејан, Сашо, Елена, Билјана, Снежана, Катерина, Ангела, Магдалена, Ивана, Весна, Ќамил,
                Николовски, Стојановски, Трајковски, Јовановски, Ѓорѓиевски, Ристовски, Димитровски, Стојановска,
                Пандев, Николова, Ангеловски, Костовски, Митревски, Ѓуровски, Велјановски, Кочоски, Талевски,
                Наумоски, Arben, Besnik, Agim, Driton, Blerim, Teuta, Ahmeti, Xhaferi, Osmani, Rexhepi, Bajrami,
                Sulejmani
                """,
            )
        val MONTENEGRO =
            names(
                """
                Danilo, Milo, Vesko, Branko, Božidar, Radovan, Andrija, Balša, Vasilije, Vukašin, Predrag,
                Radomir, Jovana, Ljiljana, Tamara, Maša, Nađa, Sofija, Dunja, Kristina, Vuković, Radović,
                Đukanović, Vujović, Bulatović, Šćepanović, Lekić, Perović, Raičević, Vujošević, Đurović, Vučinić,
                Pejović, Mugoša, Drekalović, Vlahović, Jovetić, Savićević, Mijatović, Šekularac, Kalezić,
                Medenica, Bećirović, Balijagić, Gjonaj, Dedaj, Вукашин, Радомир, Шћепановић, Ђукановић
                """,
            )
        val PLACES =
            names(
                """
                Србија, Srbija, Serbia, Београд, Beograd, Belgrade, Hrvatska, Хрватска, Hrvaška, Croatia, Zagreb,
                Загреб, Bosna i Hercegovina, Bosna in Hercegovina, Босна и Херцеговина, Bosnia and Herzegovina,
                Sarajevo, Сарајево, Сараево, Slovenija, Словенија, Slovenia, Ljubljana, Љубљана,
                Северна Македонија, Sjeverna Makedonija, Severna Makedonija, North Macedonia, Македонија, Скопје,
                Skopje, Скопље, Crna Gora, Črna gora, Црна Гора, Montenegro, Podgorica, Подгорица
                """,
            )

        // What starts like a blocked word yet is none: a title (naziv), a feast (Kurban), Courbet (Курбе), a
        // crank, a din, ebony, a flower, tapping, kids, and the surnames that begin so.
        val NEAR_MISSES =
            names(
                """
                Назив, Bez naziva, Kurban-bajram, Kurbalija, Курбе, kurbla, dreka, Siegfried, Kurent, ebanovina,
                Crnjanski, Arnautović, fuksija, kuckanje, klinci, Fukuoka
                """,
            )
    }
}
