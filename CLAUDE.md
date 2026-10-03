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
Play Games Services v2. **No Material**: the design system draws everything. **No sound library**: each
platform's own API behind `SoundDevice` (SoundPool, AVAudioPlayer, `javax.sound`, Web Audio).

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

- REST takes a seat (create, join by code, Quick play, solo) and answers a one-time ticket (30 s). A room may be read first by its code (preview). The
  socket `wss://…/v1/play` takes `hello(ticket, protocol, platform, build)` as its first frame within 5 s;
  nothing secret is in the URL.
- **The launch takes back a held seat** (the owner, 2026-10-03): the system ends an app in the background
  mid-game and the code dies with it, so a launch with a session stored asks `POST /v1/lobby-rejoins`, which
  answers a ticket for the seat the player holds wherever it is, never a fresh one (LOBBY_NOT_FOUND), and the
  room opens over Home; nothing is said when there is none (`AppServices`, `LobbySession.rejoin`).
- One coroutine actor per lobby owns its state; outgoing queues are bounded (a slow socket is closed 4429).
- One client address holds at most `MAX_SOCKETS_PER_ADDRESS` sockets (200): a mobile carrier's CGNAT puts
  many phones behind one address, and guest minting's per-address budget already bounds an abuser.
  Every state message carries `v`; a connect gets a snapshot; a gap resyncs. Times on the wire are relative.
- Close codes are a contract: 4400 protocol, 4401 ticket, 4403 kicked (by the host, or voted out, which the
  `closing` message tells apart), 4404 gone, 4408 silent, 4409 replaced, 4410 session ended, 4426 update,
  4429 slow, 4503 restarting.
- The server tells an idle public host and an idle waiting room before it acts on them (`notice`, `HOST_IDLE`,
  `ROOM_IDLE`), and a member's `stay` frame answers: it counts as activity and restarts the room's idle time.
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
  in. Voted out is a kick: 4403, banned for the room's life, hosting passing on as when a host leaves; one
  voted out while away hears it from the rejoin's 403 `LOBBY_BANNED`, whose `ErrorDto.reason` (`VOTED_OUT`)
  tells it from the host's kick (a code of its own would read UNKNOWN on 1.0.0, which retries). No
  spam: one vote each at a time, a new one at most every 30 s (`kickVoteEvery`; `TOO_SOON`), taking one back
  never held up, a vote that worked freeing its voters at once; votes are anonymous, the seat showing only the
  count („2/3“) to everyone, the one voted on too; a game's countdown wipes them. `LobbyVoteKickTest`.
- **Read time**: 1.5 s + 45 ms a character, at most 7 s (the longest question reads in 6.9 s).
- Answers: 2 to 4 per question, never hard-coded to 4. One locked answer each; a player sees the others'
  picks once locked in. The question ends early once everyone it waits for has answered.
- **Scoring**, max 100: right `50 + 40·f` plus +10/+5/+2 for the first three right; wrong `−(5 + 35·f²)`
  (−5 to −40), scaled for fewer answers; none 0; the minus can be turned off per room. `f` is the time
  left when the answer lands, less min(RTT, 300 ms).
