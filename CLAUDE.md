# CLAUDE.md — Квизић / Kvizić

The source of truth for this repository. Read it first every session; when a decision changes, change it
here in the same commit. It is lean on purpose: the code's KDoc holds the detail, this file the decisions.

## 1. What this is

A realtime multiplayer trivia game with Among Us-style rooms: a host sets a room up (questions, time,
topics, seats, private or public, the wrong-answer minus) and starts; everyone answers the same question
against the clock, sees the others' picks once locked in, then the reveal, then the next. Results at the
end; each player goes back to the room by hand. Solo is a side mode. Serbian first, Android first; the KMP
targets (iOS, desktop, web) keep compiling. Built on WYR's platform, copied and renamed.

## 2. Ground rules

- **WYR (`~/Projects/WYR`) is never modified**: it is live on Google Play.
- **Personal identity only**: git `user.name = Nikola`, `user.email = nikola.tokicg6@gmail.com`,
  `user.useConfigOnly = true`; GitHub `niktok1`, through the `github-kvizic` SSH host. Never a company account.
- The repo is public (`niktok1/kvizic`); pushing `main` is the normal flow, and CI gates the dev deploy.
  `main` must always be green: run `./gradlew ktlintCheck` and check its **exit status** before a commit.
- **No question is ever committed here.** The bank lives in the server's database; drafts live in the
  private, local, never-pushed repo `~/Projects/kvizic-content` (`STYLE.md`, `check.py`, `drafts/`,
  `dev-seed.questions.json`).
- No secrets, keystores or `local.properties` (sdk.dir only) in git.
- Commits: Conventional Commits, small, ending with the `Co-Authored-By` line the session gives.

## 3. Modules

```
:core               the wire: DTOs, routes, KvizicApi.Limits, the realtime protocol (ProtocolJson). explicitApi.
:core:domain        pure Kotlin: session, lobby (LobbySession, GamePhase, LobbyRules), errors, analytics port.
:core:network       Ktor client, token storage, PostHog sender, the realtime transport (KtorPlayTransport).
:core:data          repositories, DefaultLobbySession (one per app), the reducer, DI modules.
:app:designsystem   the skin engine and every component; no Material. Skins: Buzzers (default), Notebook.
:app:shared         screens, ViewModels, Strings, navigation, DI; Android's Google services in androidMain.
:app:androidApp / desktopApp / webApp, app/iosApp   entry points only.
:app:adminApp       the moderation app, desktop and web: review, the bank, reports, overview, accounts.
:server             Ktor: auth, lobby actors, realtime, question bank, results, reports, admin. Flyway.
:e2e                the real server in-process, real client sessions over CIO, through a fault proxy.
```

Dependencies point inward. `:core:domain` sees nothing of the project; UI never sees a DTO (a constant the
UI needs is copied into the domain and pinned by a test in `:core:data`, as `LobbyRules` is).

## 4. Libraries

Kotlin-first and multiplatform, JetBrains or the KMP standard; versions in `gradle/libs.versions.toml`. A new
dependency is recorded there and here. Server-only Java exceptions: HikariCP, the PostgreSQL driver,
`java-jwt`, Flyway; H2 for local development and tests. Android-only Google SDKs behind domain ports:
Play Games Services v2. **No Material**: the design system draws everything.

## 5. Code rules that bite

- ktlint (`.editorconfig`, 120 columns), no `!!`, explicit visibility in `:core` and `:core:domain`.
- **Wire enums** that may grow have `UNKNOWN` as their default; the client `Json` coerces. **Protocol
  messages** decode an unknown type to `Unknown` (golden files pin shipped frames).
- **Transactions run at READ COMMITTED**: counters are SQL increments, read-then-write is a
  compare-and-set, uniqueness is a constraint, rows are locked in key order (`ResultWriter` locks players
  first).
- **Flyway**: `server/src/main/resources/db/migration`, one script set for H2 and PostgreSQL; a shipped
  script never changes; `SchemaDriftTest` holds the definitions to the scripts. Column widths may be wider
  than the rules' limits (`Questions.TEXT_COLUMN`), so a limit can rise without a migration.
- **The design system's single source**: no colour, dp or sp literal in a screen; every value is a skin
  token. **Motion is draw-only**: an animation's value is read in a draw or placement lambda, never in
  composition. What stands on a raised surface **turns with its face**: a word, an icon or a tile's mark
  whose colour follows a state takes it through `rememberSettlingColor`, never a colour that snaps while the
  face settles (light words on a face still light flicker; `SettleTogetherTest`).
