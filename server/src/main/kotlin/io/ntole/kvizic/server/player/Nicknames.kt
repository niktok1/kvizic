package io.ntole.kvizic.server.player

import io.ntole.kvizic.core.player.Avatars
import kotlin.random.Random

/**
 * The nickname a guest gets: a kind adjective and their avatar's animal, in Serbian Cyrillic, the
 * adjective agreeing with the animal's gender (*Брзи Јеж*, *Брза Лисица*). Nothing typed by anyone, so
 * nothing to moderate. The animals are the avatars' own, and none of them is a Serbian insult.
 */
object Nicknames {
    private enum class Gender { MASCULINE, FEMININE }

    private class Animal(
        val name: String,
        val gender: Gender,
    )

    private val ANIMALS: Map<String, Animal> =
        mapOf(
            "fox" to Animal("Лисица", Gender.FEMININE),
            "bear" to Animal("Медвед", Gender.MASCULINE),
            "owl" to Animal("Сова", Gender.FEMININE),
            "hedgehog" to Animal("Јеж", Gender.MASCULINE),
            "wolf" to Animal("Вук", Gender.MASCULINE),
            "lynx" to Animal("Рис", Gender.MASCULINE),
            "deer" to Animal("Јелен", Gender.MASCULINE),
            "stork" to Animal("Рода", Gender.FEMININE),
            "squirrel" to Animal("Веверица", Gender.FEMININE),
            "hare" to Animal("Зец", Gender.MASCULINE),
            "badger" to Animal("Јазавац", Gender.MASCULINE),
            "otter" to Animal("Видра", Gender.FEMININE),
            "tortoise" to Animal("Корњача", Gender.FEMININE),
            "frog" to Animal("Жаба", Gender.FEMININE),
            "bee" to Animal("Пчела", Gender.FEMININE),
            "cat" to Animal("Мачка", Gender.FEMININE),
        )

    /** Each adjective's masculine and feminine form. */
    private val ADJECTIVES: List<Pair<String, String>> =
        listOf(
            "Брзи" to "Брза",
            "Мудри" to "Мудра",
            "Весели" to "Весела",
            "Храбри" to "Храбра",
            "Паметни" to "Паметна",
            "Хитри" to "Хитра",
            "Смели" to "Смела",
            "Вредни" to "Вредна",
            "Срећни" to "Срећна",
            "Вешти" to "Вешта",
            "Сјајни" to "Сјајна",
            "Радознали" to "Радознала",
            "Одважни" to "Одважна",
            "Поносни" to "Поносна",
            "Тајанствени" to "Тајанствена",
            "Насмејани" to "Насмејана",
        )

    init {
        check(ANIMALS.keys == Avatars.ALL.toSet()) { "every avatar needs an animal's name" }
    }

    /** A nickname for a player whose avatar is [avatarId]. */
    fun forAvatar(
        avatarId: String,
        random: Random = Random.Default,
    ): String {
        val animal = ANIMALS[avatarId] ?: ANIMALS.getValue(Avatars.ALL.first())
        val (masculine, feminine) = ADJECTIVES.random(random)
        val adjective = if (animal.gender == Gender.MASCULINE) masculine else feminine
        return "$adjective ${animal.name}"
    }

    /** Every nickname there can be, for tests. */
    internal fun all(): List<String> =
        ANIMALS.values.flatMap { animal ->
            ADJECTIVES.map { (masculine, feminine) ->
                "${if (animal.gender == Gender.MASCULINE) masculine else feminine} ${animal.name}"
            }
        }
}