- **Levels** (the owner, 2026-10-02): a player's level is worked out on the server from `profiles.xp` (V8), earned
  by a finished game in a room, **never in a solo run**, and only by those who stayed to its end: 20 for the game,
  1 for each right answer, 20 for a win (`Levels`). Level `n` begins at `10·(n−1)²` experience, so the first levels
  come with the first games (the second after one, the fifth after five) and each later one takes more than the
  one before (the tenth about 25 games, the twentieth about 120, the fiftieth about 800). The client never works
  it out: the profile carries `PlayerLevelDto` (the number, the experience in it, what it takes), a room's
  `MemberView` and `FinalStandingView` the number alone, 0 from a server that says none. The lobby counts a game's
  experience into its members as it ends, so a seat's level moves at once; sitting down again reads it afresh.
  Levels are for nothing yet but to show: Home's caption and bar, the account's, a seat's and the podium's badge.
  Games and wins stay in the account's statistics only.
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
  Уметност, Митологија, Тело и здравље, Возила, Игре, Стрипови и цртани, and since V9 (the owner, 2026-10-03)
  Технологија и интернет, Свемир, Математика и логика, Престонице, Знаменитости, Празници и обичаји (the
  region's, as customs, never faith), Познате личности, Фудбал and Кошарка; all feed Све. **A question takes
  every topic a player would look for it under**, 1 to 3 (the owner, 2026-10-03; Фудбал and Кошарка always
  with Спорт, Престонице with Географија, Свемир and Математика with Наука), so a narrow topic is no split:
  its questions stay in the broad one too. The content repo's `STYLE.md` holds the rule. A topic is a
  subject, never a place: Наши простори went (the owner, 2026-10-01, V6), its questions to their subjects
  and its dishes to Храна и пиће, the one migration that deletes a topic. Questions are ekavian Serbian
  Cyrillic; Latin is made by transliteration.
- **Topic groups** (the owner's B, 2026-10-01): server data (`topic_groups`, V3), each topic in one or none:
  Знање (with V7's nature, art, mythology, body and vehicles, and V9's technology, space, maths, capitals and
  landmarks), Забава (with Храна и пиће, Игре, Стрипови и цртани, and V9's customs and famous people), Спорт
  (with Фудбал and Кошарка). Written by migrations for now; moderator routes come with a topics tab.
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
  is over; the content repo's `pack_seed.py` writes it, in as few parts as fit, which `QUESTION_SEED_FILE` names
  separated by commas (2.827 questions in two parts of about 300 KiB, 2026-10-03).
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
  by `GamePhase`), Settings (the app's: the Sound switch, Ћирилица or Latinica on two chips with no heading, it being a script
  more than a language (the owner, 2026-10-03; `Language.OFFERED`), and a way to About; `AppSettingsScreen`, apart from a room's), About
  (version, legal pages, licences and the fonts' OFL, each text a tap away, and last what is kept of the player, under „Подаци“: Statistics, the account's
  id and its deletion), Update. Home's
  sliders button opens Settings. The navigator follows the room: in one, the room over Home; out, Home,
  which says why (`Navigator.followRoom`).
- Words: `Strings` (Serbian Cyrillic written by hand, Latin made from it, English), `GameStrings` for the
  game, `Plural` for Serbian's three forms. Server text (questions, names, topics) is shown through
  `shown()`/`topicName()`. The noun кôд keeps its length mark (о and a combining U+0302; every bundled face
  places it), which tells it from код (at).
- Analytics: PostHog over HTTP, never a name, a code or a question's text; every tap through `tapped`
  (`TapsTest` taps every screen). Game events: `room_entered`, `room_exited`, `game_finished`; settings: `language_changed`,
  `sound_changed`. A name once sent never changes.
- **Play Games** (signed in on a phone against dev, 2026-10-01): a launch signs in by itself, with no button
  yet (`LinkPlayGames`), and so does coming back to the foreground, for a session replaced in the background,
  where Play Games cannot be asked; a sign-in that links the guest playing keeps their id and gives them
  their Play Games name, which Home hears through `LinkPlayGames.signedIn`. The ids are the developer's, in
  `local.properties` (`kvizic.playgames.appId`, `kvizic.playgames.serverClientId`; none is Play Games off);
  the server's `PLAY_GAMES_CLIENT_ID` and `PLAY_GAMES_CLIENT_SECRET` are on Render. The Play Console takes
  Android credentials for `io.ntole.kvizic` (Play's signing key and the debug key) and `io.ntole.kvizic.dev`
  (the debug key). Its consent screen and configuration are published (2026-10-03): every player signs in. A
  prod build signed with the debug key got DEVELOPER_ERROR on 2026-10-03, so that key is no longer, or never was,
  on `io.ntole.kvizic`'s credential: sign in with a Play-installed build, or add it again.
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
  and 360×640 in every skin while answering and locked in. A question is at most 4 lines over its answers'
  tiles, read or answered; all answers of a question share one size, the largest the one that needs the most
  room is whole at, no word broken between two lines.
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
  player's line stays on the board (`RevealFitTest`), at the wire's it may give way. The board's least is its
  own line, measured, and a reveal with less room leaves the board out whole, never half off the screen; the
  explanation takes 4 lines at most, giving back at a large font what it must of its size (2026-10-03).
- **Words are never cut on a button or in a dialog** (2026-10-03, after a test at 130% font): a `StageButton`
  keeps its label on one line, smaller where it must, and only past its least size wraps onto `maxLines` (two
  for a tile with its icon above), no word broken (`OneLineFirst`). A `StageDialog`'s buttons stand in a row
  while they fit at their own width, else stacked across it, the main action on top. The lobby's chips wrap
  (`FlowRow`). `WrapFitTest`, `RoomFitDrawTest`.
- **Picks stand on the card, or on its edge** (the owner, 2026-10-02): those who picked an answer stand on
  its tile's face, past the answer's longest line in a column or beside the letter in the grid, where a crowd
  of the question's players fits so on every tile, the answers laid out as without them; otherwise on every
  tile's top edge, about half over it, in front of it (`pickersPeek`), in the row gap and the room a screen
  leaves over the grid (`rowGap`). The question decides, never the picks so far, so no one moves as more pick
  (`CrowdTest`). A crowd closes up to fit (`crowdOverlap`).
- **A game's steps give way to each other**, never a cut (`RoomStagesTest`): a question is read over its
  answers' tiles, dark, the clock's lights coming up in the bar, and its answers come onto those tiles one
  after another as they light, nothing else moving (the owner, 2026-10-02: the read's own screen put its
  places for the answers elsewhere than the answers came); the answers' tiles glide into the reveal's places
  and light up from how they stood (`TilePlaces`, `TileGlideTest`), the question and its strip fading out
  before the recalled question and the board fade in, so no part is ever drawn twice (the owner, 2026-10-02:
  the two layouts crossfading read as a flicker); each new question comes in from the side like the next
  card. Times are skin motion tokens (`stage`, `tileAppear`, `tileStagger`); nothing replays after a rotation.
- **Waiting strip**: an hourglass and the avatars of those the question still waits for, the player among
  them until they answer; no words, no count.
- **The lobby counts in no words** (the owner, 2026-10-01): the seats show who is in and how many more fit,
  an empty seat a person's outline, the host by the microphone on the avatar, the player's own seat lit in
  the accent (`PanelKind.OWN`), one still on the results greyed with an hourglass; what a seat shows so, a
  screen reader is told. The code stands small in the top bar, a lock or a globe for private or public, and a
  long press copies it; the settings' chips take an icon where one says it (the clock on the time).
- **Home** (the owner, 2026-10-02): the player's name over „Ниво 7“ and a thin bar of how far through the level they
  are (`LevelBar`). Play Games signs a player in by itself (`LinkPlayGames.automatically`); for a guest it did not, in
  a build with Play Games (`HomeState.offersPlayGames`), the fallback (the owner, 2026-10-03, after a full-width
  button that did not fit): „Сачувај напредак“, a link in the accent on the level's line, and on the profile a card,
  „Играш као гост“, what Play Games keeps and Повежи (`LinkPlayGames.manually`), since Play Games offers its own
  sign-in once for an account and a player who backed out of it would stay a guest for good. A tap Play Games
  signs nobody in for says so (`PLAY_GAMES_NOT_SIGNED_IN`): a refused key looks just like backing out. Clearing the app's data never changes the account
  Play Games hands it; the game's sign stands in the middle of the room the buttons leave, its name shrinking to stay inside the
  sign's panel where the sign is narrow (`LogoSizes.wordInset`; Nunito is wide), with no art (the owner chose
  it over the icon's bulb ring, an animal cast and a question card). Where a level is shown on an avatar it is a
  badge at the bottom start, where the host's microphone is at the end; a line of a board is too small for it.
- **Presence on Home and the public list** (the owner, 2026-10-02): no sentence but a small sign sunk into the page
  (`PresenceStrip`): a person and the players online, a magnifier and those searching, each count on
  flaps; it stays line-sized, being only nice to know, and a screen reader is told the sentence. On Home it stands
  on Quick play's foot, inside the button (`StageButton.footer`, over the face so the words stay in its middle), bare
  and in plain figures of the button's text colour, its place held before the first read. The counts
  are the poll's alone (`GET /v1/lobbies`, every 10 s): the socket's `presence` frames are not kept, since a count
  kept from the last room showed a phone one number and a tablet another. Online is everyone with the app open:
  a socket in a room, or a read of the list in the last 30 s (`LobbyRegistry.LOOKING_ON`, 2026-10-03).
- **The results** (the owner, 2026-10-02): the winner stands on a plate of the first step's colour (`WinnerBanner`,
  "Твоја победа!" for the player's own win) right over the podium, not pinned to the top, popping in once; the
  podium, the board and the chips stand centred in a region that scrolls when eight players leave no room, the
  way back fixed at the foot (`RoomRevealDrawTest`). Those who stayed to the end rank ahead of those who left,
  and the plate goes only to the one of two or more who stayed standing alone at the top (`GameResults.winner`,
  as the server's `won()`): no plate on a tie, for a player left alone, or in solo (2026-10-03). A solo run shows
  its score on flaps with no podium, and a first run sets a best („Рекорд: X“) but beats none
  (`PersonalBest.beaten`), so never „Нови рекорд!“.
- **A room is seen before it is joined** (the owner, 2026-10-02): the public list's card (`RoomCard`) shows the room's
  name, or its host's when it has none, and every setting on its chips; the join screen reads the room a code names
  once its six digits are in (`GET /v1/lobbies/{code}`, `RoomPreviewViewModel`) and shows the same card over the
  keypad, a code with no room saying so at once, a read that failed saying nothing and joining going on. The host
  names the room in the settings (optional, 24 characters, `LobbySettingsDto.name`), and the lobby calls it by that
  over the seats. The server cleans a name as a player's (`DisplayNames`, its slur list too) and keeps none of what
  is left blank; the preview shares the list's rate budget and spends the code-guess guard on a miss, as a join does.
- **Snacks** (the owner, 2026-10-02): a line said over the room, never moving it (`Snack`, `RoomNote`): who the host
  is now, what the host changed on the chips (the others only), and two the server warns with, both answered by a
  button: a public host who does nothing is told their hosting passes on (`HOST_IDLE`, `afkHostWarning` 30 s before
  the 3 min), and a waiting room nobody does anything in that it closes (`ROOM_IDLE`, `idleWarning` 1 min before
  the 30). **`Stay`** ("Још чекам" / "Остани": maybe they wait for someone) starts both times over; a snack that
  asks stays for the time the server counted, and no lesser line takes it down.
- **Kick is a boot** (`KvizicIcons.Boot`: the host's remove, a vote to put out, the votes on a seat); the door
  stays for leaving. The lobby's emotes are the regular round buttons, not the small ones.
- Reactions are the server's seven: bravo, applause, fire, wow, laugh, oops on the room's bar, and the nudge,
  a bell a member sends with its own button, „Ајде, почни!“, where the host has Start.
- **Sound** (the owner, 2026-10-02): a skin part like motion, never a library. `SkinSound` names the bank a skin
  plays from (`files/sound/<bank>/<cue>.wav`, one WAV for each `Cue`), so wearing another skin is playing from
  another bank and nothing else knows: `SoundEngine` is one object that follows `KvizicTheme.skin.sound`
  (`ProvideCues`, inside the theme). A screen asks for a `Cue` (a moment, never a sound) through `LocalCues`,
  silent by default, so no draw test makes a noise; a control plays its cue on a tap that counts (`cued`), a
  button by its kind. The room is heard through `RoomCueTracker`, a pure policy over its state (its first state
  is silent: a rotation or a reconnect replays nothing), the last seconds tick under `sounding`, the reveal
  sits with the haptic (`Feedback`), the standings board sounds the player's place moving as its lines slide.
  Reactions burst over their seat every time but are heard rarely (`ReactionVoices`, the owner, 2026-10-02): a
  player once in 30 s, the room one reaction at a time (400 ms), the player's own from the server's echo, so a
  reaction the server dropped is never heard; the buttons themselves only click.
  The engine drops a cue while sound is off or the app is away, before its bank is loaded, or sooner than its
  `minGapMillis`, and moves a `varied` cue a few percent off pitch. Android plays as a game, takes no audio focus
  and is silent in silent mode; iOS is on the ambient session; a browser lets sound start after the first
  touch, so a session's first press is silent. The samples are synthesized (`python3 tools/sound/render.py`,
  repeatable byte for byte, no licence to carry; 45 cues, a bank under 1 MB); a recorded file may replace any
  one. `SoundBankTest` holds each bank whole, `TapsTest` fails a tap that makes no sound. The mix has been
  measured, never heard on a device.
- Avatars: the server's sixteen Balkan animals, each drawn by hand (`AvatarArt`); an id this build does not
  know shows a silhouette.
- **The icon** (2026-10-01): the sign's lights in a ring, sixteen lit marquee bulbs round Nunito Black's К in
  amber, on the stage; Android's adaptive layers (the themed one the ring and the К in one colour), iOS's
  1024 tile and the web's `icon.svg` drawn from one geometry. The desktop packages have none yet.

## 11. Hosting

`render.yaml`: `kvizic-server-dev` (free, H2, deploys every green `main`), `kvizic-server` (Starter, on
`kvizic-postgres`), API at `kvizic-api.ntole.com`. Secrets are Render environment variables, never committed.
**Prod deploys only on the owner's approval** (2026-10-03): `deploy-prod.yml`, after a green CI run of a push
to `main` that changes what the server is built from (or "Run workflow" with a commit, an older one being the
rollback), checks the commit is green and dev runs it or a later one, waits in the GitHub environment
`production` for the owner's tap in GitHub Mobile, calls Render's deploy hook with `ref=<sha>`, waits for
`/health` to name the commit (Render's `RENDER_GIT_COMMIT`), and runs `:e2e:smokeTest` there: two guests in a
private room, a game started, the same first question on both, both leaving before an answer, so no check
ever counts towards a question's difficulty. Manual Deploy still works.
`kvizic-site` (free, static) is the legal pages the app and Play link to (`Site`), at `kvizic.ntole.com`,
Serbian at the root and English under `en/`: its own subdomain (2026-10-02), since `ntole.com` itself is
published from WYR's repository. A page states only what the code does. `deploy-site.yml` deploys it through
its hook after a green push whose `site/` differs from the live `commit.txt`'s commit (its build writes it):
Render's build filter once left a site change behind (2026-10-02).
**Android releases** (2026-10-03): `release-android.yml` ("Run workflow", from a phone) bumps the version on
`main` (`tools/release/bump.py`, both files), tags it, and on the owner's approval builds the signed bundle,
checks it is the upload key's, and uploads it to Play's internal track; a tag `v1.2.3` pushed by hand releases
that commit. `promote-android.yml` takes it to early access (Play's `beta` track) and production, staged,
raised, halted or resumed. Both run `tools/play/play.py`, Google's own API client, never a third party with
the service account's key. Store listing, data safety, content rating and Google's review stay in the Play
Console. The secrets are the `production` environment's (`tools/release/set-github-secrets.sh` sets them from
the Mac); LAUNCH.md §10 has the owner's one-time steps.

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
makes the draw tests write PNGs to look at. The draw tests also draw at a phone's large font, 130%
(`Density(1f, 1.3f)`): Home, the lobby, the room's dialogs and the reveal; `cutTexts()` finds a text past its
box, its lines or an ellipsis, capitals included.
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
  refuse; `:e2e` plays a player over OkHttp beside one over CIO to hold that. OkHttp also fails a socket's
  `closeReason` when the network drops with no close frame (CIO ends it quietly), which killed the app on a
  tablet (2026-10-02): `closed()` answers null for it, and `DefaultLobbySession` takes any throw from a socket
  as a lost connection; a test of each holds it.

## 13. Open

- The design gate: the font, the tile scheme, the host badge, the timer, the Latin letters, the spotlight;
  the clap icon's drawing.
- Share links that open the room (a share says the code and Play's page, `StoreLink`, for now). Sound's mix on a
  phone's speaker (the levels, the cues' character), a skin picker in Settings, and a way for a skin to be
  chosen at all.
- The moderation app's web page loses the first key after unlocking until the page is clicked.
- **Phones on their side**: phones are portrait-locked (§9) until the game has a landscape layout: the
  question screen in two panes, the bar and the question beside the answers' grid (a phone on its side is
  wide, so its answers already stand two by two), the reveal's board beside them; the lobby's seats, the join
  keypad, the settings and the results checked at a phone's height of ~360 dp. Then the lock comes off.
- **The reveal's board at 130% font on 360×640**: it does not fit there with a usual question in Buzzers
  (about 26 px short) and is left out whole; it shows on 375×667 and 360×760. To show it: the tiles' letter
  mark and least answer size stop growing with the font, or the recap gives some back, or the explanation
  drops to 3 lines at a large font. Answers at the wire's 60 characters are not whole at 130% either.
- `design-tests` hung on CI with four test JVMs (2026-10-02: no output for 29 min, cancelled at the job's
  limit; it passes locally in 42 s), and passed in one. Four again, with `DesignShotsTest` split: if it
  hangs again, the job's thread dumps say where.
