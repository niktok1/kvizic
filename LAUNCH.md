# Launch checklist — Квизић on Google Play (Android first)

Checked 2026-10-02. ✅ done, ⚠️ found broken or missing, ☐ not yet known/done.

## 1. Blockers (Play won't accept the app, or the app breaks for real players)

- ◐ **Legal pages** written (`site/`, Serbian + English: privacy, terms, account deletion, contact, home),
  to be served at `https://kvizic.ntole.com` by `kvizic-site` (render.yaml). Still to do:
  - ☐ review the pages: contact details and countries are WYR's; PostHog's 2-year and Render's 30-day
    retention are WYR's claims, so check them in PostHog's project settings and on the Render plan;
  - ☐ commit and push; then in Render, sync the blueprint so `kvizic-site` is created;
  - ☐ Cloudflare DNS: a `kvizic` CNAME to the site's `onrender.com` name, proxy off (as the API's), then
    verify the domain in Render;
  - ☐ check all 10 URLs return 200, and that the About screen's links open them.
- ⚠️ **No upload key configured.** `local.properties` has no `kvizic.upload.*`, so `bundleProdRelease`
  refuses to sign. Create the upload keystore, store it and its passwords outside the repo (and back them
  up), enrol in Play App Signing.
- ✅ No forced closed test: the Play account predates the rule (12 testers for 14 days applies to personal
  accounts made after 2023-11-13). A short internal or closed test is still worth it to get the Play Games
  testers onto the store build.
- ☐ **Production question bank.** Prod runs on Postgres, which has no seed. Publish and approve the bank with
  `./publish.py prod …` and check that every topic and difficulty has enough questions for 20-question games
  (thin topics are greyed out). Dev has 1,536 questions.
- ☐ **Prod env vars on Render**: `ALLOWED_WEB_ORIGINS`, `PLAY_GAMES_CLIENT_ID`, `PLAY_GAMES_CLIENT_SECRET`
  (or leave both unset to launch without Play Games). Confirm `JWT_SECRET` and `ADMIN_TOKEN` were generated,
  and keep the admin token somewhere safe.
- ☐ **Analytics at launch, as in WYR**: set the PostHog key (`kvizic.posthog.key`) for prod builds and check
  the events reach PostHog EU from a prod build. Then the **Статистика** switch (About → Подаци) turns them
  off, and the privacy policy describes them.

## 2. Play Console

- ☐ Store listing (sr + en): name, short and full description, icon 512², feature graphic 1024×500, phone
  screenshots (the `design-review/` stills are a starting point, but use real-device captures).
- ☐ Data safety form. Collected: guest id, display name, game results, PostHog events, Play Games id.
  Shared: none. Deletion: in the app (About → Подаци) and via the web URL.
- ☐ Content rating (IARC questionnaire). Target audience: pick 13+ to stay out of the Families policy.
- ☐ Ads: none. App access: no login needed (guest).
- ☐ Countries: Serbia, plus the region (BA, ME, HR, MK?) and the diaspora (AT, DE, CH?).
- ☐ Play Games Services: consent screen published (it needs the privacy policy page first), configuration
  published, achievements/leaderboards (if any) and the final art. Otherwise only testers can sign in (§13).

## 3. Build and release

- ☐ Bump `kvizic.app.version` (now `0.1.0`), e.g. `1.0.0`, which makes versionCode 10000.
- ☐ `bundleProdRelease` signed with the upload key; R8 build smoke-tested on a real phone (prod flavor
  against prod).
- ☐ targetSdk 36 ✅. Test on min API 24 (Nunito regular-only before Android 8) and on a small 360×640 phone.
- ☐ Decide on `MIN_CLIENT_VERSION_ANDROID`: leave it unset at launch, and know how to raise it (4426 update)
  for a bad build.

## 4. Server and operations

- ✅ `kvizic-api.ntole.com/health` is 200, Starter plan, Frankfurt, drains on deploy.
- ☐ Promote a green, dev-verified commit with Manual Deploy, and smoke-test a full game on prod with 2+ phones.
- ☐ Postgres backups: check what `basic-256mb` keeps and how long, and do one test restore.
- ☐ Uptime alert on `/health` (UptimeRobot or Render notifications), so you hear about an outage before players do.
- ☐ **Crash reporting**: there is none in the app. Rely on Play Console's Android vitals, or add a
  multiplatform crash reporter before launch. Recommendation: vitals is enough for v1.
- ☐ Capacity: the server's share of 0.5 CPU is unmeasured (§12). Run `:e2e:loadTest` against a separate
  server process once, or watch Render's CPU closely during launch week.
- ☐ Cloudflare in front of the API: `CF-Connecting-IP` is trusted; confirm the origin can't be reached any
  other way.
- ☐ Moderation: the report queue is checked daily during launch week; the admin app works against prod.

## 5. Product gaps listed as open (CLAUDE.md §13). Decide: before launch or after

- ☐ **A Play Games button for a guest** (`LinkPlayGames.manually` has no caller). Without it, a player
  who backs out of the first prompt stays a guest for good. **Recommend before launch.**
- ☐ The sound mix heard on a real phone speaker (never listened to on a device yet). **Recommend before launch.**
- ☐ The design gate: font, tile scheme, host badge, timer, Latin letters, spotlight, clap icon.
- ☐ Share links (invite to a room). They help growth, but can come after launch.
- ☐ Language and skin picker in Settings (language follows the system for now?).
- ☐ The `design-tests` CI hang with 4 JVMs. Watch for it; if it hangs again, `main` stops deploying.
- After launch: **the player id other clients see** is the account ID used for deletion by email, so a
  room member could ask to delete someone else's account; send a per-room seat id instead (the owner,
  2026-10-02: after release). Also the landscape layout for phones, iOS, web, desktop packaging and icons, and the admin web
  page's first-key bug.

## 6. Launch day

- ☐ Staged rollout (20% → 50% → 100%), watching vitals and the server log.
- ☐ Enough players online at the same time: Quick play with an empty server is the first impression. Plan
  a launch hour, invite friends and groups, or make solo the path when no room is open.
- ☐ A contact address on `contact.html` that someone actually reads.
