package io.ntole.kvizic.core.api

/**
 * Every path, header and parameter name on the wire, and the limits both sides check against.
 *
 * `:server` declares its routes with these and `:core:network` builds its requests with them, so the
 * two sides can never disagree on a path. Never hard-code a route string on either side.
 */
public object KvizicApi {
    public const val VERSION: String = "v1"

    public object Paths {
        public const val HEALTH: String = "/health"

        /** POST, no body: mints a guest with a first session, answered with a `SessionDto`. Per address. */
        public const val AUTH_GUEST: String = "/$VERSION/auth/guest"

        /** POST a `RefreshRequest`: rotates the session's refresh token, answered with a `SessionDto`. */
        public const val AUTH_REFRESH: String = "/$VERSION/auth/refresh"

        /** POST, no body: ends the session the bearer names, answered 204. */
        public const val AUTH_LOGOUT: String = "/$VERSION/auth/logout"

        /**
         * POST a `PlayGamesSignInRequest`: signs in with Google Play Games Services, answered with a
         * `SessionDto` for a new session of the player the Play Games player is linked to. The bearer is
         * optional: an unlinked Play Games player is linked to the player it names. 404 when the server
         * has no Play Games client configured.
         */
        public const val AUTH_PLAY_GAMES: String = "/$VERSION/auth/play-games"

        /** GET: the bearer's profile and stats, a `ProfileDto`. */
        public const val ME: String = "/$VERSION/me"

        /** POST a `SetAvatarRequest`: picks the player's avatar, answered with the `ProfileDto`. */
        public const val MY_AVATAR: String = "/$VERSION/me/avatar"

        /** POST, no body: deletes the bearer's account and everything of theirs, answered 204. */
        public const val ME_DELETION: String = "/$VERSION/me/deletion"

        /** GET: every topic, with how many approved questions each has, a `TopicListDto`. No session needed. */
        public const val TOPICS: String = "/$VERSION/topics"

        /**
         * POST a `CreateLobbyRequest`: opens a lobby with the bearer as its host, answered with a
         * `TicketDto` for its socket. GET: the open public lobbies, a `LobbyListDto`.
         */
        public const val LOBBIES: String = "/$VERSION/lobbies"

        /** POST a `JoinLobbyRequest`: a seat in the lobby with that code, answered with a `TicketDto`. Also every rejoin. */
        public const val LOBBY_JOINS: String = "/$VERSION/lobby-joins"

        /** POST, no body: a seat in the best waiting public lobby, or a new public one, answered with a `TicketDto`. */
        public const val QUICK_PLAY: String = "/$VERSION/quick-play"

        /** POST, no body: a solo run's one-seat lobby, answered with a `TicketDto`. */
        public const val SOLO_RUNS: String = "/$VERSION/solo-runs"

        /**
         * The realtime socket. The first frame must be a `ClientMessage.Hello` carrying a ticket from one
         * of the routes above, within a few seconds; everything after is `ClientMessage`s and
         * `ServerMessage`s in `ProtocolJson`. Failures close the socket with a code from `CloseCodes`.
         */
        public const val PLAY: String = "/$VERSION/play"

        /** POST a `ReportQuestionRequest`: a player reports a question they were asked, answered 204. */
        public const val REPORTS: String = "/$VERSION/reports"

        /** POST an `ImportQuestionsRequest`: drafts into the bank, answered with an `ImportResultDto`. Admin. */
        public const val ADMIN_QUESTION_IMPORTS: String = "/$VERSION/admin/question-imports"

        /** GET: a page of the bank, filtered by status, topic and search, an `AdminQuestionPageDto`. Admin. */
        public const val ADMIN_QUESTIONS: String = "/$VERSION/admin/questions"

        /** POST an `EditQuestionRequest`: a compare-and-set on the question's revision. Admin. */
        public const val ADMIN_QUESTION_EDITS: String = "/$VERSION/admin/question-edits"

        /** POST a `QuestionDecisionRequest`: approves a draft. Admin. */
        public const val ADMIN_QUESTION_APPROVALS: String = "/$VERSION/admin/question-approvals"

        /** POST a `QuestionDecisionRequest` with a reason: rejects a draft. Admin. */
        public const val ADMIN_QUESTION_REJECTIONS: String = "/$VERSION/admin/question-rejections"

        /** POST a `QuestionDecisionRequest`: takes an approved question out of play. Admin. */
        public const val ADMIN_QUESTION_RETIREMENTS: String = "/$VERSION/admin/question-retirements"

        /** POST a `QuestionDecisionRequest`: puts a retired or suspended question back in play. Admin. */
        public const val ADMIN_QUESTION_RESTORATIONS: String = "/$VERSION/admin/question-restorations"

        /** GET: the questions players reported, grouped, an `AdminReportListDto`. Admin. */
        public const val ADMIN_REPORTS: String = "/$VERSION/admin/reports"

        /** POST a `ResolveReportsRequest`: closes a question's open reports. Admin. */
        public const val ADMIN_REPORT_RESOLUTIONS: String = "/$VERSION/admin/report-resolutions"

