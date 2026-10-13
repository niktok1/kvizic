package io.ntole.kvizic.server.question

import io.ntole.kvizic.core.question.Difficulty
import io.ntole.kvizic.core.question.EditQuestionRequest
import io.ntole.kvizic.core.question.QuestionDraftDto
import io.ntole.kvizic.core.question.QuestionLocalizationDto
import io.ntole.kvizic.core.question.QuestionStatus
import io.ntole.kvizic.core.question.QuestionTextDto
import io.ntole.kvizic.core.region.Country
import io.ntole.kvizic.server.db.Db
import io.ntole.kvizic.server.db.QuestionCountries
import io.ntole.kvizic.server.db.QuestionOptionTranslations
import io.ntole.kvizic.server.db.QuestionTranslations
import io.ntole.kvizic.server.db.Questions
import io.ntole.kvizic.server.db.appTables
import io.ntole.kvizic.server.db.connectH2
import io.ntole.kvizic.server.db.h2Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.Connection
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A question's countries and translations in the bank: imported, localized, edited past, exported and deleted. */
class QuestionRegionsStoreTest {
    private val database = bank()
    private val db = Db(database)

    private fun bank(): Database =
        connectH2(h2Url("kvizic-regions-${UUID.randomUUID()}"), Connection.TRANSACTION_READ_COMMITTED).also {
            transaction(it) {
                SchemaUtils.create(*appTables)
                BankFixtures.topic("GEOGRAPHY")
            }
        }

    private fun words(
        tag: String,
        key: String,
        explanation: String? = null,
        options: List<String> = listOf("Prvi $tag $key", "Drugi $tag $key", "Treći $tag $key"),
    ) = QuestionTextDto("Pitanje $tag $key?", options, explanation)

    private fun draft(
        key: String,
        countries: List<Country> = emptyList(),
        translations: Map<String, QuestionTextDto> = emptyMap(),
        explanation: String? = null,
    ) = QuestionDraftDto(
        key = key,
        text = "Питање $key?",
        options = listOf("Први $key", "Други $key", "Трећи $key"),
        correct = 2,
        topics = listOf("GEOGRAPHY"),
        difficulty = Difficulty.HARD,
        explanation = explanation,
        countries = countries,
        translations = translations,
    )

    private fun import(
        vararg drafts: QuestionDraftDto,
        into: Database = database,
        now: Long = 10,
        status: QuestionStatus = QuestionStatus.DRAFT,
    ) = transaction(into) { QuestionStore.import("b", drafts.toList(), status, setOf("GEOGRAPHY"), now) }

    private fun localize(
        localization: QuestionLocalizationDto,
        now: Long,
    ) = transaction(database) { QuestionStore.localize(localization, now) }

    private fun idOf(key: String) =
        transaction(database) {
            Questions.select(Questions.id).where { Questions.importKey eq key }.single()[Questions.id]
        }

    private fun exported(
        from: Database = database,
        statuses: Set<QuestionStatus> = QuestionStatus.entries.toSet(),
    ) = transaction(from) { QuestionStore.export(statuses) }

    /** What the bank holds of a question's countries and translations, every column of every row. */
    private fun rows(from: Database = database): List<List<Any?>> =
        transaction(from) {
            QuestionCountries
                .selectAll()
                .orderBy(QuestionCountries.questionId to SortOrder.ASC, QuestionCountries.country to SortOrder.ASC)
                .map { listOf(it[QuestionCountries.questionId], it[QuestionCountries.country]) } +
                QuestionTranslations
                    .selectAll()
                    .orderBy(
                        QuestionTranslations.questionId to SortOrder.ASC,
                        QuestionTranslations.language to SortOrder.ASC,
                    ).map { row -> QuestionTranslations.columns.map { row[it] } } +
                QuestionOptionTranslations
                    .selectAll()
                    .orderBy(
                        QuestionOptionTranslations.questionId to SortOrder.ASC,
                        QuestionOptionTranslations.language to SortOrder.ASC,
                        QuestionOptionTranslations.slot to SortOrder.ASC,
                    ).map { row -> QuestionOptionTranslations.columns.map { row[it] } }
        }

