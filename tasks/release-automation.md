# Task: automate Kvizić's deploys and releases, drivable from a phone

A brief for a fresh session. Read `CLAUDE.md` and `LAUNCH.md` first; this file is the job, those are the
rules and the state of the world. Written 2026-10-03, when 1.0.0 (build 10000) was in Play's early-access
review and prod was live.

**Status (2026-10-03):** phases 1 and 2 built (`deploy-prod.yml`, `deploy-site.yml`, `release-android.yml`,
`promote-android.yml`, `tools/play/play.py`, `tools/release/`); the owner's one-time steps are LAUNCH.md §10.
Decided: the prod check is a two-guest game started and left before an answer; question publishing stays
local; notifications are GitHub Mobile's alone; Phase 3 signs dev builds with the owner's own debug keystore
as a secret (its SHA-1 is already on `io.ntole.kvizic.dev`'s credential). Phase 3 is next.

## Goal

The owner wants as little manual work as possible, and most of what remains doable **from a phone**. Today
these are manual: promoting a commit to prod (Render's Manual Deploy), building and uploading the Android
bundle, promoting it through Play's tracks, getting a test build onto a phone away from home, and a few
emergency levers. Automate them with GitHub Actions, with the owner approving the risky steps from GitHub
Mobile.

## Ground rules (from CLAUDE.md and the owner)

- Propose, then wait for a go-ahead before implementing (the owner's global rule). The plan below is agreed in
  outline; confirm each phase's details before building it, and **never put a secret in chat, a file or git**.
- `~/Projects/WYR` is never modified. Personal identity only (git `Nikola`, `nikola.tokicg6@gmail.com`,
  GitHub `niktok1`, SSH host `github-kvizic`). Commits: Conventional Commits, small, ending with the
  `Co-Authored-By` line the session gives.
- The repo is **public**. `main` must always be green: `./gradlew ktlintCheck`, check its exit status.
  No questions, secrets, keystores or `local.properties` in git.
- Change CLAUDE.md in the same commit as a decision (§11 hosting, §12 verifying); keep LAUNCH.md true.
- The owner wants **detailed, click-by-click steps** for anything they must do themselves, and every input
  you need asked for in a prompt with given answers (AskUserQuestion). Say what only they can do.

## What exists (verified 2026-10-03)

- **CI** (`.github/workflows/ci.yml`, on push to `main` and pull requests): `verify`, `design-tests`,
  `screen-tests`, `clients`, `android` (builds `assembleDevDebug` and `assembleProdRelease`, and checks that
  `bundleProdRelease` refuses to sign without the upload key), `server-postgres`, `docker-smoke`, `ios`.
- **Render** (`render.yaml`): `kvizic-server-dev` deploys every green `main` (`autoDeployTrigger:
  checksPass`); `kvizic-server` is prod, `autoDeployTrigger: "off"`, deployed by hand, API
  `https://kvizic-api.ntole.com` (`/health`); `kvizic-site` is static, from `site/`, at
  `https://kvizic.ntole.com`, and once failed to deploy a `site/` change pushed under later commits that left
  `site/` alone (check the live page after a push). Prod drains for 280 s on deploy.
  Render's [deploy hook](https://render.com/docs/deploy-hooks) takes `?ref=<sha>` to deploy a given commit.
- **Android** (`app/androidApp/build.gradle.kts`): flavors `local`, `dev`, `prod` (ids `io.ntole.kvizic.local`,
  `.dev`, `io.ntole.kvizic`). Only prod's release bundle takes the upload key, from `local.properties` or the
  environment: `KVIZIC_UPLOAD_STORE_FILE`, `KVIZIC_UPLOAD_STORE_PASSWORD`, `KVIZIC_UPLOAD_KEY_ALIAS`,
  `KVIZIC_UPLOAD_KEY_PASSWORD`. PostHog and Play Games ids are Gradle properties (`-Pkvizic.posthog.key`,
  `-Pkvizic.playgames.appId`, `-Pkvizic.playgames.serverClientId`) or `local.properties`. Bundle:
  `./gradlew :app:androidApp:bundleProdRelease` → `app/androidApp/build/outputs/bundle/prodRelease/`.
- **Version**: `kvizic.app.version` in `gradle.properties` (MAJOR.MINOR.PATCH, MINOR and PATCH < 100); the
  build number is `MAJOR*10000 + MINOR*100 + PATCH`; `app/iosApp/Configuration/Config.xcconfig` must say the
  same (`MARKETING_VERSION`, `CURRENT_PROJECT_VERSION`) or every build fails.
- **Keys** (owner's machine, never in the repo): the upload keystore `~/keys/kvizic-upload.jks`, alias
  `kvizic-upload`, SHA-1 `7E:7E:42:7E:05:74:0F:B9:13:46:5A:B5:72:E7:4E:00:42:DE:94:76`. Play App Signing's key,
  which signs what players install, has SHA-1 `0B:6C:BE:53:6B:F9:48:A1:54:6C:21:62:09:B3:3B:8B:75:9E:D7:7A`
  and is on the Play Games Android credential for `io.ntole.kvizic`.
- **Play**: the app exists in Play Console; 1.0.0 is on internal testing and sent to review for open testing
  (early access) in RS, BA, ME, MK, AT, DE, CH, SE, NO, DK. Play Games is published.
- **`:e2e`** runs the real server in-process with real client sessions. It has no mode against a remote server
  yet (check `E2eHarness.kt`; `KvizicEnvironment` names prod's base URL).
- The question bank is published with the private, local `~/Projects/kvizic-content/publish.py`; the owner
  types the admin token. It stays local unless the owner says otherwise (open question below).

## The work, in phases (do them in order; each is its own commit set and green on `main`)

### Phase 1: prod deploy with phone approval, site deploy, post-deploy check

1. `.github/workflows/deploy-prod.yml`, `workflow_dispatch` with an input `sha` (default: `main`'s head).
   Jobs: **gate** (that commit's CI is green, via the GitHub API; dev's `/health` is up and runs it, if the
   server reports its commit, otherwise say what is checked) → **deploy** in a GitHub **environment**
   `production` whose required reviewer is the owner (GitHub Mobile notifies them and they tap Approve) →
   call Render's deploy hook with `ref=<sha>` → poll `https://kvizic-api.ntole.com/health` until it answers
   and the new instance is up (allow for the 280 s drain) → the post-deploy check.
   The same workflow with an older `sha` is the **rollback**.
2. **Site deploy**: in the same push-to-`main` flow, when `site/` changed, call `kvizic-site`'s deploy hook
   after CI, so a site change is never left behind (CLAUDE.md §11 notes the gap).
3. **Post-deploy check**: a small job against prod that plays a two-client game. Prefer extending `:e2e`
   with a mode that targets a base URL (guests only; they are cleaned after 90 days) over a new script.
   If that is large, propose a smaller check first (health, `/v1/topics` counts, a guest and a room created
   and left) and say what it does not cover.
4. Update CLAUDE.md §11: prod still deploys only by a person's approval, now through the workflow.

**The owner's one-time steps** (give click-by-click): Render → `kvizic-server` → Settings → **Deploy Hook**
(copy it), and the same for `kvizic-site`; GitHub → repo Settings → **Environments → New environment**
`production`, **Required reviewers** = themselves, and the hook URLs as environment secrets
(`RENDER_DEPLOY_HOOK_PROD`) and repository secret (`RENDER_DEPLOY_HOOK_SITE`); install **GitHub Mobile** and
allow its notifications. Never ask for the hook URLs in chat.

### Phase 2: Android release to Play

1. `.github/workflows/release-android.yml`, on a tag `v*` and `workflow_dispatch`. It checks that
   `kvizic.app.version` and `Config.xcconfig` match the tag, decodes the upload keystore from a secret, runs
   `bundleProdRelease` with the `KVIZIC_UPLOAD_*` variables and the `-P` ids, verifies the signature
   (`jarsigner -verify`), uploads the bundle as a run artifact, and uploads it to Play's **internal** track
   through the Play Developer API (e.g. [r0adkll/upload-google-play](https://github.com/r0adkll/upload-google-play)
   or Gradle Play Publisher; justify the pick). Run it in the `production` environment so the owner approves.
2. A **bump** helper (workflow or script) that changes `gradle.properties` and `Config.xcconfig` together
   and opens a commit, so a release is "bump, tag, approve".
3. `.github/workflows/promote-android.yml`, `workflow_dispatch` with inputs: from track, to track
   (internal → open/early access → production), and the **rollout fraction** (0.2, 0.5, 1.0); a **halt**
   action. Approved in the `production` environment.
4. Know the limits and say so: Google may restrict API releases while an app is a draft (the first release
   was made in the console); store listing, data safety, content rating and Google's review stay manual.
5. Record the flow in LAUNCH.md and CLAUDE.md §12; keep release notes in Serbian (`<sr>…</sr>`).

**The owner's one-time steps**: create a Play **service account** (Google Cloud → IAM → Service accounts →
create → a JSON key), then Play Console → **Users and permissions → Invite new users** with that account's
email and the release permissions for this app only; GitHub secrets `PLAY_SERVICE_ACCOUNT_JSON`,
`UPLOAD_KEYSTORE_BASE64` (`base64 -i ~/keys/kvizic-upload.jks | pbcopy`), `UPLOAD_STORE_PASSWORD`,
`UPLOAD_KEY_PASSWORD`, and the variables for PostHog and Play Games ids. Remind them: the repo is public,
secrets never reach pull requests from forks, and **no workflow may use `pull_request_target` or run
untrusted code with secrets**. A leaked upload key can be reset by Google, but only after days.

### Phase 3: a dev build on the owner's phone, whenever they are

On every green `main` commit (or by `workflow_dispatch`), build the **dev** flavor
(`io.ntole.kvizic.dev`, against the dev server) and send it to the owner's phone with **Firebase App Distribution**
(free; a push notification with an install button; installs beside the Play version). Caveats to handle and
tell the owner: a dev build signed in CI has another SHA-1 than their local debug key, so Play Games sign-in
on it needs that SHA-1 added to the Play Games credential for `io.ntole.kvizic.dev` (or CI signs with a fixed
debug keystore kept as a secret: propose one); dev's data resets on every deploy.

**The owner's one-time steps**: a Firebase project (console.firebase.google.com), the Android app
`io.ntole.kvizic.dev`, App Distribution on, a tester group with their address, a service account for CI, and
the app id and credentials as GitHub secrets.

### Phase 4: optional levers (propose, don't assume)

- **Force an update** from the phone: a workflow that sets `MIN_CLIENT_VERSION_ANDROID` on `kvizic-server`
  through Render's API and restarts it (needs a Render API key secret), and one that clears it.
- A **daily notification** of how many reports wait and how many questions are live (the admin API with the
  admin token as a secret; a push through GitHub Mobile or ntfy). Moderation is checked daily in launch week.
- **Question publishing** from CI, only if the owner agrees to a private GitHub repo for the content.

## Open questions to ask the owner first (AskUserQuestion)

1. Question publishing: stay local (recommended: the content repo is deliberately never pushed), or a private
   repo so a workflow can publish?
2. Phase 3's Play Games sign-in on dev builds: a fixed debug keystore as a secret, or add the CI key's SHA-1?
3. Which notifications: only GitHub Mobile's, or also ntfy/Pushover for outages and failed deploys?

## Done when

- A push to `main` that changes the server ends, after one tap in GitHub Mobile, with prod on that commit and
  the post-deploy check green; the same with an older commit rolls back.
- A tag and one tap uploads a signed 1.x bundle to Play's internal track; another workflow promotes it with a
  chosen rollout fraction, or halts it.
- A green `main` commit reaches the owner's phone as a dev build with a notification.
- Every workflow is listed in CLAUDE.md §11–§12 and LAUNCH.md; `main` is green; no secret is in the repo, the
  logs (mask them) or chat.
