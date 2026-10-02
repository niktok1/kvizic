# Launch checklist — Квизић on Google Play (Android first)

Updated 2026-10-02. ✅ done, ⚠️ found broken or missing, ☐ to do. **You** marks a step only the owner can
take (a console, a secret, a device); the rest is done in the repository.

Decided 2026-10-02: launch in **RS, BA, ME, MK, AT, DE, CH, SE, NO, DK**, more countries later, so the
legal pages name no country; the contact block stays WYR's (toleapps, application.eili@gmail.com); a guest
signs in with Play Games from Home, under their name; Quick play with nobody online stays as it is (alone in
a room is as good as solo).

## Done in the repository

- ✅ Legal pages in `site/` (sr + en): privacy, terms, account deletion, contact, home. They name no country
  now: „available on Android through Google Play“, a player outside Serbia pointed to their own authority.
  **Live at https://kvizic.ntole.com** (2026-10-02): all 10 pages 200 over HTTPS, the app's links match.
- ✅ Production bank checked: all 1,536 questions are in `kvizic-content/drafts/`, every topic has at least 52
  (Митологија 52: 18 easy, 21 medium, 13 hard), so a 20-question game fills at every level.
- ✅ Play Games sign-in for a guest on Home (`LinkPlayGames.manually` had no caller).
- ✅ Home at 360 dp: „Направи собу“ showed as „Направи“ since Nunito (wider than the old face); tiles with
  the icon above their words have less side padding now, and English says „Join by code“.
- ✅ Version **1.0.0** (build 10000), Android and iOS.
- ✅ Release signing reads `kvizic.upload.*` from `local.properties`; `bundleProdRelease` refuses the debug key.
- ✅ Store listing drafted in sr + en: [store-listing.md](store-listing.md); the 512² icon and the 1024×500
  feature graphic in `store/`.
- ✅ The sign's name stood past its panel since Nunito; it shrinks to fit now (~53 sp on a 360 dp phone).
- ✅ targetSdk 36, `/health` 200 on Starter in Frankfurt, deploys drain.

## Your steps, in order

### 1. The legal pages ✅ live

The privacy page's retention claims (§ „Колико чувамо“): PostHog keeps events **2 years** ✅ (the owner,
2026-10-03); ☐ Render's log retention on the workspace's plan must be **30 days or less**.

> Weighed (the owner, 2026-10-02): an EU (GDPR Art. 27) or Swiss (FADP Art. 14) representative may be owed for
> the diaspora countries; launch as planned on the occasional, low-risk exemption, as WYR does, and revisit as
> the game grows.

### 2. The upload key ✅ (2026-10-02)