    /** The translation of [key] into [tag]: its text, the revision it was made from, and when it was set. */
    private fun translation(
        key: String,
        tag: String,
    ): Triple<String, Int, Long>? =
        transaction(database) {
            QuestionTranslations
                .selectAll()
                .where { QuestionTranslations.questionId eq idOf(key) }
                .firstOrNull { it[QuestionTranslations.language] == tag }
                ?.let {
                    Triple(
                        it[QuestionTranslations.text],
                        it[QuestionTranslations.sourceRevision],
                        it[QuestionTranslations.updatedAt],
                    )
                }
        }

    @Test
    fun `an import writes a question's countries and translations, made from its first revision`() {
        val result =
            import(
                draft(
                    "a",
                    countries = listOf(Country.BA, Country.HR),
                    translations =
                        mapOf(
                            "hr" to words("hr", "a", "Objašnjenje hr."),
                            "bs" to words("bs", "a", "Objašnjenje bs."),
                        ),
                    explanation = "Објашњење.",
                ),
                draft("b"),
            )
        assertEquals(listOf("a", "b"), result.created)

        val (a, b) = exported().sortedBy { it.key }
        assertEquals(listOf(Country.HR, Country.BA), a.countries, "in the countries' order")
        assertEquals(listOf("hr", "bs"), a.translations.keys.toList(), "in the languages' order")
        assertEquals(words("hr", "a", "Objašnjenje hr."), a.translations["hr"])
        assertEquals(words("bs", "a", "Objašnjenje bs."), a.translations["bs"])
        assertEquals(Triple("Pitanje hr a?", 1, 10L), translation("a", "hr"))
        assertEquals(emptyList(), b.countries, "common")
        assertEquals(emptyMap(), b.translations)
    }

    @Test
    fun `an import refuses a draft whose translation or countries break the rules, writing nothing of it`() {
        val result =
            import(
                draft("a", translations = mapOf("hr" to words("hr", "a", options = listOf("Jedan", "Dva")))),
                draft("b", countries = listOf(Country.SI, Country.SI)),
                draft("c", translations = mapOf("sr-Cyrl" to words("sr-Cyrl", "c"))),
                draft("d", translations = mapOf("hr" to words("hr", "d"))),
            )
        assertEquals(listOf("d"), result.created)
        assertEquals(
            mapOf(
                "a" to "translation hr: 2 answers, not the source's 3",
                "b" to "countries: SI twice",
                "c" to "translation sr-Cyrl: the source's own language",
            ),
            result.refused.associate { it.key to it.reason },
        )
        assertEquals(1, rows().count { it.size == QuestionTranslations.columns.size }, "d's alone")
    }

    @Test
    fun `a localization replaces the countries and the languages it names, and keeps the rest`() {
        import(
            draft(
                "a",
                countries = listOf(Country.RS),
                translations =
                    mapOf(
                        "hr" to words("hr", "a"),
                        "sl" to words("sl", "a"),
                    ),
            ),
        )
        val hrBefore = translation("a", "hr")

        val outcome =
            localize(
                QuestionLocalizationDto(
                    key = "a",
                    countries = listOf(Country.ME, Country.RS),
                    translations = mapOf("sl" to words("sl", "a2"), "mk" to words("mk", "a")),
                ),
                now = 20,
            )
        assertEquals(Localized.Updated, outcome)
        val a = exported().single()
        assertEquals(listOf(Country.RS, Country.ME), a.countries)
        assertEquals(listOf("hr", "sl", "mk"), a.translations.keys.toList())
        assertEquals(words("sl", "a2"), a.translations["sl"], "replaced")
        assertEquals(hrBefore, translation("a", "hr"), "left out, so kept, as it was")
        assertEquals(Triple("Pitanje sl a2?", 1, 20L), translation("a", "sl"))
        assertEquals(Triple("Pitanje mk a?", 1, 20L), translation("a", "mk"))

        localize(QuestionLocalizationDto(key = "a", translations = mapOf("hr" to words("hr", "a3"))), now = 30)
        assertEquals(listOf(Country.RS, Country.ME), exported().single().countries, "no countries keeps them")
        localize(QuestionLocalizationDto(key = "a", countries = emptyList()), now = 40)
        assertEquals(emptyList(), exported().single().countries, "none makes it common")
    }