- Text a skin sets in capitals is **read to a screen reader as written** (`KvizicText`).

## 6. Realtime

- REST takes a seat (create, join by code, Quick play, solo) and answers a one-time ticket (30 s). The
  socket `wss://…/v1/play` takes `hello(ticket, protocol, platform, build)` as its first frame within 5 s;
  nothing secret is in the URL.
- One coroutine actor per lobby owns its state; outgoing queues are bounded (a slow socket is closed 4429).
- One client address holds at most `MAX_SOCKETS_PER_ADDRESS` sockets (200): a mobile carrier's CGNAT puts
  many phones behind one address, and guest minting's per-address budget already bounds an abuser.
  Every state message carries `v`; a connect gets a snapshot; a gap resyncs. Times on the wire are relative.
- Close codes are a contract: 4400 protocol, 4401 ticket, 4403 kicked (by the host, or voted out, which the
  `closing` message tells apart), 4404 gone, 4408 silent, 4409 replaced, 4410 session ended, 4426 update,
  4429 slow, 4503 restarting.
- A disconnect is not a leave: grace 2 min in the lobby, the rest of the game in a game. A player dropped
  less than `dropGrace` ago (3 s) is still waited for before an early reveal.
- Deploys drain: Render waits `maxShutdownDelaySeconds` (300, prod) and the server drains lobbies for
  `DRAIN_SECONDS` (280 prod, 25 dev), then waits for sockets to close.
- The client: `DefaultLobbySession`, one per app, reconnects with backoff and jitter, re-tickets, resends
  a pending answer; the UI reads its `StateFlow` (`RoomViewModel`).

## 7. Game rules (defaults; server config, `GameTimings`, `ScoringRules`)

- Flow: lobby → countdown 5 s (7 s when someone is still on results) → **read** → **answer** → reveal 5 s
  (7 with an explanation) → … → results → back to the lobby by hand.
- **Not back, not in** (the owner, 2026-10-01): whoever is still on the last game's results when the next
  game's first question comes leaves the room (`NOT_BACK`, a normal close; not banned, they may join again),
  so nobody away holds a seat: an AFK player is out after at most one game. The countdown's „Нова игра почиње
  — уђи“ is their last call.
- **Host and vote-kick** (the owner, 2026-10-01; no ready state, no vote to start): the host starts, kicks and
  hands over as before, and a public room's idle host still hands over after 3 min. The rest may **vote a
  member out**, the host included, from the member's seat, while the room waits (never mid-game or in the
  countdown). It takes more than half of the other members in the room (connected and back from the
  results), and at least two (`kickVotesNeeded`), so of two players neither can; the tally moves with who is
  in. Voted out is a kick: 4403, banned for the room's life, hosting passing on as when a host leaves. No
  spam: one vote each at a time, a new one at most every 30 s (`kickVoteEvery`; `TOO_SOON`), taking one back
  never held up, a vote that worked freeing its voters at once; votes are anonymous, the seat showing only the
  count („2/3“) to everyone, the one voted on too; a game's countdown wipes them. `LobbyVoteKickTest`.
- **Read time**: 1.5 s + 45 ms a character, at most 7 s (the longest question reads in 6.9 s).
- Answers: 2 to 4 per question, never hard-coded to 4. One locked answer each; a player sees the others'
  picks once locked in. The question ends early once everyone it waits for has answered.
- **Scoring**, max 100: right `50 + 40·f` plus +10/+5/+2 for the first three right; wrong `−(5 + 35·f²)`
  (−5 to −40), scaled for fewer answers; none 0; the minus can be turned off per room. `f` is the time
  left when the answer lands, less min(RTT, 300 ms).
- Settings: questions 5/10/15/20, time 10/15/20/30 s, topics (none = Све), difficulty Лако/Средње/Тешко,
  seats 2–8, private or public, minus on/off. Codes are 6 digits, not reused for 30 min; a per-address guard
  stops guessing. The host changes them in the lobby alone, from its chips: never during a game, nor from
  the results (the owner, 2026-10-01).