`~/keys/kvizic-upload.jks`, alias `kvizic-upload`, its settings in `local.properties`; `bundleProdRelease`
signs with it. Its certificate's SHA-1 is `7E:7E:42:7E:05:74:0F:B9:13:46:5A:B5:72:E7:4E:00:42:DE:94:76`
(public, for Play Games' Android credentials if a build signed with it, not by Play, should sign in). Keep
the file and its password backed up together. Play App Signing is offered at the first upload: accept it.

### 3. Analytics key ✅ (2026-10-02, the Kvizić project on PostHog EU; the prod bundle carries it)

PostHog EU → the Kvizić project → **Project settings → Project API key** (`phc_…`). Add to `local.properties`:

```
kvizic.posthog.key=phc_…
```

The host defaults to PostHog's EU cloud. After the first prod build is on a phone, open PostHog → **Activity**
and check `app_opened` and `room_entered` arrive, named as prod's.

### 4. Production server (15 min)

Render → `kvizic-server` → **Environment**:

- `PLAY_GAMES_CLIENT_ID`, `PLAY_GAMES_CLIENT_SECRET`: the same two values as `kvizic-server-dev`.
- `ALLOWED_WEB_ORIGINS`: leave empty (no web client at launch).
- `JWT_SECRET`, `ADMIN_TOKEN`: both should have values (generated). Copy `ADMIN_TOKEN` to your password manager.
- `MIN_CLIENT_VERSION_ANDROID`: leave unset. For a bad build later, set it to the first good build number
  (1.0.1 is `10001`) and restart: older apps are asked to update (4426).

Then, once this commit's CI is green and dev works: **Manual Deploy → Deploy latest commit**.

### 5. The production bank (5 min)

```bash
cd ~/Projects/kvizic-content && KVIZIC_ADMIN_TOKEN=… ./publish.py prod drafts/*.json
```

It imports 25 a request and approves what it brought. Then the moderation app against prod
(`KVIZIC_ENV=prod ./gradlew :app:adminApp:run`) → **Overview** should count 1,536 approved.

### 6. Operations (20 min)

- **Uptime**: UptimeRobot (free) → HTTP monitor `https://kvizic-api.ntole.com/health`, every 5 min, email alert.
- **Backups**: Render → `kvizic-postgres` → **Recovery**: note the point-in-time window; do one restore to a
  new database, check it has the questions, delete it.
- **Origin** (the owner, 2026-10-02: left for now): Render serves `kvizic-server.onrender.com` too, past
  Cloudflare, where a caller could forge `CF-Connecting-IP` and slip the per-address limits. Revisit if abuse
  shows in the log.

### 7. The build and a real phone (30 min)

```bash
./gradlew :app:androidApp:bundleProdRelease
```

The bundle is `app/androidApp/build/outputs/bundle/prodRelease/androidApp-prod-release.aab`. Upload it to
**Testing → Internal testing**, add yourself and the Play Games testers, install from Play, and check: a full
game on two phones against prod, Play Games sign-in (the Home button too), sound on the speaker, a small
phone (360×640) and Android 7 (API 24) if you have one.

### 8. Play Console (1–2 h)

- **Create app**: name „Квизић“, default language Serbian (`sr`), Game, Free.
- **Store listing**: paste from [store-listing.md](store-listing.md), the icon and the feature graphic
  from `store/`; screenshots from step 7's phones.
- **App content**:
  - Privacy policy: `https://kvizic.ntole.com/privacy.html`.
  - Ads: **No**. App access: **All functionality is available without special access**.
  - Target audience: **13–15, 16–17, 18+** (not under 13: keeps it out of the Families policy).
  - Content rating (IARC): a trivia game; no violence, sex, drugs, gambling or swearing; **users interact**
    (multiplayer rooms, names shown, preset reactions only, no chat); no location shared; no purchases.
  - Data safety: see the table below.
  - Account deletion: in the app (About → Подаци) and `https://kvizic.ntole.com/delete.html`.
- **Countries**: Serbia, Bosnia and Herzegovina, Montenegro, North Macedonia, Austria, Germany, Switzerland,
  Sweden, Norway, Denmark.
- **Play Games Services**: Google Cloud → the OAuth consent screen → **Publish app** (the privacy URL from
  step 1 is required); Play Console → Play Games Services → **Review and publish**. After the first upload,
  check that **Setup → App signing**'s SHA-1 is among the Android credentials for `io.ntole.kvizic`.

**Data safety**: encrypted in transit: yes. Deletion: yes, users can ask. Shared with third parties: none
(PostHog and Render process data for us, as service providers).

| Data type | Collected | Why | Optional |
|---|---|---|---|
| Personal info → Name (Play Games name) | yes | App functionality | yes (sign-in) |
| Personal info → User IDs (player id, Play Games id) | yes | App functionality, Analytics | no |
| Location → Approximate (PostHog, from the IP) | yes | Analytics | yes (Статистика off) |
| App activity → App interactions | yes | Analytics, App functionality | yes (analytics part) |
| App info and performance → Diagnostics (errors shown) | yes | Analytics | yes |
| Device or other IDs (PostHog's random device id) | yes | Analytics | yes |

### 9. Launch

- Production release from the internal build, **staged rollout 20%**, then 50% and 100% over a few days,
  watching Android vitals (crashes, ANRs) and the server's log.
- Pick a launch hour and bring friends and groups online at once: Quick play with nobody else is the first
  impression.
- Moderation: check the report queue daily for the first week.

## Open, decide later

- The design gate: font, tile scheme, host badge, timer, Latin letters, spotlight, clap icon.
- Share links (invite to a room): growth, after launch.
- Language and skin picker in Settings (the language follows the system now).
- Crash reporting: Android vitals is enough for 1.0.
- Capacity: the server's share of 0.5 CPU is unmeasured; watch Render's CPU in launch week.
- The `design-tests` CI hang with 4 JVMs: if it hangs again, `main` stops deploying.
- After launch: send other clients a per-room seat id instead of the account id (the owner, 2026-10-02),
  the phone landscape layout, iOS, web, desktop packaging and icons, the admin web page's first-key bug.