    @Test
    fun `a localization finds a question by its key, else its id, and names one it cannot`() {
        import(draft("a"), draft("b"))
        val idOfB = idOf("b")
        assertEquals(Localized.Updated, localize(QuestionLocalizationDto(idOfB, listOf(Country.SI)), now = 20))
        assertEquals(listOf(Country.SI), exported().single { it.key == "b" }.countries)
        assertEquals(Localized.Unknown, localize(QuestionLocalizationDto("nope", listOf(Country.SI)), now = 20))
        assertEquals(Localized.Unknown, localize(QuestionLocalizationDto("no key", listOf(Country.SI)), now = 20))
        assertEquals(Localized.Unknown, localize(QuestionLocalizationDto("", listOf(Country.SI)), now = 20))
    }

    @Test
    fun `a localization refused in one part writes nothing of it`() {
        import(draft("a", translations = mapOf("hr" to words("hr", "a"))))
        val before = rows()
        val refused =
            localize(
                QuestionLocalizationDto(
                    key = "a",
                    countries = listOf(Country.HR),
                    translations = mapOf("bs" to words("bs", "a"), "en" to words("en", "a")),
                ),
                now = 20,
            )
        assertEquals(Localized.Refused("translation en: no such language"), refused)
        assertEquals(
            Localized.Refused("countries: UNKNOWN is no country"),
            localize(QuestionLocalizationDto("a", listOf(Country.UNKNOWN), mapOf("bs" to words("bs", "a"))), now = 20),
        )
        assertEquals(
            Localized.Refused("translation bs: an explanation, where the source has none"),
            localize(
                QuestionLocalizationDto("a", translations = mapOf("bs" to words("bs", "a", "Objašnjenje."))),
                now = 20,
            ),
        )
        assertEquals(before, rows())
    }

    @Test
    fun `the same localization twice changes nothing the second time`() {
        import(draft("a", translations = mapOf("hr" to words("hr", "a"))))
        val localization =
            QuestionLocalizationDto(
                key = "a",
                countries = listOf(Country.HR, Country.BA),
                translations = mapOf("hr" to words("hr", "a2"), "bs" to words("bs", "a")),
            )
        localize(localization, now = 20)
        val once = rows()
        assertEquals(Localized.Updated, localize(localization, now = 30))
        assertEquals(once, rows(), "updated_at included")
    }

    @Test
    fun `a localization works at any status, and moves neither the status nor the revision`() {
        import(draft("a"))
        val id = idOf("a")
        transaction(database) {
            QuestionStore.move(id, setOf(QuestionStatus.DRAFT), QuestionStatus.APPROVED, null, 11)
            QuestionStore.move(id, setOf(QuestionStatus.APPROVED), QuestionStatus.RETIRED, null, 12)
        }
        val before = transaction(database) { checkNotNull(QuestionStore.find(id)) }
        assertEquals(
            Localized.Updated,
            localize(
                QuestionLocalizationDto(
                    "a",
                    listOf(Country.MK),
                    mapOf("mk" to words("mk", "a")),
                ),
                20,
            ),
        )
        val after = transaction(database) { checkNotNull(QuestionStore.find(id)) }
        assertEquals(before, after)
        assertEquals(QuestionStatus.RETIRED, after.status)
        assertEquals(1, after.revision)
        assertEquals(listOf(Country.MK), exported().single().countries)
    }

