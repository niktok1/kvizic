package io.ntole.kvizic.language

/**
 * The words of the About screen, as [Strings.aboutScreen]: made in each language as the rest of [Strings]
 * is, and checked by the same tests. The libraries' and licences' names are their own, in every language,
 * so they are not here.
 */
data class AboutStrings(
    /** The About screen's name, as Home's button to it says. */
    val title: String,
    /** The app's version, `{0}`, and its build number beside it. */
    val version: String,
    val privacy: String,
    val terms: String,
    /** The site's page on deleting an account, in the app and without it. */
    val deleteAccountPage: String,
    val contact: String,
    /** The heading of the libraries the game ships with, each with its licence. */
    val licences: String,
    /** The heading of the fonts the game ships with, under the libraries, each with its licence's whole text. */
    val fonts: String,
    /** The heading over what the game keeps of the player: the statistics sent, the account's id, deleting it. */
    val data: String,
    /** The label over the player's account id, which they send to have their account deleted by email. */
    val accountId: String,
    /** The copy button beside the account id. */
    val copy: String,
    /** The copy button's name for a screen reader, which hears it apart from the label. */
    val copyAccountId: String,
    /** Said beside the label once the account id is copied. */
    val copied: String,
    /** The Statistics switch: whether the player lets the game send analytics. */
    val statistics: String,
    /** The button beside Statistics that explains it. */
    val aboutStatistics: String,
    /** What the Statistics switch sends, and what never. */
    val statisticsInfo: String,
    /** The button that closes the explanation. */
    val ok: String,
    /** Deleting the account, at the bottom of the screen. */
    val deleteAccount: DeleteAccountStrings,
) {
    /** These strings with [transform] applied to every one of them, as [Strings.map] asks. */
    internal fun map(transform: (String) -> String): AboutStrings =
        AboutStrings(
            title = transform(title),
            version = transform(version),
            privacy = transform(privacy),
            terms = transform(terms),
            deleteAccountPage = transform(deleteAccountPage),
            contact = transform(contact),
            licences = transform(licences),
            fonts = transform(fonts),
            data = transform(data),
            accountId = transform(accountId),
            copy = transform(copy),
            copyAccountId = transform(copyAccountId),
            copied = transform(copied),
            statistics = transform(statistics),
            aboutStatistics = transform(aboutStatistics),
            statisticsInfo = transform(statisticsInfo),
            ok = transform(ok),
            deleteAccount = deleteAccount.map(transform),
        )
}

/** The words of deleting an account, as [AboutStrings.deleteAccount]. */
data class DeleteAccountStrings(
    /** The quiet button at the bottom of the About screen. */
    val button: String,
    /** The confirm dialog's one line: everything goes, for good. */
    val warning: String,
    /** The dialog's button that deletes. */
    val confirm: String,
) {
    /** These strings with [transform] applied to every one of them, as [Strings.map] asks. */
    internal fun map(transform: (String) -> String): DeleteAccountStrings =
        DeleteAccountStrings(
            button = transform(button),
            warning = transform(warning),
            confirm = transform(confirm),
        )
}

/** The source text, written by hand. */
internal val SerbianCyrillicAboutStrings: AboutStrings =
    AboutStrings(
        title = "О игри",
        version = "Верзија {0}",
        privacy = "Политика приватности",
        terms = "Услови коришћења",
        deleteAccountPage = "Брисање налога",
        contact = "Контакт",
        licences = "Лиценце отвореног кода",
        fonts = "Фонтови",
        data = "Подаци",
        accountId = "ИД налога",
        copy = "Копирај",
        copyAccountId = "Копирај ИД налога",
        copied = "Копирано",
        statistics = "Статистика",
        aboutStatistics = "О статистици",
        statisticsInfo =
            "Шаљемо податке о томе како се игра користи, да бисмо је побољшали. Без твог имена и текста питања.",
        ok = "У реду",
        deleteAccount =
            DeleteAccountStrings(
                button = "Обриши налог",
                warning = "Налог и све у њему нестаће заувек.",
                confirm = "Обриши",
            ),
    )

internal val EnglishAboutStrings: AboutStrings =
    AboutStrings(
        title = "About",
        version = "Version {0}",
        privacy = "Privacy policy",
        terms = "Terms of use",
        deleteAccountPage = "Deleting an account",
        contact = "Contact",
        licences = "Open-source licences",
        fonts = "Fonts",
        data = "Data",
        accountId = "Account ID",
        copy = "Copy",
        copyAccountId = "Copy account ID",
        copied = "Copied",
        statistics = "Statistics",
        aboutStatistics = "About statistics",
        statisticsInfo =
            "We send how the game is used, so we can make it better. Never your name or any question's text.",
        ok = "OK",
        deleteAccount =
            DeleteAccountStrings(
                button = "Delete account",
                warning = "The account and all in it go for good.",
                confirm = "Delete",
            ),
    )
