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
  composition.
- Text a skin sets in capitals is **read to a screen reader as written** (`KvizicText`).

## 6. Realtime

- REST takes a seat (create, join by code, Quick play, solo) and answers a one-time ticket (30 s). The
  socket `wss://…/v1/play` takes `hello(ticket, protocol, platform, build)` as its first frame within 5 s;
  nothing secret is in the URL.
- One coroutine actor per lobby owns its state; outgoing queues are bounded (a slow socket is closed 4429).
- One client address holds at most `MAX_SOCKETS_PER_ADDRESS` sockets (200): a mobile carrier's CGNAT puts
  many phones behind one address, and guest minting's per-address budget already bounds an abuser.
  Every state message carries `v`; a connect gets a snapshot; a gap resyncs. Times on the wire are relative.
- Close codes are a contract: 4400 protocol, 4401 ticket, 4403 kicked, 4404 gone, 4408 silent, 4409
  replaced, 4410 session ended, 4426 update, 4429 slow, 4503 restarting.
- A disconnect is not a leave: grace 2 min in the lobby, the rest of the game in a game. A player dropped
  less than `dropGrace` ago (3 s) is still waited for before an early reveal.
- Deploys drain: Render waits `maxShutdownDelaySeconds` (300, prod) and the server drains lobbies for
  `DRAIN_SECONDS` (280 prod, 25 dev), then waits for sockets to close.
- The client: `DefaultLobbySession`, one per app, reconnects with backoff and jitter, re-tickets, resends
  a pending answer; the UI reads its `StateFlow` (`RoomViewModel`).

## 7. Game rules (defaults; server config, `GameTimings`, `ScoringRules`)

- Flow: lobby → countdown 3 s (5 s when someone is still on results) → **read** → **answer** → reveal 5 s
  (7 with an explanation) → … → results → back to the lobby by hand.
- **Read time**: 1.5 s + 45 ms a character, at most 7 s (the longest question reads in 6.9 s).
- Answers: 2 to 4 per question, never hard-coded to 4. One locked answer each; a player sees the others'
  picks once locked in. The question ends early once everyone it waits for has answered.
- **Scoring**, max 100: right `50 + 40·f` plus +10/+5/+2 for the first three right; wrong `−(5 + 35·f²)`
  (−5 to −40), scaled for fewer answers; none 0; the minus can be turned off per room. `f` is the time
  left when the answer lands, less min(RTT, 300 ms).
- Settings: questions 5/10/15/20, time 10/15/20/30 s, topics (none = Све), seats 2–8, private or public,
  minus on/off. Codes are 6 digits, not reused for 30 min; a per-address guard stops guessing.

## 8. Questions

- **Limits** (`KvizicApi.Limits`, decided 2026-10-01): a question at most **120** characters, an answer
  **60**, an explanation **160** (300 at first: on a small phone with eight players it left the reveal's
  answers no room). The content repo's house style is stricter for answers (40).
- Topics: Географија, Историја, Спорт, Музика, Филм и серије, Наука и технологија, Језик и књижевност,
  Наши простори; all feed Све. Questions are ekavian Serbian Cyrillic; Latin is made by transliteration.
- The admin routes import, edit, approve, retire and export; three wrong-answer reports suspend a question.
  Dev loads the seed from a Render secret file (`QUESTION_SEED_FILE`), H2 only.
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
  `shown()`/`topicName()`.
- Analytics: PostHog over HTTP, never a name, a code or a question's text; every tap through `tapped`
  (`TapsTest` taps every screen). Game events: `room_entered`, `room_exited`, `game_finished`.

## 10. Design system decisions

- Skins swap colours, type, shapes, depth, motion, parts, backdrop and avatar palette; every test runs
  every skin (`Skins.ALL`). Fonts are bundled (OFL); each face declares its figures' height from its
  outlines (measured unhinted, alike on macOS and Linux).
- **Long text** (`LongTextFitTest`): the longest question and four longest answers fit whole on 375×667
  and 360×640 in every skin while answering and locked in. A question is at most 7 lines read alone and 4
  over its answers; four answers stand in a 2×2 grid unless a column sets them larger, measured in the room
  each tile leaves; all answers of a question share one size.
- **The reveal** (`RevealFitTest`): with eight players, the longest question, answers and explanation it fits
  the same phones; the question is set small there (a recap), and the standings list gives way when keeping
  it would leave the answers no room (the player's points stay in the bar and the verdict).
- **Picks peek from behind the card**: those who picked an answer stand behind its tile, their heads over
  its top edge; a crowd closes up to fit the tile (`crowdOverlap`); rows keep a gap for the heads.
- **Waiting strip**: an hourglass and the avatars of those the question still waits for, the player among
  them until they answer; no words, no count.
- Reactions are the server's six: bravo, applause, fire, wow, laugh, oops.
- Avatars: the server's sixteen Balkan animals, each drawn by hand (`AvatarArt`); an id this build does not
  know shows a silhouette.

## 11. Hosting

`render.yaml`: `kvizic-server-dev` (free, H2, deploys every green `main`), `kvizic-server` (Starter, on
`kvizic-postgres`, deployed **by hand** with Manual Deploy, a commit already green and live on dev), API at
`kvizic-api.ntole.com`. Secrets are Render environment variables, never committed.

## 12. Verifying

CI (`.github/workflows/ci.yml`) is the definition of green: `verify` (lint, every JVM test including
`:e2e:test`, every client target, release signing), `server-postgres`, `docker-smoke`, `ios`. Locally:
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
- The reveal's look with eight standings: built to fit (standings give way), not yet designed at the gate.
- Play Games on a device, sound, share links. Haptics: a tap on lock-in, right or wrong on the reveal.
- The moderation app's web page loses the first key after unlocking until the page is clicked.