    @Test
    fun `an edit leaves the translations in place, made from the revision before it`() {
        import(draft("a", countries = listOf(Country.HR), translations = mapOf("hr" to words("hr", "a"))))
        val id = idOf("a")
        val edited =
            transaction(database) {
                QuestionStore.edit(
                    EditQuestionRequest(
                        id = id,
                        revision = 1,
                        text = "Ново питање a?",
                        options = listOf("Први a", "Други a", "Трећи a"),
                        correct = 0,
                        topics = listOf("GEOGRAPHY"),
                    ),
                    setOf("GEOGRAPHY"),
                    20,
                )
            }
        assertEquals(2, edited.revision)
        assertEquals(Triple("Pitanje hr a?", 1, 10L), translation("a", "hr"), "stale: made from revision 1")
        assertEquals(listOf(Country.HR), exported().single().countries)
        assertEquals(words("hr", "a"), exported().single().translations["hr"], "a stale one is exported")

        localize(QuestionLocalizationDto("a", translations = mapOf("hr" to words("hr", "a"))), now = 30)
        assertEquals(Triple("Pitanje hr a?", 2, 30L), translation("a", "hr"), "sent again, it is the new revision's")
    }

    @Test
    fun `an export leaves out a translation its question no longer fits`() {
        import(draft("a", translations = mapOf("hr" to words("hr", "a"), "bs" to words("bs", "a"))))
        val id = idOf("a")
        transaction(database) {
            QuestionStore.edit(
                EditQuestionRequest(id, 1, "Питање a?", listOf("Први a", "Други a"), 0, listOf("GEOGRAPHY")),
                setOf("GEOGRAPHY"),
                20,
            )
        }
        assertEquals(emptyMap(), exported().single().translations, "three answers for the question's two")
        localize(
            QuestionLocalizationDto("a", translations = mapOf("bs" to words("bs", "a", options = listOf("A", "B")))),
            30,
        )
        assertEquals(
            listOf("bs"),
            exported()
                .single()
                .translations.keys
                .toList(),
        )
        assertEquals(
            listOf("A", "B"),
            exported()
                .single()
                .translations
                .getValue("bs")
                .options,
            "the third slot gone",
        )
    }

    @Test
    fun `an export imports into a fresh bank as the same bank`() {
        import(
            draft(
                "a",
                countries = listOf(Country.BA),
                translations =
                    mapOf(
                        "bs" to words("bs", "a", "Bs."),
                        "hr" to words("hr", "a", "Hr."),
                    ),
                explanation = "Ср.",
            ),
            draft("b"),
            draft(
                "c",
                translations =
                    mapOf(
                        "mk" to words("mk", "c"),
                        "sl" to words("sl", "c"),
                        "hr" to words("hr", "c"),
                        "bs" to words("bs", "c"),
                    ),
            ),
            status = QuestionStatus.APPROVED,
        )
        localize(
            QuestionLocalizationDto("b", listOf(Country.RS, Country.ME), mapOf("hr" to words("hr", "b"))),
            now = 20,
        )
        val export = exported()
        assertEquals(3, export.size)

        val fresh = bank()
        val again = import(*export.toTypedArray(), into = fresh, now = 30)
        assertEquals(listOf("a", "b", "c"), again.created.sorted())
        assertEquals(export.sortedBy { it.key }, exported(fresh).sortedBy { it.key })
    }

    @Test
    fun `deleting a question deletes its countries and translations`() {
        import(
            draft("a", countries = listOf(Country.SI), translations = mapOf("sl" to words("sl", "a"))),
            draft("b", countries = listOf(Country.SI), translations = mapOf("sl" to words("sl", "b"))),
        )
        val b = idOf("b")
        assertEquals(2 * (1 + 1 + 3), rows().size, "a country, a translation and its three answers each")
        transaction(database) { Questions.deleteWhere { Questions.importKey eq "a" } }
        val left = rows()
        assertEquals(1 + 1 + 3, left.size, "b's alone")
        assertTrue(left.all { it.first() == b })
    }

    @Test
    fun `localizations of one question racing take turns`() {
        import(draft("a"))
        val localization =
            QuestionLocalizationDto(
                "a",
                listOf(Country.HR, Country.BA),
                mapOf("hr" to words("hr", "a"), "bs" to words("bs", "a"), "sl" to words("sl", "a")),
            )
        val outcomes =
            runBlocking(Dispatchers.IO) {
                (1..8).map { n -> async { db.query { QuestionStore.localize(localization, 20L + n) } } }.awaitAll()
            }
        assertEquals(List(8) { Localized.Updated }, outcomes)
        assertEquals(2 + 3 + 3 * 3, rows().size, "each row once")
    }
}
