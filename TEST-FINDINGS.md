# Test findings — 1.0.0 (10000) against prod, 2026-10-03

Found only, nothing fixed. Tick an item off (or delete it) as it is handled.

**Setup**: the prod bundle `androidApp-prod-release.aab`, as a universal APK signed with the debug key
(bundletool), so the same R8 build as on Play. Three devices:
- Poco X3 Pro (Android 12, 393 dp wide, Spanish system language).
- Galaxy Tab S6 Lite (Android 13, 800 dp wide).
- An emulator (Android 17, 16 KB pages), set to 360×640 dp and then to 130% font.

**Worked**:
- Room flow: create a private room (named, topics searched in Latin), join by code with the preview, and
  the host's changes reaching the others.
- Play: reactions and the nudge, a 5-question and a 10-question game, the reveal, the results, back to the
  room, and leaving.
- Host and room: host handover, kick plus the ban (Quick play then skips that room), and vote-kick with 3
  players („1/2“, then out).
- Rooms and modes: the public list, Quick play matching two devices, and solo.
- Connection: rejoining by code after the app was killed, and reconnecting after a network drop
  („Повезивање…“, then back at the right question).
- Other: reports, About and its legal links, and the analytics switch.
- Health: the 16 KB alignment is fine, and the logs had no crash, ANR or app error.

## Bugs, most important first

1. **The app killed mid-game loses the game.** A player whose app the system killed in the background
   (common on Xiaomi and Samsung) relaunches onto Home, with no notice and no way back. The seat is still held
   (§6: the rest of the game), and joining by code worked, but only if they remember the code. Home could
   offer „Врати се у игру“ for a room the session still holds.
    — ✅ fixed (8f15835): the launch asks `POST /v1/lobby-rejoins` and the room opens.
2. **A voted-out player is told the host kicked them.** The host voted out by the two others saw „Водитељ те
   је избацио из собе.“, not `exitVotedOut` („Играчи су гласали…“). So the client never got
   `CloseReason.VOTED_OUT`: either the 4403 close came before the `closing` frame, or the server sent KICKED
   (`LobbyMapper.kt` ~265, `Lobby.kt` ~1094).
    — ✅ fixed (af18ed2): voted out with no live socket, the rejoin's 403 was read as a kick; `ErrorDto.reason` now tells them apart. E2E test.
3. **A player who left mid-game can win.** In a room where one of two players left at question 4, the one
   who stayed saw the departed player on the winner's plate („ПОБЕДНИК Паметни Медвед“), ahead of themselves
   on a 0–0 tie. A tie should not favour someone who left, and someone who left perhaps should not be
   crowned at all.
    — ✅ fixed (c0eaabd server; b78a649 results): those who stayed rank first; the plate only for a lone winner of two or more who stayed.
4. **Lobby chips are crushed to „..“.** On the 393 dp phone, with „Географија, Историја +1“ as topics, the
   4th chip („Минус“), and with difficulty set the 3rd and 4th („Тешко“, „Минус“), are drawn as „..“, 13 px
   wide. Nobody can see the difficulty or the minus. The row should wrap or scroll.
    — ✅ fixed (UI commits e7eda07…c633ec4): the chips wrap.
5. **The host's seat dialog squeezes Kick.** On the tablet: „Откажи · Нека води · 👢 И“. The „Избаци“ label
   is 13 px wide, since three buttons don't fit the dialog's row. The host's main action can't be read.
    — ✅ fixed (UI commits e7eda07…c633ec4): a dialog's buttons stack when the row can't hold them.
6. **130% font (a common phone setting) on 360 dp:**
   - Home: „Направи собу“ shows as „Направи“; „Пријави се преко Плеј игара“ loses „игара“.
   - The lobby's 4th chip is „..“.
   - The reveal cuts the recalled question to one line („…прве мо…“) and the standings board falls off the
     bottom.
   - New room's settings are fine (they scroll).
    — ✅ mostly fixed (UI commits e7eda07…c633ec4): buttons wrap, the reveal's board is whole or left out. Still open: the board doesn't fit at 130% on 360×640 (CLAUDE.md §13).