        /** POST a `CreateTopicRequest`: adds a topic, answered 201 with its `TopicDto`. Admin. */
        public const val ADMIN_TOPICS: String = "/$VERSION/admin/topics"

        /** POST a `RenameTopicRequest`: sets both of a topic's names. Admin. */
        public const val ADMIN_TOPIC_RENAMES: String = "/$VERSION/admin/topic-renames"

        /** GET: the whole bank in the import format, the owner's private backup of edited questions. Admin. */
        public const val ADMIN_QUESTION_EXPORTS: String = "/$VERSION/admin/question-exports"

        /** GET: counts of the bank and of what is live right now, an `AdminOverviewDto`. Admin. */
        public const val ADMIN_OVERVIEW: String = "/$VERSION/admin/overview"

        /** POST a `DeleteAccountRequest`: deletes a player's account on their request, answered 204. Admin. */
        public const val ADMIN_ACCOUNT_DELETIONS: String = "/$VERSION/admin/account-deletions"
    }

    public object Headers {
        /**
         * The server's admin token, on every admin route. Without it, or with another, an admin route
         * is 403 `FORBIDDEN`, never 401, which a client would answer by refreshing a player's session.
         */
        public const val ADMIN_TOKEN: String = "X-Admin-Token"

        /** Which client sent the request, one of [ClientPlatform]'s names, beside [CLIENT_VERSION]. */
        public const val CLIENT_PLATFORM: String = "X-Client-Platform"

        /** The client's build number; a server with a minimum for the platform refuses an older one with 426. */
        public const val CLIENT_VERSION: String = "X-Client-Version"
    }

    /** What [Headers.CLIENT_PLATFORM] names, in lower case. */
    public object ClientPlatform {
        public const val ANDROID: String = "android"
        public const val IOS: String = "ios"
        public const val WEB: String = "web"
        public const val DESKTOP: String = "desktop"
    }

    public object Query {
        public const val LIMIT: String = "limit"
        public const val CURSOR: String = "cursor"

        /** A `QuestionStatus` name, repeatable, on [Paths.ADMIN_QUESTIONS]. */
        public const val STATUS: String = "status"

        /** A topic id, repeatable, on [Paths.ADMIN_QUESTIONS]. */
        public const val TOPIC: String = "topic"

        /** Free text matched against a question's text and answers, on [Paths.ADMIN_QUESTIONS]. */
        public const val SEARCH: String = "q"
    }

    public object Limits {
        public const val DEFAULT_PAGE_SIZE: Int = 20
        public const val MAX_PAGE_SIZE: Int = 100

        /** Longest server auth code a Play Games sign-in may carry, visible ASCII. One is about a hundred. */
        public const val MAX_SERVER_AUTH_CODE_LENGTH: Int = 2048

        /** Longest name a player shows, in code points once cleaned. */
        public const val MAX_DISPLAY_NAME_LENGTH: Int = 24

        /** Longest avatar, topic or reaction id: `A`-`Z`, `a`-`z`, `0`-`9` and `_`. */
        public const val MAX_ID_LENGTH: Int = 32

        /** A lobby's code: this many digits, which read the same in Cyrillic and Latin. */
        public const val LOBBY_CODE_LENGTH: Int = 6

        /** The fewest seats a host may set a lobby to, and the most. */
        public const val MIN_MAX_PLAYERS: Int = 2
        public const val MAX_PLAYERS: Int = 8

        /** The numbers of questions a game may have. */
        public val QUESTION_COUNTS: List<Int> = listOf(5, 10, 15, 20)

        /** The seconds a question may give to answer. */
        public val ANSWER_SECONDS: List<Int> = listOf(10, 15, 20, 30)

        /** The fewest and most answers a question offers: 2 for a true/false question, 4 today's. */
        public const val MIN_OPTIONS: Int = 2
        public const val MAX_OPTIONS: Int = 4

        /**
         * Longest question, answer and explanation, in UTF-16 units once trimmed. A question is read against
         * the clock, so it is short: the longest reads in some seven seconds, and four of the longest answers
         * still stand whole on the smallest phone, where each is at most a few lines.
         */
        public const val MAX_QUESTION_TEXT_LENGTH: Int = 120
        public const val MAX_OPTION_LENGTH: Int = 60
        public const val MAX_EXPLANATION_LENGTH: Int = 300

        /** Longest source URL a draft may cite, and topic name. */
        public const val MAX_SOURCE_URL_LENGTH: Int = 500
        public const val MAX_TOPIC_NAME_LENGTH: Int = 40

        /** Most topics one question is filed under. */
        public const val MAX_TOPICS_PER_QUESTION: Int = 3

        /**
         * Most drafts one import request may carry; the moderation app splits a bigger file. Kept so the
         * largest batch, every field at its longest in Cyrillic, stays under the server's 64 KiB body cap.
         */
        public const val MAX_IMPORT_BATCH: Int = 25

        /** Longest reason a moderator gives, and longest import key. */
        public const val MAX_REASON_LENGTH: Int = 200
        public const val MAX_IMPORT_KEY_LENGTH: Int = 64
    }
}