- **Difficulty is a mix, never a filter** (`DifficultyMix`): easy, medium and hard in 60/30/10, 20/60/20 or
  10/30/60 percent, as whole counts, a share that does not come out whole falling at random. Who has seen
  what comes first: a game asks an unseen question off its level before a seen one on it, and a level the
  bank is short of takes the nearest. The room's chips name it in a word, unless medium. A solo run keeps its
  format but its level, picked in its room, and each level keeps its own best (V5; medium in V1's columns).

## 8. Questions

- **Limits** (`KvizicApi.Limits`, decided 2026-10-01): a question at most **120** characters, an answer
  **60**, an explanation **160** (300 at first: on a small phone with eight players it left the reveal's
  answers no room). The content repo's house style is stricter for answers (40).
- Topics: Географија, Историја, Спорт, Музика, Филм и серије, Наука и технологија, Језик и књижевност,
  Храна и пиће, and since V7 (the owner, 2026-10-01, after what the big quiz games ask) Природа и животиње,
  Уметност, Митологија, Тело и здравље, Возила, Игре, Стрипови и цртани; all feed Све. A topic is a
  subject, never a place: Наши простори went (the owner, 2026-10-01, V6), its questions to their subjects
  and its dishes to Храна и пиће, the one migration that deletes a topic. Questions are ekavian Serbian
  Cyrillic; Latin is made by transliteration.
- **Topic groups** (the owner's B, 2026-10-01): server data (`topic_groups`, V3), each topic in one or none:
  Знање (with V7's nature, art, mythology, body and vehicles), Забава (with Храна и пиће, Игре and Стрипови и
  цртани), Спорт. Written by migrations for now; moderator routes come with a topics tab.
  The settings show the topics picked in a few words („Спорт, Музика +3“), which open the picker: a search by
  any part of a name in either script with no accents needed, the groups opened and closed by their names,
  each with a chip for the whole group, counts, and thin topics greyed.
- **Difficulty** (2026-10-01): three levels. The author's is a prior worth 40 players; a question plays at
  the level its players measure (`MeasuredDifficulty`): the share right of those it waited for, a silence
  counted as not knowing, above what a guess among its answers gets. Read when a game is picked and by the
  moderation app ("plays hard"), never stored; a game runs from easy to hard by it.
- The admin routes import, edit, approve, retire and export; three wrong-answer reports suspend a question.
  Dev loads the seed from a Render secret file (`QUESTION_SEED_FILE`), H2 only: JSON, or the JSON gzipped in
  base64 (`QuestionSeed`), since a secret file holds at most 500 KiB and Render refuses every deploy while one
  is over; the content repo's `pack_seed.py` writes it (1.536 questions in 319 KiB).
- **Publishing drafts**: `KVIZIC_ADMIN_TOKEN=… ./publish.py dev drafts/*.json` in the content repo checks them,
  imports them (25 a request, a known key a duplicate) and approves the drafts it brought; `--import-only`
  leaves them to review in the moderation app.
- **The moderation app** (`:app:adminApp`, `KVIZIC_ENV=dev ./gradlew :app:adminApp:run`, or the web page
  built with `-Pkvizic.env`): the token is typed and held in memory only, a wrong one forgotten at once (ten
  a minute lock the address out). Review shows one draft at a time, A approves, R rejects with a reason
  (ready ones a tap away), E edits, J/K move; the bank filters and pages, retires and restores; reports are
  marked fixed, dismissed or retired; Overview counts the bank and the live games; Accounts deletes one by
  its id. English words, the questions as written. It binds `moderationDataModule` alone: no player session.

## 9. The client

- Screens (`Screen`): Home (multiplayer first), Join (keypad), PublicRooms (polled every 5 s; Home's
  counts every 10 s), NewRoom and RoomSettings (one `SettingsScreen`), Room (the lobby and the whole game,
  by `GamePhase`), About, Update. The navigator follows the room: in one, the room over Home; out, Home,
  which says why (`Navigator.followRoom`).
- Words: `Strings` (Serbian Cyrillic written by hand, Latin made from it, English), `GameStrings` for the
  game, `Plural` for Serbian's three forms. Server text (questions, names, topics) is shown through
  `shown()`/`topicName()`. The noun кôд keeps its length mark (о and a combining U+0302; every bundled face
  places it), which tells it from код (at).
- Analytics: PostHog over HTTP, never a name, a code or a question's text; every tap through `tapped`
  (`TapsTest` taps every screen). Game events: `room_entered`, `room_exited`, `game_finished`.
- **Play Games** (signed in on a phone against dev, 2026-10-01): a launch signs in by itself, with no button
  yet (`LinkPlayGames`), and so does coming back to the foreground, for a session replaced in the background,
  where Play Games cannot be asked; a sign-in that links the guest playing keeps their id and gives them
  their Play Games name, which Home hears through `LinkPlayGames.signedIn`. The ids are the developer's, in
  `local.properties` (`kvizic.playgames.appId`, `kvizic.playgames.serverClientId`; none is Play Games off);
  the server's `PLAY_GAMES_CLIENT_ID` and `PLAY_GAMES_CLIENT_SECRET` are on Render. The Play Console takes
  Android credentials for `io.ntole.kvizic` (Play's signing key and the debug key) and `io.ntole.kvizic.dev`
  (the debug key). Until its configuration is published, only its Testers sign in.
- **Phones play upright** (the owner, 2026-10-02): Android's activity is portrait and the iPhone takes
  portrait alone; the game is laid out for a phone's height. iPads, desktop and the web take any shape, and
  Android 16 lets a large screen turn the app anyway, where the content keeps its width (`contentWidth`).

## 10. Design system decisions

- Skins swap colours, type, shapes, depth, motion, parts, backdrop and avatar palette; every test runs
  every skin (`Skins.ALL`). Fonts are bundled (OFL); each face declares its figures' height from its
  outlines (measured unhinted, alike on macOS and Linux).
- **The face is Nunito** (the owner's pick, 2026-10-01), display and body, one variable file drawn at each
  weight on its `wght` axis; Android before 8.0 draws its regular instance only. It has no Serbian forms:
  б is drawn as in Russian. Fira stays for the notebook skin's body and the comparisons.
- **Long text** (`LongTextFitTest`): the longest question and four longest answers fit whole on 375×667
  and 360×640 in every skin while answering and locked in. A question is at most 7 lines read alone and 4
  over its answers; all answers of a question share one size, the largest the one that needs the most room
  is whole at, no word broken between two lines.
- **Answers stand by the window, never by the question** (the owner, 2026-10-02: a question in another shape
  than the last confuses): on a phone held upright every question's answers stand in a column, a tile across
  the width each; on a wide window (`LocalWideWindow`: on its side, or `wideWindow`, 600 dp, across) four
  stand in a 2×2 grid. A row kept short, as the reveal's, shrinks its letter's mark to `letterMarkLeast`.
- **The bar over a question** (the owner, 2026-10-01): the way out and the round in numbers alone („3 / 10“,
  said „Питање 3 од 10“), the clock in the middle of the screen, the points on MEDIUM flaps, the two sides
  alike, so 1 210 stands whole at 360 dp; the topic is no part of it but a tab on the question card's top
  edge, where the longest fits (`RoomScreenDrawTest`).
- **The reveal** is one standings board under the answers (the owner's, 2026-10-01): every player, scrolling
  when there is no room, the player's line lit and kept in sight, ▲/▼ for places moved, the lines sliding
  from their places before the question, and a line draining along its foot to the next question. The
  question is recalled small, in two lines beside an explanation; the report flag stands in its corner. The
  answers keep the least room that sets them whole and 45% of the rest; at the content style's limits the
  player's line stays on the board (`RevealFitTest`), at the wire's it may give way.
- **Picks stand on the card, or on its edge** (the owner, 2026-10-02): those who picked an answer stand on
  its tile's face, past the answer's longest line in a column or beside the letter in the grid, where a crowd
  of the question's players fits so on every tile, the answers laid out as without them; otherwise on every
  tile's top edge, about half over it, in front of it (`pickersPeek`), in the row gap and the room a screen
  leaves over the grid (`rowGap`). The question decides, never the picks so far, so no one moves as more pick
  (`CrowdTest`). A crowd closes up to fit (`crowdOverlap`). While a question is read, its answers' places
  (`AnswerPlaces`) stand in the shape its answers will.
- **A game's steps give way to each other**, never a cut (`RoomStagesTest`): the question read rises into
  its answers, which come up one after another; the answers' tiles glide into the reveal's places and light
  up from how they stood (`TilePlaces`, `TileGlideTest`), the question and its strip fading out before the
  recalled question and the board fade in, so no part is ever drawn twice (the owner, 2026-10-02: the two
  layouts crossfading read as a flicker); each new question comes in from the side like the next card. Times
  are skin motion tokens (`stage`, `tileAppear`, `tileStagger`); nothing replays after a rotation.
- **Waiting strip**: an hourglass and the avatars of those the question still waits for, the player among
  them until they answer; no words, no count.
- **The lobby counts in no words** (the owner, 2026-10-01): the seats show who is in and how many more fit,
  an empty seat a person's outline, the host by the microphone on the avatar, the player's own seat lit in
  the accent (`PanelKind.OWN`), one still on the results greyed with an hourglass; what a seat shows so, a
  screen reader is told. The code stands small in the top bar, a lock or a globe for private or public, and a
  long press copies it; the settings' chips take an icon where one says it (the clock on the time).
- Reactions are the server's seven: bravo, applause, fire, wow, laugh, oops on the room's bar, and the nudge,
  a bell a member sends with its own button, „Ајде, почни!“, where the host has Start.
- Avatars: the server's sixteen Balkan animals, each drawn by hand (`AvatarArt`); an id this build does not
  know shows a silhouette.
- **The icon** (2026-10-01): the sign's lights in a ring, sixteen lit marquee bulbs round Nunito Black's К in
  amber, on the stage; Android's adaptive layers (the themed one the ring and the К in one colour), iOS's
  1024 tile and the web's `icon.svg` drawn from one geometry. The desktop packages have none yet.

## 11. Hosting

`render.yaml`: `kvizic-server-dev` (free, H2, deploys every green `main`), `kvizic-server` (Starter, on
`kvizic-postgres`, deployed **by hand** with Manual Deploy, a commit already green and live on dev), API at
`kvizic-api.ntole.com`. Secrets are Render environment variables, never committed.

## 12. Verifying

CI (`.github/workflows/ci.yml`) is the definition of green, its jobs side by side: `verify` (lint, every JVM
test but the draw tests, `:e2e:test` among them), `design-tests` and `screen-tests` (`:app:designsystem` and
`:app:shared`'s JVM tests, the slowest, bound by the processor: a still is drawn at 60 frames a second for its
first half second and at 10 after, which every animation, run on frame time, settles the same from;
the classes share out among up to four test JVMs, so a class that grows long is split (`DesignShots*`,
`Room*DrawTest`); on CI each test is logged as it starts and ends, a task stops after 15 min, a job past
ten writes its test JVMs' threads, and the reports are kept every run),
`clients` (the desktop and web targets), `android` (a debug flavor, the release build through R8, release
signing), `server-postgres`, `docker-smoke`, `ios`. Locally:
`./gradlew ktlintCheck` and the module tests; iOS needs full Xcode, so compile with
`:app:shared:compileKotlinIosSimulatorArm64` and let CI link; Kotlin/Native refuses a comma in a common
test's name, so compile `compileTestKotlinIosSimulatorArm64` before pushing one. `KVIZIC_DESIGN_DIR=<dir>`
makes the draw tests write PNGs to look at.
- **A smoke test of the real UI**: run `:server:run` with `QUESTION_SEED_FILE` (the content repo's dev seed)
  and `ALLOWED_WEB_ORIGINS=localhost:8081,127.0.0.1:8081`, serve `:app:webApp:wasmJsBrowserDistribution`'s
  output on 8081, and open both origins (each its own player). The Browser pane stops painting while hidden,
  so read state from the server's log, not from a screenshot alone.
- **The load test**, `./gradlew :e2e:loadTest` (not in CI): 480 real clients in 60 rooms of eight play a game
  at once against the in-process server, with production pings. 2026-10-01 on the development Mac: every
  game done, a room's players saw the answers open within 13 ms of each other at p99 and the reveal within
  1 ms, heap 120 MB and 1.9 cores for server and clients together. The share the server alone would take of
  Render's 0.5 CPU is unmeasured: it needs the server in a process of its own.
- **Realtime engines differ**: the socket client sets no frame limit, which Ktor's browser and OkHttp engines
  refuse; `:e2e` plays a player over OkHttp beside one over CIO to hold that.

## 13. Open

- The design gate: the font, the tile scheme, the host badge, the timer, the Latin letters, the spotlight;
  the clap icon's drawing.
- Sound, share links. Haptics: a tap on lock-in, right or wrong on the reveal.
- Play Games before launch: its variables on `kvizic-server`; its consent screen published (in Testing now,
  it wants a privacy policy page first), then its configuration, with its final art.
- The moderation app's web page loses the first key after unlocking until the page is clicked.
- **Phones on their side**: phones are portrait-locked (§9) until the game has a landscape layout: the
  question screen in two panes, the bar and the question beside the answers' grid (a phone on its side is
  wide, so its answers already stand two by two), the reveal's board beside them; the lobby's seats, the join
  keypad, the settings and the results checked at a phone's height of ~360 dp. Then the lock comes off.
- `design-tests` hung on CI with four test JVMs (2026-10-02: no output for 29 min, cancelled at the job's
  limit; it passes locally in 42 s), and passed in one. Four again, with `DesignShotsTest` split: if it
  hangs again, the job's thread dumps say where.