7. **Vote to remove is cut.** On 360 dp, the button reads „Гласај за“ (of „Гласај за избацивање“).
    — ✅ fixed (UI commits e7eda07…c633ec4).
8. **Report reasons are cut.** On the phone: „Означени одговор није…“, „Нејасно је, или је више…“. The
   whole reason should wrap.
    — ✅ fixed (UI commits e7eda07…c633ec4): reasons wrap to 3 lines.
9. **Play Games' failure is silent.** A sign-in that failed with DEVELOPER_ERROR returns to Home with no
   notice, and the button stays. A player taps it again and nothing happens.
    — ✅ fixed by the sign-in session (`PLAY_GAMES_NOT_SIGNED_IN`).
10. **Topic picker header.** Opening topics from New room showed two headers, with two back arrows, stacked.
    — ✅ fixed (UI commits e7eda07…c633ec4): one bar; Room settings had it too.

## Questions and polish

11. **Solo results** say „ТВОЈА ПОБЕДА!“ over an empty podium (the bare 2 and 3 plates), and „Нови
    рекорд!“ for −130 points and 1 of 10 right (the first medium run). Maybe solo needs its own results:
    the score, the record, and no podium.
    — ✅ fixed (b78a649): the score on flaps, no podium or plate; a first run is „Рекорд: X“.
12. **The online count** (`LobbyRegistry.presence`) counts only players seated in a room, so a player on
    Home is not counted. With two people in the app, Home said „1“. If it should mean "people in the app", it
    needs the session count.
    — ✅ fixed (e708e5b): a list read in the last 30 s counts too.
13. **No language picker.** The app is always Cyrillic, whatever the device; Latinica can't be reached. That
    matters for BA, ME and the diaspora (already in §13 Open).
14. **The fonts' OFL licences aren't shown.** `FontLicences` (designsystem) says it is "for the About screen
    to show", but nothing calls it, and About → Licences lists libraries only.
    — ✅ fixed (UI commits e7eda07…c633ec4): under About → Licences.
15. **Share text** is only „Играј Квизић са мном! Уђи у собу кодом N.“, with no Play link, so someone without
    the app can't act on it (share links are in §13).

## Unconfirmed: watch

16. **Two correct answers scored 0.** In one 3-player game (after a vote-out, and with one player's network
    dropped at question 2), two players answered question 1 right (Бразилија), yet both ended with 0 points
    („Тачно 0 од 10“). A fresh room right after scored normally (−17 each for a wrong answer).
    — ⚠️ one cause fixed (c0eaabd): a player who left mid-game and sat down again had every answer refused `NOT_PLAYING`. Unconfirmed it was this; check that game's `match_players` (`answered`, `finished`) on prod.
17. **Join keypad digits.** Typing a code quickly with key events dropped or misplaced digits twice. Not seen
    with the on-screen keypad.

## Environment notes (not app bugs)

- **The debug key no longer signs in to Play Games for `io.ntole.kvizic`.** Play Games logged
  DEVELOPER_ERROR for SHA-1 `88:5F:40:75:72:5E:1B:59:40:EE:5C:0E:09:48:CC:3E:34:B2:A3:4A`, while §9 of
  CLAUDE.md says the debug key is on that credential. Either it was replaced by Play's key (then fix §9), or
  re-add it for local prod testing. The Play-signed sign-in works: 1.0.1 (10001), installed from the internal
  track on the phone and the tablet, signed in with Play Games (2026-10-03).

## Not tested

- Sound on the speaker.
- PostHog events arriving.
- Account deletion (it is irreversible on prod).
- Android 7, TalkBack, the tablet turned on its side, Latin and English.
